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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class TrafficHandler extends ChannelInboundHandlerAdapter {

    private final MonitorService monitorService;

    public TrafficHandler(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        long size = calculateSize(msg);
        if (size > 0) {
            monitorService.sendSize(size);
        }
        ctx.fireChannelRead(msg);
    }

    private long calculateSize(Object msg) {
        if (msg instanceof ByteBuf && ((ByteBuf) msg).isReadable()) {
            return ((ByteBuf) msg).readableBytes();
        }
        if (msg instanceof ByteBufHolder) {
            final ByteBuf content = ((ByteBufHolder) msg).content();
            if (content.isReadable()) {
                return content.readableBytes();
            }
        }
        return -1;
    }
}
