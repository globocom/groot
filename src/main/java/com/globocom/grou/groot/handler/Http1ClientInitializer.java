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

package com.globocom.grou.groot.handler;

import com.globocom.grou.groot.test.CookieService;
import com.globocom.grou.groot.test.ReportService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.concurrent.TimeUnit;

public class Http1ClientInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslContext;
    private final ChannelHandler handler;
    private final ReportService reportService;

    public Http1ClientInitializer(
        SslContext sslContext,
        ReportService reportService,
        CookieService cookieService) {

        this.sslContext = sslContext;
        this.reportService = reportService;
        this.handler = new Http1ClientHandler(reportService, cookieService);
    }

    @Override
    protected void initChannel(SocketChannel channel) throws Exception {
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new IdleStateHandler(10, 10, 0, TimeUnit.SECONDS));
        pipeline.addLast(new TrafficHandler(reportService));
        if (sslContext != null) {
            pipeline.addLast(sslContext.newHandler(channel.alloc()));
        }
        pipeline.addLast(new HttpClientCodec());
        pipeline.addLast(new HttpContentDecompressor());
        pipeline.addLast(handler);
        pipeline.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                reportService.failedIncr(cause);
            }
        });
    }
}
