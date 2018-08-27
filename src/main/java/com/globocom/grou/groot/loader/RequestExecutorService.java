package com.globocom.grou.groot.loader;

import com.globocom.grou.groot.SystemEnv;
import com.globocom.grou.groot.channel.BootstrapFactory;
import com.globocom.grou.groot.channel.RequestUtils;
import com.globocom.grou.groot.channel.handler.Http1ClientInitializer;
import com.globocom.grou.groot.channel.handler.Http2ClientInitializer;
import com.globocom.grou.groot.channel.handler.SslService;
import com.globocom.grou.groot.monit.MonitorService;
import com.globocom.grou.groot.test.properties.BaseProperty;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RequestExecutorService {

    private static final Log LOGGER = LogFactory.getLog(RequestExecutorService.class);

    private final SslService sslService;
    private final MonitorService monitorService;
    private final CookieService cookieService;

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final AtomicLong now = new AtomicLong(System.currentTimeMillis());

    private long schedPeriod = 50L;

    @Autowired
    public RequestExecutorService(
        final SslService sslService,
        final MonitorService monitorService,
        final CookieService cookieService) {

        this.sslService = sslService;
        this.monitorService = monitorService;
        this.cookieService = cookieService;
    }

    private ChannelInitializer initializer(Proto proto) {
        if (proto == Proto.H2 || proto == Proto.H2C) {
            return new Http2ClientInitializer(sslService.sslContext(proto.isSsl()), Integer.MAX_VALUE, monitorService, cookieService);
        }
        return new Http1ClientInitializer(sslService.sslContext(proto.isSsl()), monitorService, cookieService);
    }

    private Channel newChannel(final Bootstrap bootstrap, Proto proto, final FullHttpRequest[] requests, long schedPeriod, AtomicLong now) {
        try {
            if (!bootstrap.config().group().isShuttingDown() && !bootstrap.config().group().isShutdown()) {
                URI uri = URI.create(proto.name().toLowerCase() + "://" + requests[0].headers().get(HttpHeaderNames.HOST) + requests[0].uri());
                final Channel channel = bootstrap
                    .clone()
                    .handler(initializer(proto))
                    .connect(uri.getHost(), uri.getPort())
                    .sync()
                    .channel();
                channel.eventLoop().scheduleAtFixedRate(() -> {
                    if (channel.isActive()) {
                        for (FullHttpRequest request : requests) {
                            monitorService.writeCounterIncr();
                            cookieService.applyCookies(request.headers());
                            channel.writeAndFlush(request.copy());
                        }
                    }
                }, schedPeriod, schedPeriod, TimeUnit.MICROSECONDS);
                return channel;
            }
        } catch (Exception e) {
            monitorService.failedIncr(e);
            monitorService.fail(e, now.get());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
        return null;
    }

    private synchronized void activeChannels(int numConn, final Proto proto, final Bootstrap bootstrap, final Channel[] channels, final FullHttpRequest[] requests, long schedPeriod, AtomicLong now) {
        for (int chanId = 0; chanId < numConn; chanId++) {
            if (channels[chanId] == null || !channels[chanId].isActive()) {

                Channel channel = newChannel(bootstrap, proto, requests, schedPeriod, now);
                if (channel != null) {
                    channels[chanId] = channel;
                }
            }
        }
    }

    private void reconnectIfNecessary(boolean reconnect, int numConn, final Proto proto, final EventLoopGroup group, Bootstrap bootstrap, Channel[] channels, final FullHttpRequest[] requests, long schedPeriod, AtomicLong now) {
        while (reconnect && !group.isShutdown() && !group.isShuttingDown()) {
            activeChannels(numConn, proto, bootstrap, channels, requests, schedPeriod, now);
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                // ignored
            }
        }
    }

    private void closeChannels(Channel[] channels, int timeout, TimeUnit unit) {
        CountDownLatch latch = new CountDownLatch(channels.length - 1);
        for (Channel channel : channels) {
            try {
                if (channel != null && channel.isActive()) {
                    channel.close();
                }
            } finally {
                latch.countDown();
            }
        }
        try {
            latch.await(timeout, unit);
        } catch (InterruptedException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
    }

    public void submit(BaseProperty property) throws RuntimeException {
        int numConn = property.getNumConn() / property.getParallelLoaders();
        int maxTestDuration = Integer.parseInt(SystemEnv.MAX_TEST_DURATION.getValue());
        @SuppressWarnings("deprecation")
        int durationSec = Math.min(maxTestDuration, Optional.ofNullable(property.getDurationTimeSec())
            .orElse(property.getDurationTimeMillis() / 1000));
        int threads = property.getThreads();

        LOGGER.info("Using " + threads + " thread(s)");

        AtomicReference<String> scheme = new AtomicReference<>(null);
        final FullHttpRequest[] requests = RequestUtils.convertPropertyToHttpRequest(property, scheme);
        if (scheme.get() == null) {
            String errMsg = "Scheme not initialized";
            LOGGER.error(errMsg);
            throw new RuntimeException(errMsg);
        }
        cookieService.saveCookies(property.getSaveCookies());
        final Proto proto = Proto.valueOf(scheme.get().toUpperCase());
        final Bootstrap bootstrap = BootstrapFactory.build(threads, property.getConnectTimeout());
        final EventLoopGroup group = bootstrap.config().group();
        Channel[] channels = new Channel[numConn];
        double lastPerformanceRate = monitorService.lastPerformanceRate();
        schedPeriod = Math.min(100, Math.max(10L, (long) (schedPeriod * lastPerformanceRate / 1.05)));

        LOGGER.info("Sched Period: " + schedPeriod + " us");

        activeChannels(numConn, proto, bootstrap, channels, requests, schedPeriod, now);

        now.set(System.currentTimeMillis());
        executor.schedule(() -> {
            long nowPreShut = System.currentTimeMillis();

            closeChannels(channels, 10, TimeUnit.SECONDS);
            group.shutdownGracefully(1L, 10L, TimeUnit.SECONDS);

            monitorService.showReport(now.get() - (System.currentTimeMillis() - nowPreShut));
            cookieService.reset();
        }, durationSec, TimeUnit.SECONDS);

        boolean forceReconnect = property.getForceReconnect();
        reconnectIfNecessary(forceReconnect, numConn, proto, group, bootstrap, channels, requests, schedPeriod, now);
    }

}
