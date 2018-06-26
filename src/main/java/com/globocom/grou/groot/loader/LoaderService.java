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

import static com.globocom.grou.groot.SystemEnv.GROUP_NAME;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globocom.grou.groot.entities.Loader;
import com.globocom.grou.groot.entities.Loader.Status;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.monit.MonitorService;
import com.globocom.grou.groot.monit.SystemInfo;
import java.sql.Date;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@SuppressWarnings({"unchecked", "Convert2MethodRef"})
@Service
public class LoaderService {

    private static final String GROU_LOADER_REDIS_KEY =
        "grou:loader:" + GROUP_NAME.getValue() + ":" + SystemInfo.hostname();

    private static final Log LOGGER = LogFactory.getLog(LoaderService.class);

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final AbortService abortService;
    private final MonitorService monitorService;
    private final StringRedisTemplate template;
    private final Loader myself;
    private final String buildVersion;
    private final String buildTimestamp;

    private final AtomicBoolean abortNow = new AtomicBoolean(false);
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public LoaderService(final AbortService abortService,
        final MonitorService monitorService,
        StringRedisTemplate template,
        @Value("${build.version}") String buildVersion,
        @Value("${build.timestamp}") String buildTimestamp) {
        this.abortService = abortService;
        this.monitorService = monitorService;
        this.template = template;
        this.buildVersion = buildVersion;
        this.buildTimestamp = buildTimestamp;
        this.myself = new Loader();
        myself.setName(SystemInfo.hostname());
        myself.setStatus(Status.IDLE);
        myself.setGroupName(GROUP_NAME.getValue());
        myself.setVersion(buildVersion + " (" + buildTimestamp + ")");
    }

    public Loader start(final Test test) {
        final String testName = test.getName();
        final String projectName = test.getProject();
        String projectDotTest = projectName + "." + testName;
        myself.setStatusDetailed(projectDotTest);
        myself.setLastExecAt(Date.from(Instant.now()));

        startMonitor(test);
        TestExecutor testExecutor = null;
        try {
            testExecutor = new TestExecutor(test, monitorService);
            abortService.start(abortNow, testExecutor);
            executorService.submit(testExecutor).get();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (testExecutor != null) {
                testExecutor.interrupt();
            }
            if (!(executorService.isShutdown() || executorService.isTerminated())) {
                abortService.stop();
            }
        }
        return stopMonitorAndReset(projectDotTest);
    }

    private void startMonitor(Test test) {
        updateStatus(Status.RUNNING);
        LOGGER.info("Starting test " + myself.getStatusDetailed());
        monitorService.monitoring(test, SystemInfo.totalSocketsTcpEstablished());
    }

    private Loader cloneMySelf() {
        Loader loader = new Loader();
        loader.setName(myself.getName());
        loader.setStatus(myself.getStatus());
        loader.setStatusDetailed(myself.getStatusDetailed());
        loader.setVersion(myself.getVersion());
        loader.setLastExecAt(myself.getLastExecAt());
        return loader;
    }

    private Loader stopMonitorAndReset(String projectDotTest) {
        final Loader myselfBeforeStop = cloneMySelf();

        monitorService.reset();
        updateStatus(Status.IDLE);
        abortNow.set(false);
        LOGGER.info("Finished test " + projectDotTest);
        myself.setStatusDetailed("");

        return myselfBeforeStop;
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
        String currentTest = myself.getStatusDetailed();
        String abortKey = "ABORT:" + currentTest + "#" + SystemInfo.hostname();
        String redisAbortKey = template.opsForValue().get(abortKey);
        if (redisAbortKey != null) {
            abortNow.set(true);
            myself.setStatusDetailed(Test.Status.ABORTED.toString());
            myself.setStatus(Status.ERROR);
            template.expire(abortKey, 10, TimeUnit.MILLISECONDS);
            LOGGER.warn("TEST ABORTED: " + currentTest);
        }
    }

}
