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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.asynchttpclient.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.sql.Date;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({"unchecked", "Convert2MethodRef"})
@Service
public class LoaderService {

    private static final String GROU_LOADER_REDIS_KEY = "grou:loader:" + SystemInfo.hostname();

    private static final Log LOGGER = LogFactory.getLog(LoaderService.class);

    private final RequestExecutorService asyncHttpClientService;
    private final MonitorService monitorService;
    private final StringRedisTemplate template;
    private final Loader myself;
    private final String buildVersion;
    private final String buildTimestamp;

    private final AtomicBoolean abortNow = new AtomicBoolean(false);
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public LoaderService(final RequestExecutorService asyncHttpClientService,
                         final MonitorService monitorService,
                         StringRedisTemplate template,
                         @Value("${build.version}") String buildVersion,
                         @Value("${build.timestamp}") String buildTimestamp) {
        this.asyncHttpClientService = asyncHttpClientService;
        this.monitorService = monitorService;
        this.template = template;
        this.buildVersion = buildVersion;
        this.buildTimestamp = buildTimestamp;
        this.myself = new Loader();
        myself.setName(SystemInfo.hostname());
        myself.setStatus(Status.IDLE);
        myself.setVersion(buildVersion + " (" + buildTimestamp + ")");
    }

    public void start(final Test test) throws Exception {
        final String testName = test.getName();
        final String projectName = test.getProject();
        myself.setStatusDetailed(projectName + "." + testName);
        myself.setLastExecAt(Date.from(Instant.now()));
        final Map<String, Object> properties = test.getProperties();
        updateStatus(Status.RUNNING);

        int maxTestDuration = Integer.parseInt(SystemEnv.MAX_TEST_DURATION.getValue());
        int durationTimeMillis = Math.min(maxTestDuration, (int) properties.get("durationTimeMillis"));
        Object connectTimeoutObj = properties.get("connectTimeout");
        int connectTimeout = connectTimeoutObj != null && connectTimeoutObj instanceof Integer ? (int) connectTimeoutObj : 2000;
        Object fixedDelayObj = properties.get("fixedDelay");
        long fixedDelay = fixedDelayObj != null && String.valueOf(fixedDelayObj).matches("\\d+") ? (long) fixedDelayObj : 0L;

        final Request request = new ParameterizedRequest(test).build();

        LOGGER.info("Starting test " + myself.getStatusDetailed());

        final long start = System.currentTimeMillis();
        try (final AsyncHttpClient asyncHttpClient = asyncHttpClientService.newClient(properties, durationTimeMillis)) {
            monitorService.monitoring(test, SystemInfo.totalSocketsTcpEstablished());
            while (!abortNow.get() && (System.currentTimeMillis() - start < durationTimeMillis)) {
                asyncHttpClientService.execute(asyncHttpClient, request);
                TimeUnit.MILLISECONDS.sleep(fixedDelay);
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
        LOGGER.info("Finished test " + myself.getStatusDetailed());
        myself.setStatusDetailed("");
    }

    private void updateStatus(Status loaderStatus) {
        myself.setStatus(loaderStatus);
        updateStatusKey();
    }

    private void updateStatusKey() {
        String loaderJson = newUndefLoaderStr();
        try {
            loaderJson = mapper.writeValueAsString(myself);
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
        }
        template.opsForValue().set(GROU_LOADER_REDIS_KEY, loaderJson, 15000, TimeUnit.MILLISECONDS);
    }

    private String newUndefLoaderStr() {
        Loader undefLoader = new Loader();
        undefLoader.setStatus(Status.ERROR);
        undefLoader.setStatusDetailed(JsonProcessingException.class.getName());
        undefLoader.setVersion(buildVersion + "." + buildTimestamp);
        try {
            return mapper.writeValueAsString(undefLoader);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @Scheduled(fixedRate = 10000)
    public void register() {
        updateStatusKey();
        checkAbortNow();
    }

    private void checkAbortNow() {
        String abortKey = "ABORT:" + myself.getStatusDetailed() + "#" + SystemInfo.hostname();
        String redisAbortKey = template.opsForValue().get(abortKey);
        if (redisAbortKey != null) {
            abortNow.set(true);
            template.expire(abortKey, 10, TimeUnit.MILLISECONDS);
            LOGGER.warn("TEST ABORTED: " + myself.getStatusDetailed());
        }
    }

    @PreDestroy
    public void shutdown() {
        template.delete(GROU_LOADER_REDIS_KEY);
    }

}
