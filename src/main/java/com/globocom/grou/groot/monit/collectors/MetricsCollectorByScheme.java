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

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@SuppressWarnings("unused")
public enum MetricsCollectorByScheme {
    ZERO       (ZeroCollector.class),
    SNMP       (SnmpMetricsCollector.class),
    PROMETHEUS (PrometheusNodeMetricsCollector.class);

    private static final Log LOGGER = LogFactory.getLog(MetricsCollectorByScheme.class);

    private final Class<? extends MetricsCollector> targetCollectorClass;

    MetricsCollectorByScheme(Class<? extends MetricsCollector> clazz) {
        this.targetCollectorClass = clazz;
    }

    public MetricsCollector collect(final URI uri) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        try {
            return ((MetricsCollector) Class.forName(targetCollectorClass.getName()).getDeclaredConstructor().newInstance()).setUri(uri);
        } catch (InvocationTargetException | NoSuchMethodException e) {
            LOGGER.error(e);
        }
        return new ZeroCollector();
    }

}
