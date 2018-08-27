package com.globocom.grou.groot.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private static final Log LOGGER = LogFactory.getLog(ReportService.class);

    private static final List<Pattern> PATTERNS_IGNORED = Collections.emptyList();

    private static final ObjectMapper MAPPER = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

    private final AtomicLong sizeAccum = new AtomicLong(0L);
    private final AtomicInteger writeAsync = new AtomicInteger(0);
    private final AtomicInteger connCounter = new AtomicInteger(0);
    private final AtomicInteger connAccum = new AtomicInteger(0);
    private final Map<Integer, Integer> statusCounter = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedCounter = new ConcurrentHashMap<>();
    private final Map<String, Object> results = new LinkedHashMap<>();

    private double lastPerformanceRate = 1L;

    public void connIncr() {
        connAccum.incrementAndGet();
        connCounter.incrementAndGet();
    }

    public void connDecr() {
        connCounter.decrementAndGet();
    }

    public void statusIncr(int statusCode) {
        final Integer oldValue;
        if ((oldValue = statusCounter.putIfAbsent(statusCode, 1)) != null) {
            statusCounter.put(statusCode, oldValue + 1);
        }
    }

    public void failedIncr(Throwable throwable) {
        //unable_to_create_channel_from_class_class_io_netty_channel_epoll_epollsocketchannel

        final Integer oldValue;
        final AtomicReference<String> key = new AtomicReference<>(
                throwable.getMessage().replaceAll(".*Exception__", "")
                                      .replaceAll("[@.:/\\s\\t\\\\\\(\\)\\[\\]]+", "_").toLowerCase());
        if (key.get().startsWith("defaultchannelpromise")) {
            key.set("failed_channelpromise_incomplete");
        }
        if (key.get().startsWith("unable_to_create_channel_from")) {
            key.set("unable_to_create_channel");
        }
        if (PATTERNS_IGNORED.stream().map(p -> p.matcher(key.get()).matches()).count() > 0) {
            return;
        }
        if ((oldValue = failedCounter.putIfAbsent(key.get(), 1)) != null) {
            failedCounter.put(key.get(), oldValue + 1);
        }
    }

    public void writeCounterIncr() {
        writeAsync.incrementAndGet();
    }

    public void bodySizeAccumulator(long size) {
        if (size > 0) {
            sizeAccum.addAndGet(size);
        }
    }

    public synchronized void reset() {
        writeAsync.set(0);
        sizeAccum.set(0);
        connCounter.set(0);
        connAccum.set(0);
        statusCounter.clear();
        failedCounter.clear();
        results.clear();
    }

    public double lastPerformanceRate() {
        return lastPerformanceRate;
    }

    public synchronized void showReport(long start) {
        long durationSec = (System.currentTimeMillis() - start) / 1_000L;
        int numResp = statusCounter.entrySet().stream().mapToInt(Map.Entry::getValue).sum();
        int numWrites = writeAsync.get();
        long sizeTotalKb = sizeAccum.get() / 1024L;
        lastPerformanceRate = (numWrites * 1.0) / (numResp * 1.0);

        results.put("duration_sec", durationSec);
        results.put("conns_rate", connAccum.get() / durationSec);

        final Map<String, Object> status = new LinkedHashMap<>();
        statusCounter.forEach((k, v) -> {
            status.put("total_" + k, v);
            status.put("rps_" + k, v / durationSec);
        });
        failedCounter.forEach((k, v) -> {
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
        LOGGER.info("rate writes/resps: " + lastPerformanceRate);
        try {
            LOGGER.info(MAPPER.writeValueAsString(results));
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage());
        }
    }

}
