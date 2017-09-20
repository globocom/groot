package com.globocom.grou.groot.entities.events.services;

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
import java.util.concurrent.ExecutionException;

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
            test.setLoader(LOADER_ID);
            test.setStatus(Test.Status.RUNNING);

            loaderService.start();

            test.setStatus(Test.Status.OK);

            log.info("CallbackEvent (test:" + test.getName() + ") sent to queue " + CALLBACK_QUEUE);

        } catch (ExecutionException e) {
            test.setStatus(Test.Status.ERROR);
            test.setStatus_detailed("An execution error has occurred");
            log.error(e.getMessage());

        } catch (InterruptedException e) {
            test.setStatus(Test.Status.ERROR);
            test.setStatus_detailed("An unexpected interruption occurred");
            log.error(e.getMessage());

        } finally {
            template.convertAndSend(CALLBACK_QUEUE, mapper.writeValueAsString(test));
        }


    }
}
