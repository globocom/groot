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
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.loader.LoaderService;
import com.globocom.grou.groot.monit.SystemInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class TestListenerService {

    private static final String CALLBACK_QUEUE = "grou:test_callback";
    private static final String TEST_QUEUE     = "grou:test_queue";

    private final ObjectMapper mapper = new ObjectMapper();

    private final LoaderService loaderService;

    private final Log log = LogFactory.getLog(this.getClass());

    private final JmsTemplate template;

    @Autowired
    public TestListenerService(LoaderService loaderService, JmsTemplate template) {
        this.loaderService = loaderService;
        this.template = template;
    }

    @JmsListener(destination = TEST_QUEUE, concurrency = "1-1")
    public void testQueue(String testStr) throws IOException {
        Test test = null;
        try {
            test = mapper.readValue(testStr, Test.class);
            loaderService.start(test, test.getProperties());
            sendToCallback(test, Test.Status.OK, "OK");
        } catch (Exception e) {
            if (test != null) {
                sendToCallback(test, Test.Status.ERROR, e.getMessage());
                log.error(test.getName() + ": " + e.getMessage());
            } else {
                log.error(testStr + ": " + e.getMessage());
            }
        }

    }

    private void sendToCallback(Test test, Test.Status status, String statusDetail) throws JsonProcessingException {
        test.setStatus(status);
        test.setStatusDetailed(statusDetail);
        test.setLoader(SystemInfo.hostname());
        template.convertAndSend(CALLBACK_QUEUE, mapper.writeValueAsString(test));
        log.info(String.format("CallbackEvent (test: %s, status: %s) sent to queue %s", test.getName(), status.toString(), CALLBACK_QUEUE));
    }
}
