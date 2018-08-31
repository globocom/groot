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
import java.util.concurrent.ConcurrentLinkedQueue;

class Http1ResponseHandler extends SimpleChannelInboundHandler<HttpObject> implements RequestQueueStamper {

    private final ConcurrentLinkedQueue<Long> requestQueueTimes = new ConcurrentLinkedQueue<>();

    private final MonitorService monitorService;

    public Http1ResponseHandler(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        requestQueueTimes.clear();
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            sendMetrics(response.status().code(), requestQueueTimes, monitorService);
        }
    }

    @Override
    public void offer(long timestamp) {
        requestQueueTimes.offer(timestamp);
    }

}
