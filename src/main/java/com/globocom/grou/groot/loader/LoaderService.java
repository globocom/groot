/*
 * Copyright (c) 2017-2017 Globo.com
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

import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.httpclient.ParameterizedRequest;
import com.globocom.grou.groot.httpclient.RequestExecutorService;
import com.globocom.grou.groot.statsd.MonitorService;
import com.globocom.grou.groot.statsd.SystemInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.AsyncHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@SuppressWarnings({"unchecked", "Convert2MethodRef"})
@Service
public class LoaderService {

    private static final String MAX_TEST_DURATION = Optional.ofNullable(System.getenv("MAX_TEST_DURATION")).orElse("600000");

    private final RequestExecutorService asyncHttpClientService;
    private final MonitorService connectionsCounterService;

    private final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    public LoaderService(final RequestExecutorService asyncHttpClientService, final MonitorService connectionsCounterService) {
        this.asyncHttpClientService = asyncHttpClientService;
        this.connectionsCounterService = connectionsCounterService;
    }

    public void start(Test test, final Map<String, Object> properties) throws Exception {
        final String testName = test.getName();
        final int durationTimeMillis = Math.min(Integer.parseInt(MAX_TEST_DURATION),
                Optional.ofNullable((Integer) properties.get("durationTimeMillis")).orElseThrow(() -> new IllegalArgumentException("durationTimeMillis property undefined")));

        final ParameterizedRequest requestBuilder = new ParameterizedRequest(test);

        log.info("Starting test " + testName);
        final long start = System.currentTimeMillis();

        try (final AsyncHttpClient asyncHttpClient = asyncHttpClientService.newClient(properties, durationTimeMillis)) {
            connectionsCounterService.monitoring(test, SystemInfo.totalSocketsTcpEstablished());
            while (System.currentTimeMillis() - start < durationTimeMillis) {
                asyncHttpClientService.execute(asyncHttpClient, requestBuilder);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            connectionsCounterService.reset();
            log.info("Finished test " + testName);
        }
    }

}
