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

package com.globocom.grou.groot.loader;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.strands.SuspendableRunnable;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.jbender.JBender;
import com.globocom.grou.groot.jbender.events.TimingEvent;
import com.globocom.grou.groot.jbender.executors.http.FiberAHC2RequestExecutor;
import com.globocom.grou.groot.jbender.util.AHC2ParameterizedRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@SuppressWarnings({"unchecked", "Convert2MethodRef"})
@Service
public class LoaderService {

    private final Log log = LogFactory.getLog(this.getClass());

    private final JBender jBender;

    @Autowired
    public LoaderService(JBender jBender) {
        this.jBender = jBender;
    }

    public void start(Test test, final Map<String, Object> properties) throws Exception {
        final String testName = test.getName();
        final int numConn = Optional.ofNullable((Integer) properties.get("numConn")).orElseThrow(() -> new IllegalArgumentException("numConn property undefined"));
        final int durationTimeMillis = Optional.ofNullable((Integer) properties.get("durationTimeMillis")).orElseThrow(() -> new IllegalArgumentException("durationTimeMillis property undefined"));

        final AHC2ParameterizedRequest requestBuilder = new AHC2ParameterizedRequest(test);

        try (final FiberAHC2RequestExecutor requestExecutor = new FiberAHC2RequestExecutor(numConn)) {

            final Channel<AHC2ParameterizedRequest> requestChannel = Channels.newChannel(10000, Channels.OverflowPolicy.DROP);
            final Channel<TimingEvent<Response>> eventChannel = Channels.newChannel(10000, Channels.OverflowPolicy.DROP);
            final long start = System.currentTimeMillis();
            log.info("Starting test " + testName);
            final SuspendableRunnable requestGeneratorRunner = () -> {
                try {
                    while (System.currentTimeMillis() - start < durationTimeMillis) {
                        requestChannel.send(requestBuilder);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                } finally {
                    log.warn("closing " + requestChannel);
                    requestChannel.close();
                }
            };
            final SuspendableRunnable jbenderRunner = () -> jBender.loadTestConcurrency(numConn, 0, requestChannel, requestExecutor, eventChannel);

            new Fiber<Void>("request-generator", requestGeneratorRunner).start();
            new Fiber<Void>("jbender", jbenderRunner).start().join();
        }
        log.info("Finished test " + testName);
    }

}
