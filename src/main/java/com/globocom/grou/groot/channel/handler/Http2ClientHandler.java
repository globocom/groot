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
import com.globocom.grou.groot.loader.CookieService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.CharsetUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Http2ClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private static final int MAX_RESPONSE_STATUS = 599;

    private static final Log LOGGER = LogFactory.getLog(Http2ClientHandler.class);
    private final MonitorService monitorService;
    private final CookieService cookieService;

    public Http2ClientHandler(MonitorService monitorService, CookieService cookieService) {
        this.monitorService = monitorService;
        this.cookieService = cookieService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        monitorService.incrementConnectionCount();
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        monitorService.decrementConnectionCount();
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        HttpHeaders headers = msg.headers();
        Integer streamId = headers.getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        if (streamId == null) {
            LOGGER.error("HttpResponseHandler unexpected message received: " + msg);
            return;
        }
        cookieService.loadCookies(headers);
        final int statusCode = msg.status().code();
        if (statusCode >= HttpResponseStatus.CONTINUE.code() && statusCode <= MAX_RESPONSE_STATUS) {
            monitorService.sendStatus(String.valueOf(statusCode));
            monitorService.sendResponseTime();
        }

        final ByteBuf content = msg.content();
        if (content.isReadable()) {
            int contentLength = content.readableBytes();
            if (LOGGER.isDebugEnabled()) {
                byte[] arr = new byte[contentLength];
                content.readBytes(arr);
                LOGGER.debug("stream_id=" + streamId + " : " + new String(arr, 0, contentLength, CharsetUtil.UTF_8));
            }
        }

    }
}