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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

class Http1ClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final int MAX_RESPONSE_STATUS = 599;

    private final ReportService reportService;
    private final CookieService cookieService;

    public Http1ClientHandler(ReportService reportService, CookieService cookieService) {
        this.reportService = reportService;
        this.cookieService = cookieService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        reportService.connIncr();
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        reportService.connDecr();
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {

        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            final int statusCode = response.status().code();
            if (statusCode >= HttpResponseStatus.CONTINUE.code() && statusCode <= MAX_RESPONSE_STATUS) {
                reportService.statusIncr(statusCode);
            }
            cookieService.loadCookies(response.headers());
            reportService.bodySizeAccumulator(response.toString().length());
        }
    }

}
