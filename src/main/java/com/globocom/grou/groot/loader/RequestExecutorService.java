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

package com.globocom.grou.groot.loader;

import com.globocom.grou.groot.SystemEnv;
import com.globocom.grou.groot.channel.BootstrapBuilder;
import com.globocom.grou.groot.channel.ChannelManagerService;
import com.globocom.grou.groot.channel.RequestUtils;
import com.globocom.grou.groot.test.properties.BaseProperty;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RequestExecutorService {

    private static final Log LOGGER = LogFactory.getLog(RequestExecutorService.class);

    private final ChannelManagerService channelManagerService;

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    @Autowired
    public RequestExecutorService(final ChannelManagerService channelManagerService) {
        this.channelManagerService = channelManagerService;
    }

    public void submit(BaseProperty property) throws RuntimeException {
        int numConn = property.getNumConn() / property.getParallelLoaders();
        int maxTestDuration = Integer.parseInt(SystemEnv.MAX_TEST_DURATION.getValue());
        @SuppressWarnings("deprecation")
        int durationSec = Math.min(maxTestDuration, Optional.ofNullable(property.getDurationTimeSec())
            .orElse(property.getDurationTimeMillis() / 1000));
        int schedPeriod = property.getFixedDelay();

        String scheme = RequestUtils.extractScheme(property);
        if (scheme == null) {
            String errMsg = "Scheme not initialized";
            LOGGER.error(errMsg);
            throw new RuntimeException(errMsg);
        }

        final FullHttpRequest[] requests = RequestUtils.convertPropertyToHttpRequest(property);
        final Proto proto = Proto.valueOf(scheme.toUpperCase());
        final Bootstrap bootstrap = BootstrapBuilder.build(property);
        final EventLoopGroup group = bootstrap.config().group();

        Channel[] channels = new Channel[numConn];
        channelManagerService.activeChannels(numConn, proto, bootstrap, channels, requests, schedPeriod);

        executor.schedule(() ->
            channelManagerService.closeChannels(group, channels, 10, TimeUnit.SECONDS), durationSec, TimeUnit.SECONDS);

        boolean forceReconnect = property.getForceReconnect();
        channelManagerService.reconnectIfNecessary(forceReconnect, numConn, proto, group, bootstrap, channels, requests, schedPeriod);
    }

}
