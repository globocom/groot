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

import com.globocom.grou.groot.Application;
import io.prometheus.client.Metrics;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

public class NodeExporterClient {

    private static final String PB_ACCEPT_HEADER =
            "application/vnd.google.protobuf;" +
            "proto=io.prometheus.client.MetricFamily;" +
            "encoding=delimited;" +
            "q=0.7,text/plain;" +
            "version=0.0.4;" +
            "q=0.3,*/*;" +
            "q=0.1";

    private final Log log = LogFactory.getLog(this.getClass());

    private final AsyncHttpClient asyncHttpClient;

    public NodeExporterClient() {
        DefaultAsyncHttpClientConfig.Builder config = config()
                .setFollowRedirect(false)
                .setSoReuseAddress(true)
                .setKeepAlive(true)
                .setCompressionEnforced(true)
                .setConnectTimeout(2000)
                .setMaxConnectionsPerHost(100)
                .setMaxConnections(100)
                .setUseInsecureTrustManager(true)
                .setUserAgent(Application.GROOT_USERAGENT);
        asyncHttpClient = asyncHttpClient(config);
    }

    public Map<String, Double> get(String url)  {
        Map<String, Double> result = new HashMap<>();
        RequestBuilder builder = new RequestBuilder().setHeader("Accept", PB_ACCEPT_HEADER).setUrl(url);
        try {
            Response response = asyncHttpClient.executeRequest(builder).get();

            final ByteBuffer buf = response.getResponseBodyAsByteBuffer();
            final InputStream body = new InputStream() {

                @Override
                public int read() throws IOException {
                    if (!buf.hasRemaining()) {
                        return -1;
                    }
                    return buf.get() & 0xFF;
                }

                @Override
                public int read(byte[] bytes, int off, int len) throws IOException {
                    if (!buf.hasRemaining()) {
                        return -1;
                    }

                    len = Math.min(len, buf.remaining());
                    buf.get(bytes, off, len);
                    return len;
                }
            };

            while (buf.remaining() > 0) {
                Metrics.MetricFamily metrics = Metrics.MetricFamily.parseDelimitedFrom(body);
                if (metrics.getType() == Metrics.MetricType.SUMMARY) continue;
                metrics.getMetricList().forEach(metric -> {
                    String labels = metric.getLabelList().stream().map(l -> l.getName() + "=\"" + l.getValue() + "\"").collect(Collectors.joining(","));
                    if (!labels.isEmpty()) labels = "{" + labels + "}";
                    String key = metrics.getName() + labels;
                    double value = (metric.hasCounter()) ? metric.getCounter().getValue() : ((metric.hasGauge()) ? metric.getGauge().getValue() : -1);

                    if (log.isDebugEnabled()) log.debug(key + " " + value);
                    result.put(key, value);
                });
            }
        } catch (Exception ignore) {
            //
        }

        return result;
    }
}
