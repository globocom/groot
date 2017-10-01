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

package com.globocom.grou.groot.statsd;

import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.statsd.collectors.MetricsCollector;
import com.globocom.grou.groot.statsd.collectors.MetricsCollectorByScheme;
import io.galeb.statsd.StatsDClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.Response;
import org.asynchttpclient.exception.TooManyConnectionsException;
import org.asynchttpclient.exception.TooManyConnectionsPerHostException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@EnableScheduling
@Service
public class MonitorService {

    private static final String LOADER_STATSD_KEY = Optional.ofNullable(System.getenv("LOADER_STATSD_KEY")).orElse("loaders");
    private static final String TARGET_STATSD_KEY = Optional.ofNullable(System.getenv("TARGET_STATSD_KEY")).orElse("targets");
    private static final String RESPONSE_STATSD_KEY = Optional.ofNullable(System.getenv("RESPONSE_STATSD_KEY")).orElse("response");

    private final Log log = LogFactory.getLog(this.getClass());

    private final AtomicReference<Test> test = new AtomicReference<>(null);

    private final StatsDClient statsdClient;
    private volatile int delta = 0;
    private List<MetricsCollector> targets = Collections.emptyList();
    private String prefixResponse = "UNKNOW.UNKNOW." + RESPONSE_STATSD_KEY;

    @Autowired
    public MonitorService(final StatsdService statsdService) {
        this.statsdClient = statsdService.client();
    }

    public synchronized void monitoring(final Test test, int delta) {
        if (!this.test.compareAndSet(null, test)) {
            throw new IllegalStateException("Already monitoring other test");
        }
        this.delta = delta;
        extractMonitTargets(test);
    }

    private void extractMonitTargets(final Test test) {
        this.prefixResponse = String.format("%s.%s.%s.", test.getProject(), test.getName(), RESPONSE_STATSD_KEY);
        final Map<String, Object> properties = test.getProperties();
        String monitTargets = (String) properties.get("monitTargets");
        if (monitTargets != null) {
            targets = Arrays.stream(monitTargets.split(",")).map(String::trim).map(URI::create).map(uri -> {
                String uriScheme = uri.getScheme();
                if (uriScheme != null) {
                    try {
                        return MetricsCollectorByScheme.valueOf(uriScheme.toUpperCase()).collect(uri);
                    } catch (Exception e) {
                        log.warn("Monitoring scheme problem (" + uri.getScheme() + "). Using ZeroCollector because " + e.getMessage());
                        return new MetricsCollectorByScheme.ZeroCollector().setUri(uri);
                    }
                }
                return new MetricsCollectorByScheme.ZeroCollector().setUri(uri);
            }).collect(Collectors.toList());
        } else {
            targets = Collections.emptyList();
        }
    }

    public synchronized void reset() {
        this.test.set(null);
        this.targets = Collections.emptyList();
        this.prefixResponse = "UNKNOW.UNKNOW." + RESPONSE_STATSD_KEY + ".";
        delta = 0;
    }

    public void completed(final Response response, long start) {
        try {
            int statusCode = response.getStatusCode();
            int bodySize = response.getResponseBodyAsBytes().length;
            statsdClient.recordExecutionTime(prefixResponse + "status." + statusCode, System.currentTimeMillis() - start);
            statsdClient.recordExecutionTime(prefixResponse + "size", bodySize);
        } catch (Exception e) {
            fail(e, start);
        }
    }

    public void fail(final Throwable t, long start) {
        if (!((t instanceof TooManyConnectionsException) || (t instanceof TooManyConnectionsPerHostException) || t.getMessage().contains("executor not accepting a task"))) {
            String messageException = t.getMessage().replaceAll("[ .:/]", "_").replaceAll(".*Exception__", "");
            statsdClient.recordExecutionTime(prefixResponse + "status." + messageException, System.currentTimeMillis() - start);
            statsdClient.recordExecutionTime(prefixResponse + "size", 0);
            log.error(t);
        }
    }

    @Scheduled(fixedRate = 1000)
    public synchronized void sendMetrics() throws IOException {
        if (test.get() != null) {
            String hostnameFormated = SystemInfo.hostname().replaceAll("[.]", "_");
            String prefixStatsdLoaderKey = String.format("%s.%s.%s.%s.", test.get().getProject(), test.get().getName(), LOADER_STATSD_KEY, hostnameFormated);
            String prefixStatsdTargetsKey = String.format("%s.%s.%s.", test.get().getProject(), test.get().getName(), TARGET_STATSD_KEY);

            int tcpConn = SystemInfo.totalSocketsTcpEstablished();
            statsdClient.gauge(prefixStatsdLoaderKey + "conns", tcpConn - delta);
            statsdClient.gauge(prefixStatsdLoaderKey + "cpu", SystemInfo.cpuLoad());
            statsdClient.gauge(prefixStatsdLoaderKey + "memFree", SystemInfo.memFree());

            targets.forEach(target -> {
                String prefixStatsd = prefixStatsdTargetsKey + target.getTargetFormated() + ".";
                int targetConns = target.getConns();
                int targetMemFree = target.getMemFree();
                int targetCpuUsed = target.getCpuUsed();

                statsdClient.gauge(prefixStatsd + "conns", targetConns);
                statsdClient.gauge(prefixStatsd + "cpu", targetCpuUsed);
                statsdClient.gauge(prefixStatsd + "memFree", targetMemFree);
            });
        }
    }
}
