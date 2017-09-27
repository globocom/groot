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
import com.globocom.grou.groot.Application;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.httpclient.RequestExecutorService;
import com.globocom.grou.groot.httpclient.ParameterizedRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.AsyncHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

@SuppressWarnings({"unchecked", "Convert2MethodRef"})
@Service
public class LoaderService {

    private final RequestExecutorService asyncHttpClientService;

    private final Log log = LogFactory.getLog(this.getClass());

    @Autowired
    public LoaderService(RequestExecutorService asyncHttpClientService) {
        this.asyncHttpClientService = asyncHttpClientService;
    }

    public void start(Test test, final Map<String, Object> properties) throws Exception {
        final String testName = test.getName();
        final int numConn = Optional.ofNullable((Integer) properties.get("numConn")).orElseThrow(() -> new IllegalArgumentException("numConn property undefined"));
        final int durationTimeMillis = Optional.ofNullable((Integer) properties.get("durationTimeMillis")).orElseThrow(() -> new IllegalArgumentException("durationTimeMillis property undefined"));

        final ParameterizedRequest requestBuilder = new ParameterizedRequest(test);
        final Channel<ParameterizedRequest> requestChannel = Channels.newChannel(10000, Channels.OverflowPolicy.DROP);

        log.info("Starting test " + testName);
        final long start = System.currentTimeMillis();

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

        final SuspendableRunnable requestExecutor = () -> {
            try (final AsyncHttpClient asyncHttpClient = newAsyncHttpClient(numConn, durationTimeMillis)) {
                while (true) {
                    final ParameterizedRequest request = requestChannel.receive();
                    if (request == null) {
                        break;
                    }
                    asyncHttpClientService.execute(asyncHttpClient, request);
                }
            } catch (IOException e) {
                log.error(e);
            }
        };

        new Fiber<Void>("request-generator", requestGeneratorRunner).start();
        new Fiber<Void>("request-executor", requestExecutor).start().join();

        log.info("Finished test " + testName);
    }

    private AsyncHttpClient newAsyncHttpClient(int numConn, int durationTimeMillis) {
        return asyncHttpClient(config()
                    .setFollowRedirect(false)
                    .setSoReuseAddress(true)
                    .setKeepAlive(true)
                    .setConnectTimeout(2000)
                    .setPooledConnectionIdleTimeout(durationTimeMillis)
                    .setConnectionTtl(durationTimeMillis)
                    .setMaxConnectionsPerHost(numConn)
                    .setMaxConnections(numConn)
                    .setUseInsecureTrustManager(true)
                    .setUserAgent(Application.GROOT_USERAGENT).build());
    }

}
