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
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;

@Service
public class TestListenerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestListenerService.class);

    private static final String CALLBACK_QUEUE = "grou:test_callback";
    public static final String TEST_QUEUE     = "grou:test_queue";

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
        try {
            test = mapper.readValue(testStr, Test.class);
            synchronized (lock) {
                checkProperties(test.getProperties());
                sendToCallback(test, Test.Status.RUNNING, "");
                loaderService.start(test);
                sendToCallback(test, Test.Status.OK, "OK");
            }
        } catch (Exception e) {
            if (test != null) {
                sendToCallback(test, Test.Status.ERROR, e.getMessage());
                LOGGER.error(test.getProject() + "." + test.getName() + ": " + e.getMessage());
            } else {
                LOGGER.error(testStr + ": " + e.getMessage());
            }
        }

    }

    private void sendToCallback(Test test, Test.Status status, String statusDetail) throws JsonProcessingException {
        final Loader loader = new Loader();
        loader.setName(SystemInfo.hostname());
        loader.setStatus(Enum.valueOf(Loader.Status.class, status.toString()));
        loader.setStatusDetailed(statusDetail);
        test.setLoaders(Collections.singleton(loader));
        template.convertAndSend(CALLBACK_QUEUE, mapper.writeValueAsString(test));
        LOGGER.info(String.format("CallbackEvent (test: %s.%s, status: %s) sent to queue %s", test.getProject(), test.getName(), status.toString(), CALLBACK_QUEUE));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void checkProperties(final Map<String, Object> properties) throws IllegalArgumentException {
        Object durationTimeMillis = properties.get("durationTimeMillis");
        if ((durationTimeMillis != null && durationTimeMillis instanceof Integer && (Integer) durationTimeMillis >= 1000)) {
            String maxTestDuration = SystemEnv.MAX_TEST_DURATION.getValue();
            if ((Integer) durationTimeMillis > Integer.parseInt(maxTestDuration)) {
                throw new IllegalArgumentException("durationTimeMillis property is greater than MAX_TEST_DURATION: " + maxTestDuration);
            }
        } else {
            throw new IllegalArgumentException("durationTimeMillis property undefined or less than 1000 ms");
        }
        Object uri = properties.get("uri");
        if (uri == null || ((String)uri).isEmpty()) {
            throw new IllegalArgumentException("uri property undefined");
        }
        URI uriTested = URI.create((String) uri);
        String schema = uriTested.getScheme();
        if (!schema.matches("(http[s]?|ws[s]?)")) {
            throw new IllegalArgumentException("The URI scheme, of the URI " + uri + ", must be equal (ignoring case) to ‘http’, ‘https’, ‘ws’, or ‘wss’");
        }
        Object numConn = properties.get("numConn");
        if (!(numConn != null && numConn instanceof  Integer && (Integer) numConn > 0)) {
            throw new IllegalArgumentException("numConn property undefined or less than 1 conn");
        }
    }
}
