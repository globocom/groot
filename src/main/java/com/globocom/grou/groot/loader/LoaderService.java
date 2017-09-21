package com.globocom.grou.groot.loader;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.strands.SuspendableRunnable;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import com.globocom.grou.groot.jbender.JBender;
import com.globocom.grou.groot.jbender.events.TimingEvent;
import com.globocom.grou.groot.jbender.executors.Validator;
import com.globocom.grou.groot.jbender.executors.http.FiberApacheHttpClientRequestExecutor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@Service
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "ConstantConditions"})
public class LoaderService {

    private static final String GROOT_USERAGENT = "Grou$Groot/1.0";

    private final Log log = LogFactory.getLog(this.getClass());

    public void start(String testName, String uri, int numConn, int durationTimeMillis) throws IOException, ExecutionException, InterruptedException {
        final Validator<CloseableHttpResponse> responseValidator = response -> {
            sendToStatsd(response);
        };
        try (final FiberApacheHttpClientRequestExecutor requestExecutor = new FiberApacheHttpClientRequestExecutor<>(responseValidator, 1000000)) {

            final Channel<HttpGet> requestChannel = Channels.newChannel(10000, Channels.OverflowPolicy.DROP);
            final Channel<TimingEvent<CloseableHttpResponse>> eventChannel = Channels.newChannel(10000, Channels.OverflowPolicy.DROP);
            final long start = System.currentTimeMillis();
            log.info("Starting test " + testName);
            final SuspendableRunnable requestGeneratorRunner = () -> {
                while (System.currentTimeMillis() - start < durationTimeMillis) {
                    requestChannel.send(new HttpGet(uri));
                }
                log.warn("closing " + requestChannel);
                requestChannel.close();
            };
            final SuspendableRunnable jbenderRunner = () -> JBender.loadTestConcurrency(numConn, 0, requestChannel, requestExecutor, eventChannel);

            new Fiber<Void>("request-generator", requestGeneratorRunner).start();
            new Fiber<Void>("jbender", jbenderRunner).start().join();
        }
        log.info("Finished test " + testName);
    }

    private void sendToStatsd(CloseableHttpResponse response) {

    }
}
