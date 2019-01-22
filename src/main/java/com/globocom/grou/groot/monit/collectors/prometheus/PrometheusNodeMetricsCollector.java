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

package com.globocom.grou.groot.monit.collectors.prometheus;

import com.globocom.grou.groot.monit.collectors.MetricsCollector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

public class PrometheusNodeMetricsCollector extends MetricsCollector {

    private static final Log LOGGER = LogFactory.getLog(PrometheusNodeMetricsCollector.class);

    private final NodeExporterClient nodeExporterClient = new NodeExporterClient();

    private String nodeUrl = "";

    private final Map<String, Double> lastCpuTotalMetric = new HashMap<String, Double>(){{
        put("idle",    -1.0);
        put("iowait",  -1.0);
        put("steal",   -1.0);
        put("irq",     -1.0);
        put("softirq", -1.0);
    }};

    private int getCpuMetric(String metric, boolean invert) {
        try {
            List<BigDecimal> metricsPerCpu = new ArrayList<>();
            nodeExporterClient.get(nodeUrl).entrySet().stream()
                    .filter(e -> e.getKey().startsWith("node_cpu") && e.getKey().endsWith("mode=\"" + metric + "\"}"))
                    .map(Map.Entry::getValue).filter(v -> v >= 0.0).forEach(v -> metricsPerCpu.add(BigDecimal.valueOf(v)));
            double total = metricsPerCpu.stream().reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue() / (double) metricsPerCpu.size();
            int result = 0;
            double cpuMetric = lastCpuTotalMetric.get(metric);
            if (cpuMetric >= 0.0D) {
                double diffTotal = Math.min(1.0, Math.max(0.0, total - cpuMetric));
                result = (int) (100.0 * (invert ? (1.0 - diffTotal) : diffTotal));
            }
            lastCpuTotalMetric.put(metric, total);
            return result;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public MetricsCollector setUri(final URI uri) {
        super.setUri(uri);
        nodeUrl = "http://" + getUriHost() + ":" + getUriPort() + "/metrics";
        return this;
    }

    @Override
    public int getConns() {
        try {
            return Optional.ofNullable(nodeExporterClient.get(nodeUrl).get("node_tcp_connection_states{state=\"established\"}")).orElse(-1.0).intValue();
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public double getMemFree() {
        try {
            if (nodeExporterClient.get(nodeUrl).containsKey("node_memory_MemAvailable")) {
                return new BigDecimal(nodeExporterClient.get(nodeUrl).get("node_memory_MemAvailable")).doubleValue();

            } else if (nodeExporterClient.get(nodeUrl).containsKey("node_memory_MemFree_bytes")) {
                return new BigDecimal(nodeExporterClient.get(nodeUrl).get("node_memory_MemFree_bytes")).doubleValue();

            } else {
                return -1.0;
            }
        } catch (Exception e) {
            return -1.0;
        }
    }

    @Override
    public double getMemBuffers() {
        try {
            if (nodeExporterClient.get(nodeUrl).containsKey("node_memory_Buffers_bytes")) {
                return new BigDecimal(nodeExporterClient.get(nodeUrl).get("node_memory_Buffers_bytes")).doubleValue();
            } else if (nodeExporterClient.get(nodeUrl).containsKey("node_memory_Buffers")) {
                return new BigDecimal(nodeExporterClient.get(nodeUrl).get("node_memory_Buffers")).doubleValue();
            } else {
                return -1.0;
            }
        } catch (Exception e) {
            return -1.0;
        }
    }

    @Override
    public double getMemCached() {
        try {
            if (nodeExporterClient.get(nodeUrl).containsKey("node_memory_Cached_bytes")) {
                return new BigDecimal(nodeExporterClient.get(nodeUrl).get("node_memory_Cached_bytes")).doubleValue();
            } else if (nodeExporterClient.get(nodeUrl).containsKey("node_memory_Cached")) {
                return new BigDecimal(nodeExporterClient.get(nodeUrl).get("node_memory_Cached")).doubleValue();
            } else {
                return -1.0;
            }
        } catch (Exception e) {
            return -1.0;
        }
    }

    @Override
    public int getCpuUsed() {
        return getCpuMetric("idle", true);
    }

    @Override
    public int getCpuIoWait() {
        return getCpuMetric("iowait", false);
    }

    @Override
    public int getCpuSteal() {
        return getCpuMetric("steal", false);
    }

    @Override
    public int getCpuIrq() {
        return getCpuMetric("irq", false);
    }

    @Override
    public int getCpuSoftIrq() {
        return getCpuMetric("softirq", false);
    }

    @Override
    public float getLoad1m() {
        try {
            return Optional.ofNullable(nodeExporterClient.get(nodeUrl).get("node_load1")).orElse(-1.0).floatValue();
        } catch (Exception e) {
            return -1.0f;
        }
    }

    @Override
    public float getLoad5m() {
        try {
            return Optional.ofNullable(nodeExporterClient.get(nodeUrl).get("node_load5")).orElse(-1.0).floatValue();
        } catch (Exception e) {
            return -1.0f;
        }
    }

    @Override
    public float getLoad15m() {
        try {
            return Optional.ofNullable(nodeExporterClient.get(nodeUrl).get("node_load15")).orElse(-1.0).floatValue();
        } catch (Exception e) {
            return -1.0f;
        }
    }

    @Override
    protected int defaultPort() {
        return 9100;
    }

}
