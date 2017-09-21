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
import java.util.Optional;

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

    @JmsListener(destination = TEST_QUEUE, concurrency = "1-1")
    public void testQueue(String testStr) throws IOException {
        Test test = mapper.readValue(testStr, Test.class);
        log.info(testStr);
        String testName = test.getName();

        try {
            String uri = Optional.ofNullable((String) test.getProperties().get("uri")).orElseThrow(() -> new IllegalArgumentException("uri property undefined"));
            int numConn = Optional.ofNullable((Integer) test.getProperties().get("numConn")).orElseThrow(() -> new IllegalArgumentException("numConn property undefined"));
            int durationTimeMillis = Optional.ofNullable((Integer) test.getProperties().get("durationTimeMillis")).orElseThrow(() -> new IllegalArgumentException("durationTimeMillis property undefined"));
            loaderService.start(testName, uri, numConn, durationTimeMillis);
            sendToCallback(test, Test.Status.OK, "OK");
        } catch (Exception e) {
            sendToCallback(test, Test.Status.ERROR, e.getMessage());
            log.error(testName + ": " + e.getMessage());
        }

    }

    private void sendToCallback(Test test, Test.Status status, String statusDetail) throws JsonProcessingException {
        test.setStatus(status);
        test.setStatusDetailed(statusDetail);
        test.setLoader(LOADER_ID);
        template.convertAndSend(CALLBACK_QUEUE, mapper.writeValueAsString(test));
        log.info(String.format("CallbackEvent (test: %s, status: %s) sent to queue %s", test.getName(), status.toString(), CALLBACK_QUEUE));
    }
}
