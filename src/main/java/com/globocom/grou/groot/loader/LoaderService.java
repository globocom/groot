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
import com.globocom.grou.groot.entities.Loader;
import com.globocom.grou.groot.entities.Loader.Status;
import com.globocom.grou.groot.entities.Test;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({"unchecked", "Convert2MethodRef"})
@Service
public class LoaderService {

    private static final String GROU_LOADER_REDIS_KEY = "grou:loader:" + SystemInfo.hostname();

    private static final Log LOGGER = LogFactory.getLog(LoaderService.class);

    private final RequestExecutorService asyncHttpClientService;
    private final MonitorService monitorService;
    private final StringRedisTemplate template;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    private final AtomicReference<String> currentTest = new AtomicReference<>("");
    private final AtomicBoolean abortNow = new AtomicBoolean(false);

    @Autowired
    public LoaderService(final RequestExecutorService asyncHttpClientService, final MonitorService monitorService, StringRedisTemplate template) {
        this.asyncHttpClientService = asyncHttpClientService;
        this.monitorService = monitorService;
        this.template = template;
    }

    public void start(final Test test) throws Exception {
        final String testName = test.getName();
        final String projectName = test.getProject();
        currentTest.set(projectName + "." + testName);
        final Map<String, Object> properties = test.getProperties();
        updateStatus(Status.RUNNING);

        int maxTestDuration = Integer.parseInt(SystemEnv.MAX_TEST_DURATION.getValue());
        int durationTimeMillis = Math.min(maxTestDuration, (int) properties.get("durationTimeMillis"));
        Object connectTimeoutObj = properties.get("connectTimeout");
        int connectTimeout = connectTimeoutObj != null && connectTimeoutObj instanceof Integer ? (int) connectTimeoutObj : 2000;

        final ParameterizedRequest requestBuilder = new ParameterizedRequest(test);

        LOGGER.info("Starting test " + currentTest.get());

        final long start = System.currentTimeMillis();
        try (final AsyncHttpClient asyncHttpClient = asyncHttpClientService.newClient(properties, durationTimeMillis)) {
            monitorService.monitoring(test, SystemInfo.totalSocketsTcpEstablished());
            while (!abortNow.get() && (System.currentTimeMillis() - start < durationTimeMillis)) {
                asyncHttpClientService.execute(asyncHttpClient, requestBuilder);
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            try {
                Thread.sleep(connectTimeout);
            } finally {
                stop();
            }
        }
    }

    private void stop() {
        monitorService.reset();
        updateStatus(Status.IDLE);
        abortNow.set(false);
        LOGGER.info("Finished test " + currentTest.get());
        currentTest.set("");
    }

    private void updateStatus(Status loaderStatus) {
        status.set(loaderStatus);
        updateStatusKey();
    }

    private void updateStatusKey() {
        String doubleDotWithTestName = !"".equals(currentTest.get()) ? ":" + currentTest.get() : "";
        template.opsForValue().set(GROU_LOADER_REDIS_KEY, status.get() + doubleDotWithTestName, 15000, TimeUnit.MILLISECONDS);
    }

    @Scheduled(fixedRate = 10000)
    public void register() {
        updateStatusKey();
        checkAbortNow();
    }

    private void checkAbortNow() {
        String abortKey = "ABORT:" + currentTest.get() + "#" + SystemInfo.hostname();
        String redisAbortKey = template.opsForValue().get(abortKey);
        if (redisAbortKey != null) {
            abortNow.set(true);
            template.expire(abortKey, 10, TimeUnit.MILLISECONDS);
            LOGGER.warn("TEST ABORTED: " + currentTest.get());
        }
    }

    @PreDestroy
    public void shutdown() {
        template.delete(GROU_LOADER_REDIS_KEY);
    }

}
