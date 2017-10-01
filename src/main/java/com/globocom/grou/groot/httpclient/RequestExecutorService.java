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

package com.globocom.grou.groot.httpclient;

import com.globocom.grou.groot.Application;
import com.globocom.grou.groot.statsd.MonitorService;
import com.globocom.grou.groot.statsd.SystemInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.AsyncCompletionHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

@Service
public class RequestExecutorService {

    private final Log log = LogFactory.getLog(this.getClass());

    private final MonitorService monitorService;

    @Autowired
    public RequestExecutorService(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    public void execute(final AsyncHttpClient asyncHttpClient, final ParameterizedRequest request) {

        final long start = System.currentTimeMillis();
        asyncHttpClient.executeRequest(request, new AsyncCompletionHandler<Response>() {

            @Override
            public Response onCompleted(Response response) throws Exception {
                try {
                    monitorService.completed(response, start);
                } catch (Exception e) {
                    onThrowable(e);
                }
                return response;
            }

            @Override
            public void onThrowable(Throwable t) {
                monitorService.fail(t, start);
            }
        });
    }

    public AsyncHttpClient newClient(final Map<String, Object> testProperties, int durationTimeMillis) throws IllegalArgumentException {
        int numConn = Optional.ofNullable((Integer) testProperties.get("numConn")).orElseThrow(() -> new IllegalArgumentException("numConn property undefined"));
        int connectTimeout = Optional.ofNullable((Integer) testProperties.get("connectTimeout")).orElse(2000);
        boolean keepAlive = Optional.ofNullable((Boolean) testProperties.get("keepAlive")).orElse(true);
        boolean followRedirect = Optional.ofNullable((Boolean) testProperties.get("followRedirect")).orElse(false);

        DefaultAsyncHttpClientConfig.Builder config = config()
                .setFollowRedirect(followRedirect)
                .setSoReuseAddress(true)
                .setKeepAlive(keepAlive)
                .setConnectTimeout(connectTimeout)
                .setPooledConnectionIdleTimeout(durationTimeMillis)
                .setConnectionTtl(durationTimeMillis)
                .setMaxConnectionsPerHost(numConn)
                .setMaxConnections(numConn)
                .setUseInsecureTrustManager(true)
                .setUserAgent(Application.GROOT_USERAGENT);

        if (SystemInfo.getOS().startsWith("linux")) {
            config.setUseNativeTransport(true);
        }

        return asyncHttpClient(config);
    }
}
