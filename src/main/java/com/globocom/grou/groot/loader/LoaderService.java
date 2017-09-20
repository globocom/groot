package com.globocom.grou.groot.loader;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import com.globocom.grou.groot.jbender.JBender;
import com.globocom.grou.groot.jbender.events.TimingEvent;
import com.globocom.grou.groot.jbender.executors.http.FiberApacheHttpClientRequestExecutor;
import com.globocom.grou.groot.jbender.intervals.ConstantIntervalGenerator;
import com.globocom.grou.groot.jbender.intervals.IntervalGenerator;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@Service
public class LoaderService {

    final IntervalGenerator intervalGenerator = new ConstantIntervalGenerator(10000000);

    public void start() throws IOException, ExecutionException, InterruptedException {
        try(final FiberApacheHttpClientRequestExecutor requestExecutor =
            new FiberApacheHttpClientRequestExecutor<>((res) -> {
                if (res == null) {
                    throw new AssertionError("Response is null");
                }
                final int status = res.getStatusLine().getStatusCode();
                if (status != 200) {
                    throw  new AssertionError("Status is " + status);
                }
            }, 1000000)) {

            final Channel<HttpGet> requestChannel = Channels.newChannel(1000);
            final Channel<TimingEvent<CloseableHttpResponse>> eventChannel = Channels.newChannel(1000);

            new Fiber<Void>("request-generator", () -> {
                for(int i=0; i < 1000; ++i) {
                    requestChannel.send(new HttpGet("http://localhost:8090/version"));
                }

                requestChannel.close();
            }).start();

            new Fiber<Void>("jbender", () -> {
                JBender.loadTestThroughput(intervalGenerator, 0, requestChannel, requestExecutor, eventChannel);
            }).start().join();
        }
    }
}
