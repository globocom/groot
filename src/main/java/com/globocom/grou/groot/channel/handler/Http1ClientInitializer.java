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

package com.globocom.grou.groot.channel.handler;

import com.globocom.grou.groot.channel.BootstrapFactory;
import com.globocom.grou.groot.monit.MonitorService;
import com.globocom.grou.groot.loader.CookieService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.Attribute;
import java.util.concurrent.TimeUnit;

public class Http1ClientInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslContext;
    private final ChannelHandler handler;
    private final MonitorService monitorService;

    public Http1ClientInitializer(
        SslContext sslContext,
        MonitorService monitorService,
        CookieService cookieService) {

        this.sslContext = sslContext;
        this.monitorService = monitorService;
        this.handler = new Http1ClientHandler(monitorService, cookieService);
    }

    @Override
    protected void initChannel(SocketChannel channel) throws Exception {
        final ChannelPipeline pipeline = channel.pipeline();
        final Attribute<Integer> idleTimeoutAttr = channel.attr(BootstrapFactory.IDLE_TIMEOUT_ATTR);
        if (idleTimeoutAttr != null) {
            Integer idleTimeout = idleTimeoutAttr.get();
            pipeline.addLast(new IdleStateHandler(idleTimeout, idleTimeout, 0, TimeUnit.SECONDS));
        }
        pipeline.addLast(new TrafficHandler(monitorService));
        if (sslContext != null) {
            pipeline.addLast(sslContext.newHandler(channel.alloc()));
        }
        pipeline.addLast(new HttpClientCodec());
        pipeline.addLast(new HttpContentDecompressor());
        pipeline.addLast(handler);
        pipeline.addLast(new ExceptionChannelInboundHandlerAdapter(monitorService));
    }

}
