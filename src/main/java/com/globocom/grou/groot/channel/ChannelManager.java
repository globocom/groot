/*
 * Copyright (c) 2017-2018 Globo.com
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globocom.grou.groot.channel;

import com.globocom.grou.groot.channel.handler.CookieStorageHandler;
import com.globocom.grou.groot.channel.handler.Http1ClientInitializer;
import com.globocom.grou.groot.channel.handler.Http2ClientInitializer;
import com.globocom.grou.groot.loader.Proto;
import com.globocom.grou.groot.monit.MonitorService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.concurrent.ScheduledFuture;
import java.net.URI;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ChannelManager {

    private static final Log LOGGER = LogFactory.getLog(ChannelManager.class);

    private static final int DEFAULT_TCP_HTTP = 80;
    private static final int DEFAULT_TCP_HTTPS = 443;

    private final long start;
    private final ScheduledExecutorService executor;

    private final SslEngine sslEngine = new SslEngine();
    private List<String> ciphers = null;
    private MonitorService monitorService = null;
    private Bootstrap bootstrap = null;
    private EventLoopGroup group = null;
    private Proto proto = null;
    private FullHttpRequest[] requests = new FullHttpRequest[0];
    private int fixedDelay = 45;
    private int numConn = 0;
    private int durationSec = 0;
    @SuppressWarnings("unchecked")
    private SimpleImmutableEntry<Channel, ScheduledFuture>[] channels = new SimpleImmutableEntry[0];

    public ChannelManager() {
        this.start = System.currentTimeMillis();
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    public ChannelManager setMonitorService(MonitorService monitorService) {
        this.monitorService = monitorService;
        return this;
    }

    public ChannelManager setDurationSec(int durationSec) {
        this.durationSec = durationSec;
        return this;
    }

    public ChannelManager setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
        this.group = bootstrap.config().group();
        return this;
    }

    public ChannelManager setProto(Proto proto) {
        this.proto = proto;
        return this;
    }

    public ChannelManager setRequests(FullHttpRequest[] requests) {
        this.requests = requests;
        return this;
    }

    public ChannelManager setFixedDelay(int fixedDelay) {
        this.fixedDelay = fixedDelay;
        return this;
    }

    public ChannelManager setNumConn(int numConn) {
        this.numConn = numConn;
        //noinspection unchecked
        channels = new SimpleImmutableEntry[numConn];
        return this;
    }

    public ChannelManager setSslCiphers(List<String> ciphers) {
        this.ciphers = ciphers;
        return this;
    }

    public ChannelManager check() throws IllegalArgumentException {
        if (monitorService == null ||
            numConn == 0 ||
            durationSec == 0 ||
            bootstrap == null ||
            proto == null ||
            requests.length == 0 ||
            channels.length == 0) {
            throw new IllegalArgumentException();
        }
        return this;
    }

    private ChannelInitializer initializer(Proto proto) {
        if (proto == Proto.H2 || proto == Proto.H2C) {
            return new Http2ClientInitializer(sslEngine.setCiphers(ciphers).sslContext(proto.isSsl()), Integer.MAX_VALUE, monitorService);
        }
        return new Http1ClientInitializer(sslEngine.setCiphers(ciphers).sslContext(proto.isSsl()), monitorService);
    }

    private SimpleImmutableEntry<Channel, ScheduledFuture> newChannel() throws Exception {
        try {
            if (!bootstrap.config().group().isShuttingDown() && !bootstrap.config().group().isShutdown()) {
                URI uri = URI.create(proto.name().toLowerCase() + "://" + requests[0].headers().get(HttpHeaderNames.HOST) + requests[0].uri());
                final Channel channel = bootstrap
                    .clone()
                    .handler(initializer(proto))
                    .connect(uri.getHost(), getPort(uri))
                    .sync()
                    .channel();

                final ScheduledFuture<?> scheduledFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
                    if (channel.isActive()) {
                        for (FullHttpRequest request : requests) {
                            monitorService.writeCounterIncr();
                            channel.writeAndFlush(request.copy());
                        }
                    }
                }, fixedDelay, fixedDelay, TimeUnit.MICROSECONDS);
                return new SimpleImmutableEntry<>(channel, scheduledFuture);
            }
        } catch (CancellationException | InterruptedException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(e.getMessage(), e);
            }
        } catch (Exception e) {
            monitorService.fail(e);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
        return null;
    }

    private int getPort(URI uri) {
        if (uri.getPort() == -1) {
            int tcpPort = proto.isSsl() ? DEFAULT_TCP_HTTPS : DEFAULT_TCP_HTTP;
            return tcpPort;
        }
        return uri.getPort();
    }

    public synchronized void activeChannels() {

        final CountDownLatch latch = new CountDownLatch(numConn);
        IntStream.range(0, numConn).parallel().forEach(chanId -> {
            if (channels[chanId] == null || channels[chanId].getKey() == null || !channels[chanId].getKey().isActive()) {
                try {
                    SimpleImmutableEntry<Channel, ScheduledFuture> channel = newChannel();
                    if (channel != null) {
                        channels[chanId] = channel;
                    }
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
    }

    public void reconnect() {
        executor.scheduleAtFixedRate(() -> {
                if (System.currentTimeMillis() - start < durationSec * 1_000L) {
                    activeChannels();
                }
            }, 100, 100, TimeUnit.MILLISECONDS);

    }

    public CountDownLatch closeFutureChannels() {

        final CountDownLatch done = new CountDownLatch(1);

        group.schedule(() -> {
            long nowPreShut = System.currentTimeMillis();
            final CountDownLatch latch = new CountDownLatch(channels.length);
            try {
                for (SimpleImmutableEntry<Channel, ScheduledFuture> channel : channels) {
                    try {
                        if (channel != null && channel.getKey() != null && channel.getKey().isActive()) {
                            channel.getValue().cancel(true);
                            channel.getKey().close();
                        }
                    } finally {
                        latch.countDown();
                    }
                }
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(e.getMessage(), e);
                }
            } finally {
                group.shutdownGracefully(1L, 10L, TimeUnit.SECONDS);
                executor.shutdownNow();
                CookieStorageHandler.reset();
                LOGGER.info("Test FINISHED");
                monitorService.showReport(System.currentTimeMillis() - nowPreShut);
                done.countDown();
            }
        }, durationSec, TimeUnit.SECONDS);
        return done;
    }

}
