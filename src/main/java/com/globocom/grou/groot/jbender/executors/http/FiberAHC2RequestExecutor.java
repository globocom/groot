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

package com.globocom.grou.groot.jbender.executors.http;

import co.paralleluniverse.common.util.Exceptions;
import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import com.globocom.grou.groot.Application;
import com.globocom.grou.groot.jbender.executors.RequestExecutor;
import com.globocom.grou.groot.jbender.executors.Validator;
import com.globocom.grou.groot.jbender.util.AHC2ParameterizedRequest;
import io.galeb.statsd.StatsDClient;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;

import java.util.concurrent.ExecutionException;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

public class FiberAHC2RequestExecutor implements RequestExecutor<AHC2ParameterizedRequest, Response>, AutoCloseable {

    private final Validator<Response> validator;
    private final AsyncHttpClient asyncHttpClient;

    private StatsDClient statsdClient;

    public FiberAHC2RequestExecutor(int numConn) {
        this(numConn, null);
    }

    public FiberAHC2RequestExecutor(int numConn, Validator<Response> validator) {
        this.asyncHttpClient = asyncHttpClient(config()
                .setFollowRedirect(false)
                .setSoReuseAddress(true)
                .setKeepAlive(true)
                .setConnectTimeout(2000)
                .setPooledConnectionIdleTimeout(1000)
                .setMaxConnectionsPerHost(numConn)
                .setUserAgent(Application.GROOT_USERAGENT).build());
        this.validator = validator;
    }

    @Override
    @Suspendable
    public Response execute(long nanoTime, AHC2ParameterizedRequest request) throws SuspendExecution, InterruptedException, ExecutionException {
        String prefixStatsdKey = request.getTestProject() + "." + request.getTestName();
        Response ret;
        try {
            ret = new Fiber<>(() -> {
                try {
                    Response response = asyncHttpClient.prepareRequest(request).execute().get();
                    if (statsdClient != null) {
                        int statusCode = response.getStatusCode();
                        statsdClient.recordExecutionTime(prefixStatsdKey + "." + statusCode, nanoTime);
                    }
                    return response;
                } catch (Exception e) {
                    statsdClient.recordExecutionTime(prefixStatsdKey + "." + e.getMessage()
                            .replaceAll("[ .:/]", "_").replaceAll(".*Exception__", ""), nanoTime);
                    throw Exceptions.rethrowUnwrap(e);
                }
            }).start().get();
        } catch (ExecutionException e) {
            statsdClient.recordExecutionTime(prefixStatsdKey + "." + e.getMessage()
                    .replaceAll("[ .:/]", "_").replaceAll(".*Exception__", ""), nanoTime);
            throw Exceptions.rethrowUnwrap(e);
        }
        if (validator != null) {
            validator.validate(ret);
        }
        return ret;
    }

    @Override
    public RequestExecutor<AHC2ParameterizedRequest, Response> statsdClient(StatsDClient statsdClient) {
        this.statsdClient = statsdClient;
        return this;
    }

    @Override
    public void close() throws Exception {

    }
}
