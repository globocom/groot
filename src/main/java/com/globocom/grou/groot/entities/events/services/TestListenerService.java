package com.globocom.grou.groot.entities.events.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globocom.grou.groot.entities.Test;
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
    private static final String LOADER_ID      = "local";

    private final ObjectMapper mapper = new ObjectMapper();


    private final Log log = LogFactory.getLog(this.getClass());

    private final JmsTemplate template;

    @Autowired
    public TestListenerService(JmsTemplate template) {
        this.template = template;
    }

    @JmsListener(destination = TEST_QUEUE)
    public void testQueue(String testStr) throws IOException {
        Test test = mapper.readValue(testStr, Test.class);
        test.setStatus(Test.Status.OK);
        test.setLoader(LOADER_ID);
        template.convertAndSend(CALLBACK_QUEUE, mapper.writeValueAsString(test));
        log.info("CallbackEvent (test:" + test.getName() + ") sent to queue " + CALLBACK_QUEUE);
    }
}
