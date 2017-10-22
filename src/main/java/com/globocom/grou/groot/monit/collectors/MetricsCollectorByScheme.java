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

import com.globocom.grou.groot.monit.collectors.prometheus.PrometheusNodeMetricsCollector;
import com.globocom.grou.groot.monit.collectors.snmp.SnmpMetricsCollector;
import com.globocom.grou.groot.monit.collectors.zero.ZeroCollector;

import java.net.URI;

@SuppressWarnings("unused")
public enum MetricsCollectorByScheme {
    ZERO       (ZeroCollector.class),
    SNMP       (SnmpMetricsCollector.class),
    PROMETHEUS (PrometheusNodeMetricsCollector.class);

    private final Class<? extends MetricsCollector> targetCollectorClass;

    MetricsCollectorByScheme(Class<? extends MetricsCollector> clazz) {
        this.targetCollectorClass = clazz;
    }

    public MetricsCollector collect(final URI uri) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return ((MetricsCollector) Class.forName(targetCollectorClass.getName()).newInstance()).setUri(uri);
    }

}
