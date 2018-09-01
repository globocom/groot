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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChannelManagerService {

    private static final Log LOGGER = LogFactory.getLog(ChannelManagerService.class);

    private final SslService sslService;
    private final MonitorService monitorService;

    @Autowired
    public ChannelManagerService(SslService sslService, MonitorService monitorService) {
        this.sslService = sslService;
        this.monitorService = monitorService;
    }

    private ChannelInitializer initializer(Proto proto) {
        if (proto == Proto.H2 || proto == Proto.H2C) {
            return new Http2ClientInitializer(sslService.sslContext(proto.isSsl()), Integer.MAX_VALUE, monitorService);
        }
        return new Http1ClientInitializer(sslService.sslContext(proto.isSsl()), monitorService);
    }

    private SimpleImmutableEntry<Channel, ScheduledFuture> newChannel(final Bootstrap bootstrap, Proto proto, final FullHttpRequest[] requests, long schedPeriod) {
        try {
            if (!bootstrap.config().group().isShuttingDown() && !bootstrap.config().group().isShutdown()) {
                URI uri = URI.create(proto.name().toLowerCase() + "://" + requests[0].headers().get(HttpHeaderNames.HOST) + requests[0].uri());
                final Channel channel = bootstrap
                    .clone()
                    .handler(initializer(proto))
                    .connect(uri.getHost(), uri.getPort())
                    .sync()
                    .channel();
                final ScheduledFuture<?> scheduledFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
                    if (channel.isActive()) {
                        for (FullHttpRequest request : requests) {
                            monitorService.writeCounterIncr();
                            channel.writeAndFlush(request.copy());
                        }
                    }
                }, schedPeriod, schedPeriod, TimeUnit.MICROSECONDS);
                return new SimpleImmutableEntry<>(channel, scheduledFuture);
            }
        } catch (Exception e) {
            monitorService.fail(e);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
        return null;
    }

    public synchronized void activeChannels(
        int numConn,
        final Proto proto,
        final Bootstrap bootstrap,
        final SimpleImmutableEntry<Channel, ScheduledFuture>[] channels,
        final FullHttpRequest[] requests,
        final int fixedDelay) {

        final CountDownLatch latch = new CountDownLatch(numConn);
        IntStream.range(0, numConn).parallel().forEach(chanId -> {
            if (channels[chanId] == null || channels[chanId].getKey() == null || !channels[chanId].getKey().isActive()) {
                SimpleImmutableEntry<Channel, ScheduledFuture> channel = newChannel(bootstrap, proto, requests, fixedDelay);
                if (channel != null) {
                    channels[chanId] = channel;
                }
                latch.countDown();
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

    public void reconnectIfNecessary(boolean reconnect, int numConn, final Proto proto, final EventLoopGroup group,
        Bootstrap bootstrap, SimpleImmutableEntry<Channel, ScheduledFuture>[] channels, final FullHttpRequest[] requests, int fixedDelay) {
        LOGGER.info("Sched Period: " + fixedDelay + " us");
        while (reconnect && !group.isShutdown() && !group.isShuttingDown()) {
            activeChannels(numConn, proto, bootstrap, channels, requests, fixedDelay);
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                // ignored
            }
        }
    }

    public void closeChannels(EventLoopGroup group, SimpleImmutableEntry<Channel, ScheduledFuture>[] channels, int timeout, TimeUnit unit) {
        long nowPreShut = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(channels.length - 1);
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
            latch.await(timeout, unit);
        } catch (InterruptedException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(e.getMessage(), e);
            }
        } finally {
            group.shutdownGracefully(1L, 10L, TimeUnit.SECONDS);
            CookieStorageHandler.reset();

            monitorService.showReport(System.currentTimeMillis() - nowPreShut);
        }
    }

}
