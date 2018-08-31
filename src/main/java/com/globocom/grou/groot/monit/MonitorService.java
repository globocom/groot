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

package com.globocom.grou.groot.monit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.globocom.grou.groot.SystemEnv;
import com.globocom.grou.groot.monit.collectors.MetricsCollector;
import com.globocom.grou.groot.monit.collectors.MetricsCollectorByScheme;
import com.globocom.grou.groot.monit.collectors.zero.ZeroCollector;
import com.globocom.grou.groot.test.Test;
import com.globocom.grou.groot.test.properties.BaseProperty;
import io.galeb.statsd.StatsDClient;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MonitorService {

    private static final Log LOGGER = LogFactory.getLog(MonitorService.class);

    private static final List<Pattern> PATTERNS_IGNORED = Collections.emptyList();

    private static final String UNKNOWN = "UNKNOWN";

    private static final Pattern IS_INT = Pattern.compile("\\d+");

    private static final String LIST_SEPARATOR = ",";

    private static final ObjectMapper MAPPER = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

    private final String prefixTag = SystemEnv.PREFIX_TAG.getValue();
    private final AtomicReference<Test> test = new AtomicReference<>(null);
    private final String hostnameFormated = SystemInfo.hostname();
    private final Object lock = new Object();

    private final StatsDClient statsdClient;
    private List<MetricsCollector> targets = Collections.emptyList();
    private String prefixResponse = getStatsdPrefixResponse(null);
    private String prefixStatsdLoaderKey = getPrefixStatsdLoader(null);
    private String prefixStatsdTargetsKey = getPrefixStatsdTargets(null);
    private final Map<Integer, Long> statusCounter = new ConcurrentHashMap<>();
    private final Map<String, Long> failCounter = new ConcurrentHashMap<>();
    private final AtomicLong sizeSum = new AtomicLong(0L);
    private final AtomicInteger writeAsync = new AtomicInteger(0);
    private final AtomicInteger connCounter = new AtomicInteger(0);
    private final AtomicInteger connAccum = new AtomicInteger(0);
    private final Map<String, Object> results = new LinkedHashMap<>();

    private long testStart = System.currentTimeMillis();

    private double lastPerformanceRate = 1L;

    @Autowired
    public MonitorService(final StatsdService statsdService) {
        this.statsdClient = statsdService.client();
    }

    public void start(final Test test) {
        synchronized (lock) {
            testStart = System.currentTimeMillis();
            if (!this.test.compareAndSet(null, test)) {
                throw new IllegalStateException("Already monitoring other test");
            }
            prefixStatsdLoaderKey = getPrefixStatsdLoader(test);
            prefixStatsdTargetsKey = getPrefixStatsdTargets(test);
            extractMonitTargets(test);
        }
    }

    private String sanitize(String key, String to) {
        return key.replaceAll("[@.:/\\s\\t\\\\\\(\\)\\[\\]]+", to).toLowerCase();
    }

    private String getPrefixBase(final Test test) {
        String testName = UNKNOWN;
        String testProject = UNKNOWN;
        String testTags = "UNDEF";
        if (test != null) {
            testName = sanitize(test.getName(), "_");
            testProject = sanitize(test.getProject(), "_");
            testTags = sanitize(test.getTags().stream().sorted().collect(Collectors.joining()), "");
            testTags = "".equals(testTags) ? "UNDEF" : testTags;
        }
        return String.format("%sproject.%s.%salltags.%s.%stest.%s.%s%s.%s.",
            prefixTag, testProject, prefixTag, testTags, prefixTag, testName, prefixTag, SystemEnv.STATSD_LOADER_KEY.getValue(), sanitize(hostnameFormated, "_"));
    }

    private String getPrefixStatsdTargets(final Test test) {
        return getPrefixBase(test) + "targets." + prefixTag + SystemEnv.STATSD_TARGET_KEY.getValue() + ".";
    }

    private String getPrefixStatsdLoader(final Test test) {
        return getPrefixBase(test) + "loaders.";
    }

    private String getStatsdPrefixResponse(final Test test) {
        return getPrefixBase(test) + SystemEnv.STATSD_RESPONSE_KEY.getValue() + ".";
    }

    private void extractMonitTargets(final Test test) {
        this.prefixResponse = getStatsdPrefixResponse(test);
        final BaseProperty properties = test.getProperties();
        Object monitTargetsObj = properties.getMonitTargets();
        if (monitTargetsObj != null) {
            final List<String> monitTargets = convertObjToMonitTargets(monitTargetsObj);
            targets = monitTargets.stream().map(String::trim).map(URI::create).map(mapUriToMetricsCollector()).collect(Collectors.toList());
        } else {
            targets = Collections.emptyList();
        }
    }

    private List<String> convertObjToMonitTargets(Object obj) {
        return obj instanceof List ? (List) obj : Arrays.asList(obj.toString().split(LIST_SEPARATOR));
    }

    private Function<URI, MetricsCollector> mapUriToMetricsCollector() {
        return uri -> {
            String uriScheme = uri.getScheme();
            if (uriScheme != null) {
                try {
                    return MetricsCollectorByScheme.valueOf(uriScheme.toUpperCase()).collect(uri);
                } catch (Exception e) {
                    LOGGER.warn("Monitoring scheme problem (" + uri.getScheme() + "). Using ZeroCollector because " + e.getMessage());
                    return new ZeroCollector().setUri(uri);
                }
            }
            return new ZeroCollector().setUri(uri);
        };
    }

    public void stop() {
        synchronized (lock) {
            completeWithFakeEmptyResponse();
            test.set(null);
            targets = Collections.emptyList();
            prefixResponse = getStatsdPrefixResponse(null);
            prefixStatsdLoaderKey = getPrefixStatsdLoader(null);
            prefixStatsdTargetsKey = getPrefixStatsdTargets(null);
            writeAsync.set(0);
            sizeSum.set(0);
            connCounter.set(0);
            connAccum.set(0);
            statusCounter.clear();
            failCounter.clear();
            results.clear();
        }
    }

    private void completeWithFakeEmptyResponse() {
        try {
            TimeUnit.SECONDS.sleep(1);
            statusCounter.entrySet().stream()
                .map(Entry::getKey).forEach(s -> sendFakeResponseToStatsd(String.valueOf(s)));
            failCounter.entrySet().stream()
                .map(Entry::getKey).forEach(this::sendFakeResponseToStatsd);
        } catch (InterruptedException ignore) {
            // ignored
        }
    }

    public void fail(final Throwable t) {
        if (t != null && t.getMessage() != null) {
            boolean isInternalProblem = t.getMessage().contains("executor not accepting a task");
            String messageException = t.getMessage();
            if (!isInternalProblem) {
                if (messageException.contains("connection timed out")) {
                    messageException = "connection_timeout";
                } else if (messageException.contains("request timeout")) {
                    messageException = "request_timeout";
                } else if (t instanceof java.net.ConnectException) {
                    messageException = "conn_fail";
                } else if (t instanceof java.net.UnknownHostException) {
                    messageException = "unknown_host";
                } else if (t instanceof java.net.NoRouteToHostException) {
                    messageException = "no_route";
                } else if (messageException.startsWith("defaultchannelpromise")) {
                    messageException = "channelpromise_incomplete";
                } else if (messageException.startsWith("unable_to_create_channel_from")) {
                    messageException = "unable_to_create_channel";
                }
            }
            messageException = sanitize(messageException, "_").replaceAll(".*Exception__", "");
            sendFakeResponseToStatsd(messageException, System.currentTimeMillis(), true);
            failedIncr(messageException);
            LOGGER.error(t);
        }
    }

    private void sendFakeResponseToStatsd(String statusCode) {
        sendFakeResponseToStatsd(statusCode, System.currentTimeMillis(), false);
    }

    private void sendFakeResponseToStatsd(String statusCode, long start, boolean needCount) {
        statsdClient.recordExecutionTime(prefixResponse + "status." + prefixTag + "status." + statusCode, System.currentTimeMillis() - start);
        sendResponseTime(start);
        sendSize(0);
        if (needCount) {
            counterFromStatus(statusCode);
        }
    }

    private String counterFromStatus(String statusCode) {
        String realStatus;
        if (IS_INT.matcher(statusCode).matches()) {
            realStatus = "status_" + statusCode;
            int statusInt = Integer.parseInt(statusCode);
            statusIncr(statusInt);
        } else {
            realStatus = statusCode;
            failedIncr(statusCode);
        }
        return realStatus;
    }

    public void sendStatus(String statusCode, Long startRequest) {
        String realStatus = counterFromStatus(statusCode);
        statsdClient.recordExecutionTime(prefixResponse + "status." + prefixTag + "status." + realStatus, System.currentTimeMillis() - startRequest);
    }

    public void sendSize(long bodySize) {
        if (bodySize > 0) {
            statsdClient.recordExecutionTime(prefixResponse + "size", bodySize);
            sizeSum.addAndGet(bodySize);
        }
    }

    public void sendResponseTime(long startRequest) {
        statsdClient.recordExecutionTime(prefixResponse + "completed", System.currentTimeMillis() - startRequest);
    }

    @Scheduled(fixedRate = 1000)
    public void sendMetrics() throws IOException {
        synchronized (lock) {
            if (test.get() != null) {
                statsdClient.recordExecutionTime(prefixStatsdLoaderKey + "conns", Math.max(0, connCounter.get()));
                statsdClient.recordExecutionTime(prefixStatsdLoaderKey + "cpu", (long) (100 * SystemInfo.cpuLoad()));
                statsdClient.recordExecutionTime(prefixStatsdLoaderKey + "memFree", SystemInfo.memFree() / 1024 / 1024);

                targets.forEach(target -> {
                    String prefixStatsd = prefixStatsdTargetsKey + target.getKey() + ".";
                    int targetConns = target.getConns();
                    double targetMemFree = target.getMemFree();
                    double targetMemBuffers = target.getMemBuffers();
                    double targetMemCached = target.getMemCached();
                    int targetCpuUsed = target.getCpuUsed();
                    int targetCpuIoWait = target.getCpuIoWait();
                    int targetCpuSteal = target.getCpuSteal();
                    int targetCpuIrq = target.getCpuIrq();
                    int targetCpuSoftIrq = target.getCpuSoftIrq();
                    float targetLoad1m = target.getLoad1m();
                    float targetLoad5m = target.getLoad5m();
                    float targetLoad15m = target.getLoad15m();

                    statsdClient.recordExecutionTime(prefixStatsd + "conns", targetConns);
                    statsdClient.recordExecutionTime(prefixStatsd + "cpu", targetCpuUsed);
                    statsdClient.recordExecutionTime(prefixStatsd + "iowait", targetCpuIoWait);
                    statsdClient.recordExecutionTime(prefixStatsd + "steal", targetCpuSteal);
                    statsdClient.recordExecutionTime(prefixStatsd + "irq", targetCpuIrq);
                    statsdClient.recordExecutionTime(prefixStatsd + "softirq", targetCpuSoftIrq);
                    statsdClient.recordExecutionTime(prefixStatsd + "memFree", (long) targetMemFree / 1024 / 1024);
                    statsdClient.recordExecutionTime(prefixStatsd + "memBuffers", (long) targetMemBuffers / 1024 / 1024);
                    statsdClient.recordExecutionTime(prefixStatsd + "memCached", (long) targetMemCached / 1024 / 1024 );
                    statsdClient.gauge(prefixStatsd + "load1m", targetLoad1m);
                    statsdClient.gauge(prefixStatsd + "load5m", targetLoad5m);
                    statsdClient.gauge(prefixStatsd + "load15m", targetLoad15m);
                });
            }
        }
    }

    public void incrementConnectionCount() {
        connCounter.incrementAndGet();
        connAccum.incrementAndGet();
    }

    public void decrementConnectionCount() {
        connCounter.decrementAndGet();
    }

    public synchronized void showReport(long start) {
        long durationSec = (System.currentTimeMillis() - start - testStart) / 1_000L;
        long numResp = statusCounter.entrySet().stream().mapToLong(Map.Entry::getValue).sum();
        int numWrites = writeAsync.get();
        long sizeTotalKb = sizeSum.get() / 1024L;

        results.put("duration_sec", durationSec);
        results.put("conns_rate", connAccum.get() / durationSec);

        final Map<String, Object> status = new LinkedHashMap<>();
        statusCounter.forEach((k, v) -> {
            status.put("total_" + k, v);
            status.put("rps_" + k, v / durationSec);
        });
        failCounter.forEach((k, v) -> {
            status.put("total_" + k, v);
            status.put("rps_" + k, v / durationSec);
        });
        results.put("status", status);

        results.put("total_responses", numResp);
        results.put("rps", numResp / durationSec);
        results.put("size_total", sizeTotalKb);
        results.put("io_throughput", sizeTotalKb / durationSec);

        LOGGER.info("conns actives: " + connCounter.get());
        LOGGER.info("writes total: " + numWrites);
        LOGGER.info("rate writes/resps: " + (numWrites * 1.0) / (numResp * 1.0));
        try {
            LOGGER.info(MAPPER.writeValueAsString(results));
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    public void failedIncr(Throwable throwable) {
        failedIncr(throwable.getMessage());
    }

    public void failedIncr(String msg) {
        //unable_to_create_channel_from_class_class_io_netty_channel_epoll_epollsocketchannel
        final Long oldValue;
        if (PATTERNS_IGNORED.stream().map(p -> p.matcher(msg).matches()).count() > 0) {
            return;
        }
        if ((oldValue = failCounter.putIfAbsent(msg, 1L)) != null) {
            failCounter.put(msg, oldValue + 1L);
        }
    }

    public void writeCounterIncr() {
        writeAsync.incrementAndGet();
    }

    public void statusIncr(int statusCode) {
        final Long oldValue;
        if ((oldValue = statusCounter.putIfAbsent(statusCode, 1L)) != null) {
            statusCounter.put(statusCode, oldValue + 1L);
        }
    }

}