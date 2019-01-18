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
import com.globocom.grou.groot.channel.ChannelManager;
import com.globocom.grou.groot.channel.RequestUtils;
import com.globocom.grou.groot.monit.MonitorService;
import com.globocom.grou.groot.test.properties.BaseProperty;
import com.globocom.grou.groot.test.properties.SslProperty;
import io.netty.bootstrap.Bootstrap;
import io.netty.handler.codec.http.FullHttpRequest;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RequestExecutorService {

    private static final Log LOGGER = LogFactory.getLog(RequestExecutorService.class);

    private final MonitorService monitorService;

    @Autowired
    public RequestExecutorService(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    public void submit(BaseProperty property) throws RuntimeException {
        int numConn = property.getNumConn() / property.getParallelLoaders();
        int maxTestDuration = Integer.parseInt(SystemEnv.MAX_TEST_DURATION.getValue());
        int durationSec = getDurationSec(property, maxTestDuration);
        int fixedDelay = property.getFixedDelay();
        SslProperty sslProperty = Optional.ofNullable(property.getSsl()).orElse(new SslProperty());
        List<String> ciphers = sslProperty.getCiphers();

        String scheme = RequestUtils.extractScheme(property);
        if (scheme == null) {
            String errMsg = "Scheme not initialized";
            LOGGER.error(errMsg);
            throw new RuntimeException(errMsg);
        }

        final FullHttpRequest[] requests = RequestUtils.convertPropertyToHttpRequest(property);
        final Proto proto = Proto.valueOf(scheme.toUpperCase());
        final Bootstrap bootstrap = BootstrapBuilder.build(property);
        final ChannelManager channelManager = new ChannelManager()
            .setBootstrap(bootstrap)
            .setMonitorService(monitorService)
            .setSslCiphers(ciphers)
            .setProto(proto)
            .setDurationSec(durationSec)
            .setFixedDelay(fixedDelay)
            .setRequests(requests)
            .setNumConn(numConn)
            .check();

        CountDownLatch done = channelManager.closeFutureChannels();
        channelManager.activeChannels();
        boolean forceReconnect = property.getForceReconnect();
        if (forceReconnect) {
            channelManager.reconnect();
        }
        try {
            done.await(durationSec + 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Test OverTime");
        }
    }

    @SuppressWarnings("deprecation")
    private int getDurationSec(BaseProperty property, int maxTestDuration) {
        return Math.min(maxTestDuration, Optional.ofNullable(property.getDurationTimeSec())
            .orElse(property.getDurationTimeMillis() / 1000));
    }

}
