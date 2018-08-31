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

import com.globocom.grou.groot.monit.MonitorService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;

public class ApnChannelHandler extends ApplicationProtocolNegotiationHandler {

    private final MonitorService monitorService;
    private final HttpToHttp2ConnectionHandler connectionHandler;
    private final Http2ClientHandler http2ClientHandler;

    public ApnChannelHandler(
        final MonitorService monitorService,
        final HttpToHttp2ConnectionHandler connectionHandler,
        final Http2ClientHandler responseHandler) {

        super("");
        this.monitorService = monitorService;
        this.connectionHandler = connectionHandler;
        this.http2ClientHandler = responseHandler;
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
            ChannelPipeline p = ctx.pipeline();
            p.addLast(connectionHandler);
            p.addLast(new CookieStorageHandler());
            p.addLast(new RequestStartStamperHandler(http2ClientHandler));
            p.addLast(http2ClientHandler);
            p.addLast(new ExceptionChannelInboundHandler(monitorService));
            return;
        }
        ctx.close();
        throw new IllegalStateException("unknown protocol: " + protocol);
    }

    @Override
    protected void handshakeFailure(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        monitorService.fail(cause);
        ctx.close();
    }
}
