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

import com.globocom.grou.groot.jetty.generator.*;
import com.globocom.grou.groot.jetty.listeners.CollectorInformations;
import com.globocom.grou.groot.jetty.listeners.report.GlobalSummaryListener;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.entities.properties.GrootProperties;
import com.globocom.grou.groot.monit.MonitorService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class TestExecutor implements Runnable {

    private static final Log LOGGER = LogFactory.getLog(TestExecutor.class);
    private static final int DEFAULT_NUM_THREADS = Runtime.getRuntime().availableProcessors();

    private final GlobalSummaryListener globalSummaryListener = new GlobalSummaryListener();
    private final LoadGenerator.Builder builder;

    private LoadGenerator loadGenerator = null;

    public TestExecutor(final Test test, long durationTimeMillis, final MonitorService monitorService) {
        long start = System.currentTimeMillis();
        final HashMap<String, Object> properties = new HashMap<>(test.getProperties());

        int users = (int) Optional.ofNullable(properties.get(GrootProperties.USERS)).orElse(0);
        int numConns = (int) Optional.ofNullable(properties.get(GrootProperties.NUM_CONN)).orElse(0);
        int channelsPerUser = numConns > 0 ? 1 : (int) Optional.ofNullable(properties.get(GrootProperties.CONNS_PER_USER)).orElse(1);
        int iterations = (int) Optional.ofNullable(properties.get(GrootProperties.ITERATIONS)).orElse(0);
        int warmupIterations = (int) Optional.ofNullable(properties.get(GrootProperties.WARMUP_ITERATIONS)).orElse(0);

        int threadsFromProperties = (int) Optional.ofNullable(properties.get(GrootProperties.THREADS)).orElse(Runtime.getRuntime().availableProcessors());
        int threads = recalNumThreadsIfNecessary(threadsFromProperties, users, numConns, iterations);

        int usersPerThread = numConns > 0 ? Math.max(1, numConns / threads) : Math.max(1, users / threads);
        int iterationsPerThread = Math.max(1, iterations / threads);
        int warmupIterationsPerThread = warmupIterations / threads;

        int resourceRate = (int) Optional.ofNullable(properties.get(GrootProperties.RESOURCE_RATE)).orElse(0);
        long rateRampUpPeriod = (long) Optional.ofNullable(properties.get(GrootProperties.RATE_RAMPUP_PERIOD)).orElse(0L);
        int numberOfNIOselectors = (int) Optional.ofNullable(properties.get(GrootProperties.NIO_SELECTORS)).orElse(1);
        int maxRequestsQueued = (int) Optional.ofNullable(properties.get(GrootProperties.MAX_REQUESTS_QUEUED)).orElse(128 * threads * 1024);
        boolean connectionBlocking = (boolean) Optional.ofNullable(properties.get(GrootProperties.BLOCKING)).orElse(true);
        long connectionTimeout = (long) Optional.ofNullable(properties.get(GrootProperties.CONNECTION_TIMEOUT)).orElse(2000L);
        long idleTimeout = (long) Optional.ofNullable(properties.get(GrootProperties.IDLE_TIMEOUT)).orElse(5000L);

        final URI uri = URI.create(String.valueOf(Optional.ofNullable(properties.get(GrootProperties.URI_REQUEST)).orElse("https://127.0.0.1:8443")));
        final String method = (String) Optional.ofNullable(properties.get(GrootProperties.METHOD)).orElse("GET");
        final HttpFields headers = getHttpFields(properties);

        final SslContextFactory sslContextFactory = new SslContextFactory(true);
        try {
            sslContextFactory.start();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        String body = "";
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            body = String.valueOf(properties.get(GrootProperties.BODY));
        }
        final Resource resource = resourceBuild(method, uri.getPath(), headers, body);
        String scheme = uri.getScheme();
        final HTTPClientTransportBuilder httpClientTransportBuilder = getHttpClientTransportBuilder(scheme, numberOfNIOselectors);
        if ("h2c".equals(scheme)) scheme = HttpScheme.HTTPS.asString();
        if ("h2".equals(scheme)) scheme = HttpScheme.HTTP.asString();
        int port = uri.getPort() > 0 ? uri.getPort() : (scheme.endsWith("s") ? 443 : 80);

        final TestListener testListener = new TestListener(monitorService, start);

        builder = new LoadGenerator.Builder()
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
                .scheme(scheme)
                .host(uri.getHost())
                .port(port)
                .maxRequestsQueued(maxRequestsQueued)
                .connectBlocking(connectionBlocking)
                .connectTimeout(connectionTimeout)
                .idleTimeout(idleTimeout)
                .resourceListener(testListener)
                .resourceListener(globalSummaryListener)
                .requestListener(testListener)
                .requestListener(globalSummaryListener);
    }

    private int recalNumThreadsIfNecessary(int threads, int users, int numConns, int iterations) {
        return IntStream.of(threads, users, numConns, iterations, DEFAULT_NUM_THREADS).filter(x -> x > 0).sorted().findFirst().orElse(1);
    }

    @Override
    public void run() {
        try {
            loadGenerator = builder.build();
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

    @SuppressWarnings("unchecked")
    private HttpFields getHttpFields(final HashMap<String, Object> properties) {
        try {
            Object headersObj = properties.get(GrootProperties.HEADERS);
            if (headersObj instanceof Map) {
                final Map<String, String> mapOfHeaders = (Map<String, String>) headersObj;
                final HttpFields httpFields = new HttpFields(mapOfHeaders.size());
                mapOfHeaders.forEach(httpFields::put);
                return httpFields;
            }
            if (headersObj instanceof List) {
                final List<?> listOfHeaders = (List<?>) headersObj;
                final HttpFields httpFields = new HttpFields(listOfHeaders.size());
                listOfHeaders.stream()
                        .filter(map -> map instanceof Map)
                        .map(map -> (Map<String, String>) map)
                        .forEach(map -> map.forEach(httpFields::put));
                return httpFields;
            }
            return new HttpFields(0);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            return new HttpFields(0);
        }
    }

    public void interrupt() {
        if (loadGenerator != null) loadGenerator.interrupt();
    }

    private Resource resourceBuild(String method, String path, final HttpFields headers, final String body) {
        Resource resource = new Resource().method(method).path(path == null || path.isEmpty() ? "/" : path).requestHeaders(headers);
        if (!(body == null || body.isEmpty())) {
            resource.setContent(body.getBytes(StandardCharsets.UTF_8));
        }
        return resource;
    }

    private HTTPClientTransportBuilder getHttpClientTransportBuilder(String schema, int numberOfNIOselectors) {
        switch (schema) {
            case "http":
            case "https": {
                return new HTTP1ClientTransportBuilder().selectors(numberOfNIOselectors);
            }
            case "h2c":
            case "h2": {
                // Chrome uses 15 MiB session and 6 MiB stream windows.
                return new HTTP2ClientTransportBuilder().sessionRecvWindow(15 * 1024 * 1024).streamRecvWindow(6 * 1024 * 1024).selectors(numberOfNIOselectors);
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
        double timeInSeconds = (end - start) / 1_000.0;
        double rqs = timeInSeconds == 0.0 ? 0.0 : (totalRequestCommitted * 1.0) / timeInSeconds;
        LOGGER.info("start: " + simpleDateFormat.format(latencyTimeSummary.getStartTimeStamp()) +
                    " , end: " + simpleDateFormat.format(latencyTimeSummary.getEndTimeStamp()) +
                    " [total: " + timeInSeconds + " secs]");
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("-----------     Estimated RPS     ------------------");
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("estimated RPS : " + rqs);
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("response 1xx family: " + globalSummaryListener.getResponses1xx().longValue());
        LOGGER.info("response 2xx family: " + globalSummaryListener.getResponses2xx().longValue());
        LOGGER.info("response 3xx family: " + globalSummaryListener.getResponses3xx().longValue());
        LOGGER.info("response 4xx family: " + globalSummaryListener.getResponses4xx().longValue());
        LOGGER.info("response 5xx family: " + globalSummaryListener.getResponses5xx().longValue());
        LOGGER.info("");
    }
}
