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

import com.globocom.grou.groot.SystemEnv;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.entities.Test.Status;
import com.globocom.grou.groot.httpclient.ParameterizedRequest;
import com.globocom.grou.groot.httpclient.RequestExecutorService;
import com.globocom.grou.groot.monit.MonitorService;
import com.globocom.grou.groot.monit.SystemInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.AsyncHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({"unchecked", "Convert2MethodRef"})
@Service
public class LoaderService {

    private static final String GROU_LOADER_REDIS_KEY = "grou:loader:" + SystemInfo.hostname();

    private static final Log LOGGER = LogFactory.getLog(LoaderService.class);

    private final RequestExecutorService asyncHttpClientService;
    private final MonitorService connectionsCounterService;
    private final StringRedisTemplate template;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.UNDEF);

    @Autowired
    public LoaderService(final RequestExecutorService asyncHttpClientService, final MonitorService connectionsCounterService, StringRedisTemplate template) {
        this.asyncHttpClientService = asyncHttpClientService;
        this.connectionsCounterService = connectionsCounterService;
        this.template = template;
    }

    public void start(Test test, final Map<String, Object> properties) throws Exception {
        updateStatus(Status.RUNNING);
        final String testName = test.getName();
        final String projectName = test.getProject();
        final int durationTimeMillis = Math.min(Integer.parseInt(SystemEnv.MAX_TEST_DURATION.getValue()),
                Optional.ofNullable((Integer) properties.get("durationTimeMillis")).orElseThrow(() -> new IllegalArgumentException("durationTimeMillis property undefined")));
        int connectTimeout = Optional.ofNullable((Integer) test.getProperties().get("connectTimeout")).orElse(2000);

        final ParameterizedRequest requestBuilder = new ParameterizedRequest(test);

        LOGGER.info("Starting test " + projectName + "." + testName);
        final long start = System.currentTimeMillis();

        try (final AsyncHttpClient asyncHttpClient = asyncHttpClientService.newClient(properties, durationTimeMillis)) {
            connectionsCounterService.monitoring(test, SystemInfo.totalSocketsTcpEstablished());
            while (System.currentTimeMillis() - start < durationTimeMillis) {
                asyncHttpClientService.execute(asyncHttpClient, requestBuilder);
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            try {
                Thread.sleep(connectTimeout);
            } finally {
                connectionsCounterService.reset();
                updateStatus(Status.UNDEF);
                LOGGER.info("Finished test " + projectName + "." + testName);
            }
        }
    }

    private void updateStatus(Status loaderStatus) {
        status.set(loaderStatus);
        updateRedis();
    }

    private void updateRedis() {
        template.opsForValue().set(GROU_LOADER_REDIS_KEY, status.get().toString(), 15000, TimeUnit.MILLISECONDS);
    }

    @Scheduled(fixedRate = 10000)
    public void register() {
        updateRedis();
    }

    @PreDestroy
    public void shutdown() {
        template.delete(GROU_LOADER_REDIS_KEY);
    }

}
