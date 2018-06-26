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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.globocom.grou.groot.Application;
import com.globocom.grou.groot.SystemEnv;
import com.globocom.grou.groot.jetty.generator.*;
import com.globocom.grou.groot.jetty.generator.builders.Http1ClientTransportBuilder;
import com.globocom.grou.groot.jetty.generator.builders.Http2ClientTransportBuilder;
import com.globocom.grou.groot.jetty.generator.builders.HttpClientTransportBuilder;
import com.globocom.grou.groot.jetty.generator.common.Resource;
import com.globocom.grou.groot.jetty.listeners.CollectorInformations;
import com.globocom.grou.groot.jetty.listeners.report.GlobalSummaryListener;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.entities.properties.GrootProperties;
import com.globocom.grou.groot.monit.MonitorService;
import java.util.concurrent.Executors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public class TestExecutor implements Runnable {

    private static final Log LOGGER = LogFactory.getLog(TestExecutor.class);
    private static final int DEFAULT_NUM_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int MAX_TEST_DURATION = Integer.parseInt(SystemEnv.MAX_TEST_DURATION.getValue());

    private final GlobalSummaryListener globalSummaryListener = new GlobalSummaryListener();
    private final LoadGenerator.Builder builder;
    private final long durationTimeMillis;

    private LoadGenerator loadGenerator = null;

    @SuppressWarnings({"unchecked", "checkstyle:RightCurlyAlone", "checkstyle:Indentation"})
    public TestExecutor(final Test test, final MonitorService monitorService) throws Exception {
        long start = System.currentTimeMillis();
        final Map<String, Object> properties = Optional.ofNullable(test.getProperties()).orElse(new HashMap<>());

        // TODO: Deprecated (it will be removed)
        addDurationTimeMillisPropIfAbsent(properties, test);

        this.durationTimeMillis = Math.min(MAX_TEST_DURATION,
            (int) properties.get(GrootProperties.DURATION_TIME_MILLIS));

        //@formatter:off
        final List<Map<String, Object>> requestsProp =
            (List<Map<String, Object>>) Optional.ofNullable(properties.get(GrootProperties.REQUESTS))
                .orElse(Collections.singletonList(new HashMap<>() {{
                            put(GrootProperties.ORDER, 0);
                            put(GrootProperties.URI_REQUEST, properties.get(GrootProperties.URI_REQUEST));
                            put(GrootProperties.HEADERS, properties.get(GrootProperties.HEADERS));
                            put(GrootProperties.METHOD, properties.get(GrootProperties.METHOD));
                            put(GrootProperties.BODY, properties.get(GrootProperties.BODY));
                            put(GrootProperties.AUTH, properties.get(GrootProperties.AUTH));
                            put(GrootProperties.SAVE_COOKIES, properties.get(GrootProperties.SAVE_COOKIES));
                            put(GrootProperties.CREDENTIALS, properties.get(GrootProperties.CREDENTIALS));
                            put(GrootProperties.PREEMPTIVE, properties.get(GrootProperties.PREEMPTIVE));
                        }}));
        //@formatter:on

        int users = (int) Optional.ofNullable(properties.get(GrootProperties.USERS))
            .orElse(0);
        int numConns = (int) Optional.ofNullable(properties.get(GrootProperties.NUM_CONN))
            .orElse(0);
        final int channelsPerUser = numConns > 0
            ? 1 : (int) Optional.ofNullable(properties.get(GrootProperties.CONNS_PER_USER))
            .orElse(1);
        int iterations = (int) Optional.ofNullable(properties.get(GrootProperties.ITERATIONS))
            .orElse(0);
        int warmupIterations = (int) Optional.ofNullable(properties.get(GrootProperties.WARMUP_ITERATIONS))
            .orElse(0);

        int threadsFromProperties = (int) Optional.ofNullable(properties.get(GrootProperties.THREADS))
            .orElse(Runtime.getRuntime().availableProcessors());
        int threads = recalNumThreadsIfNecessary(threadsFromProperties, users, numConns, iterations);

        final int usersPerThread = numConns > 0 ? Math.max(1, numConns / threads) : Math.max(1, users / threads);
        final int iterationsPerThread = Math.max(1, iterations / threads);
        final int warmupIterationsPerThread = warmupIterations / threads;

        final int resourceRate = (int) Optional.ofNullable(properties.get(GrootProperties.RESOURCE_RATE))
            .orElse(0);
        final long rateRampUpPeriod = (int) Optional.ofNullable(properties.get(GrootProperties.RATE_RAMPUP_PERIOD))
            .orElse(0);
        @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
        int numberOfNIOselectors = (int) Optional.ofNullable(properties.get(GrootProperties.NIO_SELECTORS))
            .orElse(1);
        final int maxRequestsQueued = (int) Optional.ofNullable(properties.get(GrootProperties.MAX_REQUESTS_QUEUED))
            .orElse(128 * threads * 1024);
        final boolean connectionBlocking = (boolean) Optional.ofNullable(properties.get(GrootProperties.BLOCKING))
            .orElse(true);
        final long connectionTimeout = (int) Optional.ofNullable(properties.get(GrootProperties.CONNECTION_TIMEOUT))
            .orElse(2000);
        final long idleTimeout = (int) Optional.ofNullable(properties.get(GrootProperties.IDLE_TIMEOUT))
            .orElse(5000);

        final AtomicReference<HttpClientTransportBuilder> httpClientTransportBuilder = new AtomicReference<>(null);
        final AtomicReference<String> scheme = new AtomicReference<>(null);
        final AtomicReference<String> host = new AtomicReference<>(null);
        final AtomicInteger port = new AtomicInteger(0);
        final SslContextFactory sslContextFactory = new SslContextFactory(true);
        try {
            sslContextFactory.start();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        final boolean saveCookies = (boolean) Optional.ofNullable(properties.get(GrootProperties.SAVE_COOKIES))
            .orElse(false);
        final Map<String, String> auth = (Map<String, String>) Optional.ofNullable(properties.get(GrootProperties.AUTH))
            .orElse(Collections.emptyMap());
        boolean authPreemptive = false;
        String username = null;
        String password = null;
        if (!auth.isEmpty()) {
            String credentials = auth.get(GrootProperties.CREDENTIALS);
            authPreemptive = Boolean.parseBoolean(Optional.ofNullable(auth.get(GrootProperties.PREEMPTIVE))
                .orElse("false"));
            int idx;
            if (credentials != null && (idx = credentials.indexOf(":")) > -1) {
                username = credentials.substring(0, idx);
                password = credentials.substring(idx + 1);
            }
        }

        final Resource resource = new Resource();
        requestsProp.forEach(requestProp ->
            resource.addResource(
                resourceBuild(requestProp, httpClientTransportBuilder, scheme, host, port, numberOfNIOselectors))
        );
        try {
            final ObjectMapper mapper = new ObjectMapper()
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            LOGGER.info(mapper.writeValueAsString(resource));
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
        }

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
            .httpClientTransportBuilder(httpClientTransportBuilder.get())
            .sslContextFactory(sslContextFactory)
            .username(username)
            .password(password)
            .saveCookies(saveCookies)
            .authPreemptive(authPreemptive)
            .userAgent(Application.GROOT_USERAGENT)
            .maxRequestsQueued(maxRequestsQueued)
            .connectBlocking(connectionBlocking)
            .connectTimeout(connectionTimeout)
            .idleTimeout(idleTimeout)
            .resourceListener(testListener)
            .resourceListener(globalSummaryListener)
            .requestListener(testListener)
            .requestListener(globalSummaryListener);
    }

    // TODO: Deprecated (it will be removed)
    @SuppressWarnings("deprecation")
    private void addDurationTimeMillisPropIfAbsent(final Map<String, Object> properties, final Test test) {
        if (!properties.containsKey(GrootProperties.DURATION_TIME_MILLIS)) {
            properties.put(GrootProperties.DURATION_TIME_MILLIS, test.getDurationTimeMillis());
        }
    }

    @SuppressWarnings("checkstyle:EmptyCatchBlock")
    private void sleep(long durationTimeMillis) {
        try {
            TimeUnit.MILLISECONDS.sleep(durationTimeMillis);
        } catch (InterruptedException ignore) {
        }
    }

    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    private Resource resourceBuild(
        final Map<String, Object> requestProp,
        final AtomicReference<HttpClientTransportBuilder> httpClientTransportBuilder,
        final AtomicReference<String> scheme,
        final AtomicReference<String> host,
        final AtomicInteger port,
        int numberOfNIOselectors) {
        final URI uri = URI.create(String.valueOf(Optional.ofNullable(requestProp.get(GrootProperties.URI_REQUEST))
            .orElse("https://127.0.0.1:8443")));
        String localScheme = uri.getScheme();
        httpClientTransportBuilder
            .compareAndExchange(null, getHttpClientTransportBuilder(localScheme, numberOfNIOselectors));

        if ("h2c".equals(localScheme)) {
            localScheme = HttpScheme.HTTPS.asString();
        }
        if ("h2".equals(localScheme)) {
            localScheme = HttpScheme.HTTP.asString();
        }

        scheme.compareAndSet(null, localScheme);
        port.compareAndSet(0, uri.getPort() > 0 ? uri.getPort() : (localScheme.endsWith("s") ? 443 : 80));
        host.compareAndSet(null, uri.getHost());

        final String method = (String) Optional.ofNullable(requestProp.get(GrootProperties.METHOD))
            .orElse("GET");
        final HttpFields headers = getHttpFields(requestProp);
        final int order = (int) Optional.ofNullable(requestProp.get(GrootProperties.ORDER))
            .orElse(Math.abs(new Random().nextInt()));

        String body = "";
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            body = String.valueOf(requestProp.get(GrootProperties.BODY));
        }
        Resource resource = new Resource().method(method).setUri(uri).requestHeaders(headers).setOrder(order);
        if (!(body == null || body.isEmpty())) {
            resource.setContent(body.getBytes(StandardCharsets.UTF_8));
        }
        return resource;
    }

    private int recalNumThreadsIfNecessary(int threads, int users, int numConns, int iterations) {
        return IntStream.of(threads, users, numConns, iterations, DEFAULT_NUM_THREADS).filter(x -> x > 0).sorted()
            .findFirst().orElse(1);
    }

    @Override
    public void run() {
        Executors.newSingleThreadExecutor().submit(() -> {
            sleep(durationTimeMillis);
            interrupt();
        });
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
    private HttpFields getHttpFields(final Map<String, Object> properties) {
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

    void interrupt() {
        loadGenerator.interrupt();
    }

    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    private HttpClientTransportBuilder getHttpClientTransportBuilder(
        String schema,
        @SuppressWarnings("checkstyle:AbbreviationAsWordInName") int numberOfNIOselectors) {

        switch (schema) {
            case "http":
            case "https": {
                return new Http1ClientTransportBuilder().selectors(numberOfNIOselectors);
            }
            case "h2c":
            case "h2": {
                // Chrome uses 15 MiB session and 6 MiB stream windows.
                return new Http2ClientTransportBuilder().sessionRecvWindow(15 * 1024 * 1024)
                    .streamRecvWindow(6 * 1024 * 1024).selectors(numberOfNIOselectors);
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
        LOGGER.info("start: " + simpleDateFormat.format(latencyTimeSummary.getStartTimeStamp())
            + " , end: " + simpleDateFormat.format(latencyTimeSummary.getEndTimeStamp())
            + " [total: " + timeInSeconds + " secs]");
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
