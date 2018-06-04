/*
 * Copyright (c) 2017-2018 Globo.com
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

import com.globo.grou.groot.generator.HTTP1ClientTransportBuilder;
import com.globo.grou.groot.generator.HTTP2ClientTransportBuilder;
import com.globo.grou.groot.generator.HTTPClientTransportBuilder;
import com.globo.grou.groot.generator.LoadGenerator;
import com.globo.grou.groot.generator.Resource;
import com.globo.grou.groot.generator.listeners.CollectorInformations;
import com.globo.grou.groot.generator.listeners.report.GlobalSummaryListener;
import com.globocom.grou.groot.SystemEnv;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.entities.properties.GrootProperties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TestExecutor implements Runnable {

    private static final Log LOGGER = LogFactory.getLog(TestExecutor.class);

    private int threads = Runtime.getRuntime().availableProcessors();
    private int warmupIterationsPerThread = 0;
    private int iterationsPerThread = 100000;
    private long runFor = 0;
    private int usersPerThread = 1;
    private int channelsPerUser = 1;
    private int resourceRate = 0;
    private long rateRampUpPeriod = 0;
    private int numberOfNIOselectors = 1;
    private int maxRequestsQueued = 128 * 1024;
    private boolean connectBlocking = true;
    private long connectTimeout = 5000;
    private long idleTimeout = 5000;
    private final HTTPClientTransportBuilder httpClientTransportBuilder;
    private final URI uri;
    private final SslContextFactory sslContextFactory;

    public TestExecutor(Test test) {
        int maxTestDuration = Integer.parseInt(SystemEnv.MAX_TEST_DURATION.getValue());
        runFor = Math.min(maxTestDuration, test.getDurationTimeMillis());
        LOGGER.warn("runFor: " + runFor);
        final HashMap<String, Object> properties = new HashMap<>(test.getProperties());
        Object connectTimeoutObj = properties.get(GrootProperties.CONNECTION_TIMEOUT);
        connectTimeout = connectTimeoutObj instanceof Integer ? (int) connectTimeoutObj : 2000;
        final Object uriStrObj = properties.get(GrootProperties.URI_REQUEST);
        uri = URI.create(uriStrObj != null ? uriStrObj.toString() : "https://127.0.0.1:8443");
        LOGGER.warn("uri: " + uri.toString());
        httpClientTransportBuilder = getHttpClientTransportBuilder(uri.getScheme());
        LOGGER.warn("schema: " + uri.getScheme());
        sslContextFactory = new SslContextFactory(true);
        try {
            sslContextFactory.start();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public void run() {
        try {
            GlobalSummaryListener globalSummaryListener = new GlobalSummaryListener();

            LoadGenerator.Builder builder = new LoadGenerator.Builder();
            final LoadGenerator loadGenerator = builder
                    .threads(threads)
                    .warmupIterationsPerThread(warmupIterationsPerThread)
                    .iterationsPerThread(iterationsPerThread)
                    .runFor(0, TimeUnit.MILLISECONDS)
                    .usersPerThread(usersPerThread)
                    .channelsPerUser(channelsPerUser)
                    .resource(resource(builder))
                    .resourceRate(resourceRate)
                    .rateRampUpPeriod(rateRampUpPeriod)
                    .httpClientTransportBuilder(httpClientTransportBuilder)
                    .sslContextFactory(sslContextFactory)
                    .scheme(uri.getScheme())
                    .host(uri.getHost())
                    .port(uri.getPort())
                    .maxRequestsQueued(maxRequestsQueued)
                    .connectBlocking(connectBlocking)
                    .connectTimeout(connectTimeout)
                    .idleTimeout(idleTimeout)
                    .resourceListener(globalSummaryListener)
                    .requestListener(globalSummaryListener)
                    .build();

            LOGGER.info("load generator config: " + loadGenerator.getConfig().toString());
            LOGGER.info("load generation begin");
            CompletableFuture<Void> cf = loadGenerator.begin();
            cf.whenComplete((ignore, throwable) -> {
                if (throwable == null) {
                    LOGGER.info("load generation complete");
                } else {
                    LOGGER.info("load generation failure", throwable);
                }
            }).join();

            displayGlobalSummaryListener(globalSummaryListener);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private Resource resource(LoadGenerator.Builder builder) {
        return new Resource("/");
    }

    private HTTPClientTransportBuilder getHttpClientTransportBuilder(String schema) {
        switch (schema) {
            case "http":
            case "https": {
                return new HTTP1ClientTransportBuilder().selectors(numberOfNIOselectors);
            }
            case "h2c":
            case "h2": {
                return new HTTP2ClientTransportBuilder().selectors(numberOfNIOselectors);
            }
            default: {
                throw new IllegalArgumentException("unsupported transport " + schema);
            }
        }
    }

    private void displayGlobalSummaryListener(GlobalSummaryListener globalSummaryListener) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss z");
        CollectorInformations latencyTimeSummary =
                new CollectorInformations(globalSummaryListener.getLatencyTimeHistogram() //
                        .getIntervalHistogram());

        long totalRequestCommitted = globalSummaryListener.getRequestCommitTotal();
        long start = latencyTimeSummary.getStartTimeStamp();
        long end = latencyTimeSummary.getEndTimeStamp();

        LOGGER.info("");
        LOGGER.info("");
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("--------    Latency Time Summary     ---------------");
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("total count:" + latencyTimeSummary.getTotalCount());
        LOGGER.info("maxLatency:" //
                + TimeUnit.NANOSECONDS.toMillis(latencyTimeSummary.getMaxValue()));
        LOGGER.info("minLatency:" //
                + TimeUnit.NANOSECONDS.toMillis(latencyTimeSummary.getMinValue()));
        LOGGER.info("aveLatency:" //
                + TimeUnit.NANOSECONDS.toMillis(Math.round(latencyTimeSummary.getMean())));
        LOGGER.info("50Latency:" //
                + TimeUnit.NANOSECONDS.toMillis(latencyTimeSummary.getValue50()));
        LOGGER.info("90Latency:" //
                + TimeUnit.NANOSECONDS.toMillis(latencyTimeSummary.getValue90()));
        LOGGER.info("stdDeviation:" //
                + TimeUnit.NANOSECONDS.toMillis(Math.round(latencyTimeSummary.getStdDeviation())));
        long timeInSeconds = TimeUnit.SECONDS.convert(end - start, TimeUnit.MILLISECONDS);
        long qps = timeInSeconds == 0L ? 0 : totalRequestCommitted / timeInSeconds;
        LOGGER.info("start: " + simpleDateFormat.format(latencyTimeSummary.getStartTimeStamp()) +
                    " , end: " + simpleDateFormat.format(latencyTimeSummary.getEndTimeStamp()) +
                    " [total: " + timeInSeconds + " secs]");
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("-----------     Estimated QPS     ------------------");
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("estimated QPS : " + qps);
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("response 1xx family: " + globalSummaryListener.getResponses1xx().longValue());
        LOGGER.info("response 2xx family: " + globalSummaryListener.getResponses2xx().longValue());
        LOGGER.info("response 3xx family: " + globalSummaryListener.getResponses3xx().longValue());
        LOGGER.info("response 4xx family: " + globalSummaryListener.getResponses4xx().longValue());
        LOGGER.info("response 5xx family: " + globalSummaryListener.getResponses5xx().longValue());
        LOGGER.info("");
    }
}
