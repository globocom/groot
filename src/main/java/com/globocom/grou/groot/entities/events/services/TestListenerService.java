package com.globocom.grou.groot.entities.events.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.loader.LoaderService;
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

    private final LoaderService loaderService;

    private final Log log = LogFactory.getLog(this.getClass());

    private final JmsTemplate template;

    @Autowired
    public TestListenerService(LoaderService loaderService, JmsTemplate template) {
        this.loaderService = loaderService;
        this.template = template;
    }

    @JmsListener(destination = TEST_QUEUE)
    public void testQueue(String testStr) throws IOException {
        Test test = mapper.readValue(testStr, Test.class);

        try {
            sendToCallback(test, Test.Status.RUNNING, "RUNNING");
            Thread.sleep(2000);
            loaderService.start();
            sendToCallback(test, Test.Status.OK, "OK");

        } catch (Exception e) {
            sendToCallback(test, Test.Status.ERROR, e.getMessage());
            log.error(e.getMessage());
        }

    }

    void sendToCallback(Test test, Test.Status status, String statusDetail) throws JsonProcessingException {
        test.setStatus(status);
        test.setStatusDetailed(statusDetail);
        test.setLoader(LOADER_ID);
        template.convertAndSend(CALLBACK_QUEUE, mapper.writeValueAsString(test));
        log.info("CallbackEvent (test:" + test.getName() + ") sent to queue " + CALLBACK_QUEUE);
    }
}
