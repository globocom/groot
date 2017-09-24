package com.globocom.grou.groot.loader;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.strands.SuspendableRunnable;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.jbender.JBender;
import com.globocom.grou.groot.jbender.events.TimingEvent;
import com.globocom.grou.groot.jbender.executors.Validator;
import com.globocom.grou.groot.jbender.executors.http.FiberApacheHttpClientRequestExecutor;
import com.globocom.grou.groot.jbender.util.ParameterizedRequest;
import io.galeb.statsd.NonBlockingStatsDClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings({"unchecked", "Convert2MethodRef"})
@Service
public class LoaderService {

    private static final String STATSD_PREFIX = Optional.ofNullable(System.getenv("STATSD_PREFIX")).orElse("grou");
    private static final String STATSD_HOST   = Optional.ofNullable(System.getenv("STATSD_HOST")).orElse("localhost");
    private static final int    STATSD_PORT   = Integer.parseInt(Optional.ofNullable(System.getenv("STATSD_PORT")).orElse("8125"));

    private final Log log = LogFactory.getLog(this.getClass());

    private final NonBlockingStatsDClient statsDClient = new NonBlockingStatsDClient(STATSD_PREFIX, STATSD_HOST, STATSD_PORT);

    public void start(Test test, final Map<String, Object> properties) throws IOException, ExecutionException, InterruptedException {
        final String testName = test.getName();
        final String testProject = test.getProject();
        final int numConn = Optional.ofNullable((Integer) properties.get("numConn")).orElseThrow(() -> new IllegalArgumentException("numConn property undefined"));
        final int durationTimeMillis = Optional.ofNullable((Integer) properties.get("durationTimeMillis")).orElseThrow(() -> new IllegalArgumentException("durationTimeMillis property undefined"));

        final AtomicLong lastResponse = new AtomicLong(System.currentTimeMillis());
        final Validator<CloseableHttpResponse> responseValidator = response -> {
            int statusCode = response.getStatusLine().getStatusCode();
            long now = System.currentTimeMillis();
            long responseTime = now - lastResponse.getAndSet(now);
            statsDClient.recordExecutionTime(testProject + "." + testName + "." + statusCode, responseTime);
        };
        try (final FiberApacheHttpClientRequestExecutor requestExecutor = new FiberApacheHttpClientRequestExecutor<>(responseValidator, 1000000)) {

            final Channel<HttpEntityEnclosingRequestBase> requestChannel = Channels.newChannel(10000, Channels.OverflowPolicy.DROP);
            final Channel<TimingEvent<CloseableHttpResponse>> eventChannel = Channels.newChannel(10000, Channels.OverflowPolicy.DROP);
            final long start = System.currentTimeMillis();
            log.info("Starting test " + testName);
            final SuspendableRunnable requestGeneratorRunner = () -> {
                try {
                    while (System.currentTimeMillis() - start < durationTimeMillis) {
                        requestChannel.send(new ParameterizedRequest(properties));
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                } finally {
                    log.warn("closing " + requestChannel);
                    requestChannel.close();
                }
            };
            final SuspendableRunnable jbenderRunner = () -> JBender.loadTestConcurrency(numConn, 0, requestChannel, requestExecutor, eventChannel);

            new Fiber<Void>("request-generator", requestGeneratorRunner).start();
            new Fiber<Void>("jbender", jbenderRunner).start().join();
        }
        log.info("Finished test " + testName);
    }

}
