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

package com.globocom.grou.groot.monit.collectors.prometheus;

import com.globocom.grou.groot.monit.collectors.MetricsCollector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class PrometheusNodeMetricsCollector extends MetricsCollector {

    private static final Log LOGGER = LogFactory.getLog(PrometheusNodeMetricsCollector.class);

    private final NodeExporterClient nodeExporterClient = new NodeExporterClient();

    private String nodeUrl = "";
    private double lastTotalIdle = -1.0;

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
            return new BigDecimal(nodeExporterClient.get(nodeUrl).get("node_memory_MemAvailable")).doubleValue();
        } catch (Exception e) {
            return -1.0;
        }
    }

    @Override
    public int getCpuUsed() {
        try {
            double totalIdle = nodeExporterClient.get(nodeUrl).entrySet().stream()
                    .filter(e -> e.getKey().startsWith("node_cpu") && e.getKey().endsWith("mode=\"idle\"}"))
                    .map(Map.Entry::getValue).collect(Collectors.summarizingDouble(Double::doubleValue)).getAverage();

            int result = 0;
            if (lastTotalIdle >= 0.0D) {
                result = (int) (100.0 * (1.0 - Math.max(0.0, totalIdle - lastTotalIdle)));
            }
            lastTotalIdle = totalIdle;
            return result;
        } catch (Exception e) {
            return -1;
        }
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
