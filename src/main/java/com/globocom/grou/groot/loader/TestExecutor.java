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
import com.globocom.grou.groot.monit.MonitorService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TestExecutor implements Runnable {

    private static final Log LOGGER = LogFactory.getLog(TestExecutor.class);

    private static final int MAX_TEST_DURATION = Integer.parseInt(SystemEnv.MAX_TEST_DURATION.getValue());

    private final int threads;
    private final int warmupIterationsPerThread;
    private final int iterationsPerThread;
    private final long durationTimeMillis;
    private final int usersPerThread;
    private final int channelsPerUser;
    private final int resourceRate;
    private final long rateRampUpPeriod;
    private final int numberOfNIOselectors;
    private final int maxRequestsQueued;
    private final boolean connectionBlocking;
    private final long connectionTimeout;
    private final long idleTimeout;
    private final HTTPClientTransportBuilder httpClientTransportBuilder;
    private final URI uri;
    private final SslContextFactory sslContextFactory;
    private final Resource resource;
    private final long start;
    private final MonitorService monitorService;

    public TestExecutor(final Test test, final MonitorService monitorService) {
        this.monitorService = monitorService;
        start = System.currentTimeMillis();
        durationTimeMillis = Math.min(MAX_TEST_DURATION, test.getDurationTimeMillis());
        final HashMap<String, Object> properties = new HashMap<>(test.getProperties());

        threads = (int) Optional.ofNullable(properties.get(GrootProperties.THREADS)).orElse(Runtime.getRuntime().availableProcessors());
        warmupIterationsPerThread = (int) Optional.ofNullable(properties.get(GrootProperties.WARMUP_ITERATIONS)).orElse(0) / threads;
        iterationsPerThread = Math.max(1, (int) Optional.ofNullable(properties.get(GrootProperties.ITERATIONS)).orElse(1000) / threads);
        if (properties.containsKey(GrootProperties.NUM_CONN)) {
            int numConns = (int) Optional.ofNullable(properties.get(GrootProperties.NUM_CONN)).orElse(1);
            usersPerThread = Math.max(1, numConns / threads);
            channelsPerUser = 1;
        } else {
            usersPerThread = Math.max(1, (int) Optional.ofNullable(properties.get(GrootProperties.USERS)).orElse(1) / threads);
            channelsPerUser = (int) Optional.ofNullable(properties.get(GrootProperties.CONNS_PER_USER)).orElse(1);
        }
        resourceRate = (int) Optional.ofNullable(properties.get(GrootProperties.RESOURCE_RATE)).orElse(0);
        rateRampUpPeriod = (long) Optional.ofNullable(properties.get(GrootProperties.RATE_RAMPUP_PERIOD)).orElse(0L);
        numberOfNIOselectors = (int) Optional.ofNullable(properties.get(GrootProperties.NIO_SELECTORS)).orElse(1);
        maxRequestsQueued = (int) Optional.ofNullable(properties.get(GrootProperties.MAX_REQUESTS_QUEUED)).orElse(128 * 1024);
        connectionBlocking = (boolean) Optional.ofNullable(properties.get(GrootProperties.BLOCKING)).orElse(true);
        connectionTimeout = (long) Optional.ofNullable(properties.get(GrootProperties.CONNECTION_TIMEOUT)).orElse(2000L);
        idleTimeout = (long) Optional.ofNullable(properties.get(GrootProperties.IDLE_TIMEOUT)).orElse(5000L);
        resource = resourceBuild();

        final Object uriStrObj = properties.get(GrootProperties.URI_REQUEST);
        uri = URI.create(uriStrObj != null ? uriStrObj.toString() : "https://127.0.0.1:8443");
        httpClientTransportBuilder = getHttpClientTransportBuilder(uri.getScheme());
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
            final TestListener testListener = new TestListener(monitorService, start);
            final GlobalSummaryListener globalSummaryListener = new GlobalSummaryListener();

            LoadGenerator.Builder builder = new LoadGenerator.Builder();
            final LoadGenerator loadGenerator = builder
                    .threads(threads)
                    .warmupIterationsPerThread(warmupIterationsPerThread)
                    .iterationsPerThread(iterationsPerThread)
                    .runFor(durationTimeMillis, TimeUnit.MILLISECONDS)
                    .usersPerThread(usersPerThread)
                    .channelsPerUser(channelsPerUser)
                    .resource(resource)
                    .resourceRate(resourceRate)
                    .rateRampUpPeriod(rateRampUpPeriod)
                    .httpClientTransportBuilder(httpClientTransportBuilder)
                    .sslContextFactory(sslContextFactory)
                    .scheme(uri.getScheme())
                    .host(uri.getHost())
                    .port(uri.getPort())
                    .maxRequestsQueued(maxRequestsQueued)
                    .connectBlocking(connectionBlocking)
                    .connectTimeout(connectionTimeout)
                    .idleTimeout(idleTimeout)
                    .resourceListener(testListener)
                    .resourceListener(globalSummaryListener)
                    .requestListener(testListener)
                    .resourceListener(globalSummaryListener)
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

    private Resource resourceBuild() {
        Resource resource = new Resource();
        // TODO: convert properties to resource
        return resource.path("/");
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
