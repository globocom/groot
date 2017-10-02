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

package com.globocom.grou.groot.monit.collectors;

import com.globocom.grou.groot.monit.collectors.prometheus.NodeExporterClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.util.stream.Collectors;

public class PrometheusNodeMetricsCollector implements MetricsCollector {

    private final Log log = LogFactory.getLog(this.getClass());

    private final NodeExporterClient nodeExporterClient = new NodeExporterClient();

    private String nodeUrl = "";
    private String targetFormated;
    private String nodeHost = null;
    private String nodePort = null;
    private double lastTotalIdle = -1.0;

    @Override
    public MetricsCollector setUri(URI uri) {
        nodeUrl = "http://" + uri.getHost() + ":9100/metrics";
        extractQueryParams(uri);
        String targetPort = uri.getPort() > 0 ? "__" + uri.getPort() : "";
        targetFormated = uri.getHost().replaceAll("[.]", "_") + targetPort;
        return this;
    }

    @Override
    public int getConns() {
        try {
            return Integer.parseInt(nodeExporterClient.get(nodeUrl).get("node_tcp_connection_states{state=\"established\"}"));
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public double getMemFree() {
        try {
            return new BigDecimal(nodeExporterClient.get(nodeUrl).get("node_memory_MemAvailable")).doubleValue();
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public int getCpuUsed() {
        try {
            double totalIdle = nodeExporterClient.get(nodeUrl).entrySet().stream()
                    .filter(e -> e.getKey().startsWith("node_cpu") && e.getKey().endsWith("mode=\"idle\"}"))
                    .map(e -> Double.parseDouble(e.getValue())).collect(Collectors.summarizingDouble(Double::doubleValue)).getAverage();

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
            return Float.parseFloat(nodeExporterClient.get(nodeUrl).get("node_load1"));
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public float getLoad5m() {
        try {
            return Float.parseFloat(nodeExporterClient.get(nodeUrl).get("node_load5"));
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public float getLoad15m() {
        try {
            return Float.parseFloat(nodeExporterClient.get(nodeUrl).get("node_load15"));
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public String getTargetFormated() {
        return targetFormated;
    }

    private void extractQueryParams(final URI uri) {
        String query = uri.getQuery();
        String[] attrs = query.split("&");
        for (String attr : attrs) {
            int indexOf;
            if ((indexOf = attr.indexOf("=")) > 0) {
                String key = attr.substring(0, indexOf);
                String value = attr.substring(indexOf + 1);
                switch (key) {
                    case "nodeHost":
                        nodeHost = value;
                        break;
                    case "nodePort":
                        nodePort = value;
                        break;
                }
            }
        }
        if (nodeHost == null) nodeHost = uri.getHost();
        if (nodePort == null) nodePort = "9100";
        nodeUrl = "http://" + nodeHost + ":" + nodePort + "/metrics";
    }
}
