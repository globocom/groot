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
import io.galeb.statsd.StatsDClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@EnableScheduling
@Service
public class MonitorService {

    private final AtomicReference<Test> test = new AtomicReference<>(null);

    private final StatsDClient statsdClient;
    private volatile int delta = 0;

    @Autowired
    public MonitorService(final StatsdService statsdService) {
        this.statsdClient = statsdService.client();
    }

    public synchronized void monitoring(final Test test, int delta) {
        if (!this.test.compareAndSet(null, test)) {
            throw new IllegalStateException("Already monitoring other test");
        } else {
            this.delta = delta;
        }
    }

    public synchronized void reset() {
        this.test.set(null);
        delta = 0;
    }

    @Scheduled(fixedRate = 1000)
    public synchronized void sendMetrics() throws IOException {
        if (test.get() != null) {
            final Map<String, Object> properties = test.get().getProperties();
            String prefixStatsdKey = test.get().getProject() + "." + test.get().getName();

            int tcpConn = SystemInfo.totalSocketsTcpEstablished();
            statsdClient.gauge(prefixStatsdKey + ".testLoader.activeConns", tcpConn - delta);
            statsdClient.gauge(prefixStatsdKey + ".testLoader.cpuLoad", SystemInfo.cpuLoad());
            statsdClient.gauge(prefixStatsdKey + ".testLoader.memFree", SystemInfo.memFree());

            String snmpTarget = (String) properties.get("snmp");
            if (snmpTarget != null) {

            }
        }
    }
}
