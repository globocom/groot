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

package com.globocom.grou.groot.entities.events.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globocom.grou.groot.SystemEnv;
import com.globocom.grou.groot.entities.Loader;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.entities.properties.PropertiesUtils;
import com.globocom.grou.groot.loader.LoaderService;
import com.globocom.grou.groot.monit.SystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;

@Service
public class TestListenerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestListenerService.class);

    private static final String CALLBACK_QUEUE = "grou:test_callback";
    public static final String TEST_QUEUE = "grou:test_queue";

    private final ObjectMapper mapper = new ObjectMapper();

    private final LoaderService loaderService;
    private final StringRedisTemplate template;
    private final Object lock = new Object();

    @Autowired
    public TestListenerService(LoaderService loaderService, StringRedisTemplate template) {
        this.loaderService = loaderService;
        this.template = template;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter() {
        return new MessageListenerAdapter((MessageListener) (message, bytes) -> {
            byte[] body = message.getBody();
            try {
                testStart(new String(body, Charset.defaultCharset()));
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    private void testStart(String testStr) throws IOException {
        Test test = null;
        Loader myself = null;
        try {
            test = mapper.readValue(testStr, Test.class);
            synchronized (lock) {
                PropertiesUtils.check(test.getProperties());
                String maxTestDuration = SystemEnv.MAX_TEST_DURATION.getValue();
                int durationTimeMillis = test.getDurationTimeMillis();
                if (durationTimeMillis > Integer.parseInt(maxTestDuration)) {
                    throw new IllegalArgumentException(durationTimeMillis + " is greater than MAX_TEST_DURATION: " + maxTestDuration);
                }
                sendRunningToCallback(test);
                myself = loaderService.start(test);
                sendToCallback(test, myself);
            }
        } catch (Exception e) {
            if (test != null) {
                if (myself != null) {
                    myself.setStatusDetailed(e.getMessage());
                    sendToCallback(test, myself);
                } else {
                    sendToCallback(test, Loader.Status.ERROR, e.getMessage());
                }
                LOGGER.error(test.getProject() + "." + test.getName() + ": " + e.getMessage());
            } else {
                LOGGER.error(testStr + ": " + e.getMessage());
            }
        }

    }

    private void sendRunningToCallback(Test test) throws JsonProcessingException {
        sendToCallback(test, Loader.Status.RUNNING, "");
    }

    private void sendToCallback(Test test, Loader.Status status, String statusDetailed) throws JsonProcessingException {
        final Loader loader = new Loader();
        loader.setName(SystemInfo.hostname());
        loader.setStatus(status);
        loader.setStatusDetailed(statusDetailed);
        sendToCallback(test, loader);
    }

    private void sendToCallback(Test test, Loader loader) throws JsonProcessingException {
        Loader.Status status = loader.getStatus();
        if (status == Loader.Status.RUNNING && !"".equals(loader.getStatusDetailed())) {
            loader.setStatus(Loader.Status.OK);
            loader.setStatusDetailed("");
        }
        test.setLoaders(Collections.singleton(loader));
        template.convertAndSend(CALLBACK_QUEUE, mapper.writeValueAsString(test));
        LOGGER.info(String.format("CallbackEvent (test: %s.%s, status: %s) sent to queue %s", test.getProject(), test.getName(), loader.getStatus(), CALLBACK_QUEUE));
    }
}
