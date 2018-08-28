package com.globocom.grou.groot.loader;

import com.globocom.grou.groot.SystemEnv;
import com.globocom.grou.groot.channel.BootstrapBuilder;
import com.globocom.grou.groot.channel.ChannelManagerService;
import com.globocom.grou.groot.channel.RequestUtils;
import com.globocom.grou.groot.channel.handler.CookieStorageHandler;
import com.globocom.grou.groot.monit.MonitorService;
import com.globocom.grou.groot.test.properties.BaseProperty;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RequestExecutorService {

    private static final Log LOGGER = LogFactory.getLog(RequestExecutorService.class);

    private final ChannelManagerService channelManagerService;
    private final MonitorService monitorService;

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    @Autowired
    public RequestExecutorService(
        final ChannelManagerService channelManagerService,
        final MonitorService monitorService) {

        this.channelManagerService = channelManagerService;
        this.monitorService = monitorService;
    }

    public void submit(BaseProperty property) throws RuntimeException {
        int numConn = property.getNumConn() / property.getParallelLoaders();
        int maxTestDuration = Integer.parseInt(SystemEnv.MAX_TEST_DURATION.getValue());
        @SuppressWarnings("deprecation")
        int durationSec = Math.min(maxTestDuration, Optional.ofNullable(property.getDurationTimeSec())
            .orElse(property.getDurationTimeMillis() / 1000));

        AtomicReference<String> scheme = new AtomicReference<>(null);
        final FullHttpRequest[] requests = RequestUtils.convertPropertyToHttpRequest(property, scheme);
        if (scheme.get() == null) {
            String errMsg = "Scheme not initialized";
            LOGGER.error(errMsg);
            throw new RuntimeException(errMsg);
        }
        final Proto proto = Proto.valueOf(scheme.get().toUpperCase());
        final Bootstrap bootstrap = BootstrapBuilder.build(property);
        final EventLoopGroup group = bootstrap.config().group();

        Channel[] channels = new Channel[numConn];
        channelManagerService.activeChannels(numConn, proto, bootstrap, channels, requests);

        executor.schedule(() -> {
            long nowPreShut = System.currentTimeMillis();

            channelManagerService.closeChannels(channels, 10, TimeUnit.SECONDS);
            group.shutdownGracefully(1L, 10L, TimeUnit.SECONDS);

            monitorService.showReport((System.currentTimeMillis() - nowPreShut));
            CookieStorageHandler.reset();
        }, durationSec, TimeUnit.SECONDS);

        boolean forceReconnect = property.getForceReconnect();
        channelManagerService.reconnectIfNecessary(forceReconnect, numConn, proto, group, bootstrap, channels, requests);
    }

}
