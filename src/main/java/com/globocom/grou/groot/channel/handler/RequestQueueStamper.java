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
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.concurrent.ConcurrentLinkedQueue;

public interface RequestQueueStamper extends ChannelHandler {

    void offer(long startRequest);

    int MAX_RESPONSE_STATUS = 599;

    default void sendMetrics(
        int statusCode,
        final ConcurrentLinkedQueue<Long> requestQueueTimes,
        final MonitorService monitorService) {

        if (statusCode >= HttpResponseStatus.CONTINUE.code() && statusCode <= MAX_RESPONSE_STATUS) {
            final Long tempStartRequest;
            final long startRequest = (tempStartRequest = requestQueueTimes.poll()) != null ? tempStartRequest : 0L;
            monitorService.sendStatus(String.valueOf(statusCode), startRequest);
            monitorService.sendResponseTime(startRequest);
        }
    }

}
