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
import com.globocom.grou.groot.monit.MonitorService;
import com.globocom.grou.groot.monit.SystemInfo;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

@Service
public class RequestExecutorService {

    private static final Log LOGGER = LogFactory.getLog(RequestExecutorService.class);
    private static final long MAX_CONTENT_LENGTH = 10 * 1024 * 1024; // 10 mb

    private final MonitorService monitorService;

    @Autowired
    public RequestExecutorService(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    public void execute(final AsyncHttpClient asyncHttpClient, final ParameterizedRequest request) {

        final long start = System.currentTimeMillis();
        asyncHttpClient.executeRequest(request, new AsyncCompletionHandler<Response>() {

            @Override
            public State onHeadersReceived(HttpHeaders headers) throws Exception {
                String contentLength;
                if ((contentLength = headers.get(org.springframework.http.HttpHeaders.CONTENT_LENGTH)) != null &&
                        Integer.parseInt(contentLength) > MAX_CONTENT_LENGTH) return State.ABORT;
                return State.CONTINUE;
            }

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
        Object parallelLoadersObj = testProperties.get("parallelLoaders");
        Object connectTimeoutObj = testProperties.get("connectTimeout");
        Object keepAliveObj = testProperties.get("keepAlive");
        Object followRedirectObj = testProperties.get("followRedirect");

        int parallelLoaders = parallelLoadersObj != null && parallelLoadersObj instanceof Integer ? (int) parallelLoadersObj : 1;
        int numConn = (Integer) testProperties.get("numConn") / Math.max(1, parallelLoaders);
        int connectTimeout = connectTimeoutObj != null && connectTimeoutObj instanceof Integer ? (int) connectTimeoutObj : 2000;
        boolean keepAlive = keepAliveObj == null || !(keepAliveObj instanceof Boolean) || (boolean) keepAliveObj;
        boolean followRedirect = (followRedirectObj != null && followRedirectObj instanceof Boolean) && (boolean) followRedirectObj;

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

        if (SystemInfo.isLinux()) {
            config.setUseNativeTransport(true);
        }

        return asyncHttpClient(config);
    }
}
