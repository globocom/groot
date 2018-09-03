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

import static com.globocom.grou.groot.SystemEnv.GROUP_NAME;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globocom.grou.groot.monit.MonitorService;
import com.globocom.grou.groot.monit.SystemInfo;
import com.globocom.grou.groot.test.Loader;
import com.globocom.grou.groot.test.Loader.Status;
import com.globocom.grou.groot.test.Test;
import com.globocom.grou.groot.test.properties.BaseProperty;
import java.sql.Date;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PreDestroy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoaderService {

    private static final String GROU_LOADER_REDIS_KEY = "grou:loader:" + GROUP_NAME.getValue() + ":" + SystemInfo.hostname();

    private static final Log LOGGER = LogFactory.getLog(LoaderService.class);

    private final MonitorService monitorService;

    private final RequestExecutorService requestExecutorService;
    private final StringRedisTemplate template;
    private final Loader loader;
    private final String buildVersion;
    private final String buildTimestamp;

    private final AtomicBoolean abortNow = new AtomicBoolean(false);
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public LoaderService(final MonitorService monitorService,
                         final RequestExecutorService requestExecutorService,
                         final StringRedisTemplate template,
                         @Value("${build.version}") String buildVersion,
                         @Value("${build.timestamp}") String buildTimestamp) {

        this.monitorService = monitorService;
        this.requestExecutorService = requestExecutorService;
        this.template = template;
        this.buildVersion = buildVersion;
        this.buildTimestamp = buildTimestamp;
        this.loader = new Loader();
        loader.setName(SystemInfo.hostname());
        loader.setStatus(Status.IDLE);
        loader.setGroupName(GROUP_NAME.getValue());
        loader.setVersion(buildVersion + " (" + buildTimestamp + ")");
    }

    public Loader start(final Test test) throws Exception {
        final String testName = test.getName();
        final String projectName = test.getProject();
        String projectDotTest = projectName + "." + testName;
        loader.setStatusDetailed(projectDotTest);
        loader.setLastExecAt(Date.from(Instant.now()));
        final BaseProperty property = test.getProperties();
        updateStatus(Status.RUNNING);

        LOGGER.info("Starting test " + loader.getStatusDetailed());

        final Loader loaderClone = loader.copy();
        try {
            monitorService.start(test);
            requestExecutorService.submit(property);
        } catch (Exception e) {
            updateStatus(Status.ERROR);
            loaderClone.setStatusDetailed(e.getMessage());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(e.getMessage(), e);
            }
        } finally {
            monitorService.stop();
            idle(projectDotTest);
        }

        return loaderClone;
    }

    private void idle(String projectDotTest) {
        abortNow.set(false);
        loader.setStatusDetailed("");
        updateStatus(Status.IDLE);
        LOGGER.info("Finished test " + projectDotTest);
    }

    private void updateStatus(Status loaderStatus) {
        loader.setStatus(loaderStatus);
        updateStatusKey();
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

    void updateStatusKey() {
        String loaderJson = newUndefLoaderStr();
        try {
            loaderJson = mapper.writeValueAsString(loader);
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
        }
        template.opsForValue().set(GROU_LOADER_REDIS_KEY, loaderJson, 15000, TimeUnit.MILLISECONDS);
    }

    void checkAbortNow() {
        String currentTest = loader.getStatusDetailed();
        String abortKey = "ABORT:" + currentTest + "#" + SystemInfo.hostname();
        String redisAbortKey = template.opsForValue().get(abortKey);
        if (redisAbortKey != null) {
            abortNow.set(true);
            loader.setStatusDetailed(Test.Status.ABORTED.toString());
            loader.setStatus(Status.ERROR);
            template.expire(abortKey, 10, TimeUnit.MILLISECONDS);
            LOGGER.warn("TEST ABORTED: " + currentTest);
        }
    }

    @PreDestroy
    public void shutdown() {
        template.delete(GROU_LOADER_REDIS_KEY);
    }

}
