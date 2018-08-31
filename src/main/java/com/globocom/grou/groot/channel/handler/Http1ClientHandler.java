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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

class Http1ClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final int MAX_RESPONSE_STATUS = 599;

    private final MonitorService monitorService;

    public Http1ClientHandler(MonitorService monitorService) {
        this.monitorService = monitorService;
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
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {

        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            final int statusCode = response.status().code();
            if (statusCode >= HttpResponseStatus.CONTINUE.code() && statusCode <= MAX_RESPONSE_STATUS) {

                monitorService.sendStatus(String.valueOf(statusCode));
                //TODO: long startRequest = RequestStartStamperHandler.queue.poll();
                long startRequest = System.currentTimeMillis();
                monitorService.sendResponseTime(startRequest);
            }
        }
    }

}
