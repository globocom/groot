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

package com.globocom.grou.groot.statsd.collectors;

import java.net.URI;

@SuppressWarnings("unused")
public enum MetricsCollectorByScheme {
    ZERO (ZeroCollector.class),
    SNMP (SnmpMetricsCollector.class);

    private final Class<? extends MetricsCollector> targetCollectorClass;

    MetricsCollectorByScheme(Class<? extends MetricsCollector> clazz) {
        this.targetCollectorClass = clazz;
    }

    public MetricsCollector collect(final URI uri) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return ((MetricsCollector) Class.forName(targetCollectorClass.getName()).newInstance()).setUri(uri);
    }

    public static class ZeroCollector implements MetricsCollector {

        private String targetHost;
        private String targetPort;

        @Override
        public MetricsCollector setUri(final URI uri) {
            targetHost = uri.getHost().replaceAll("[.]", "_");
            targetPort = uri.getPort() > 0 ? "__" + uri.getPort() : "";
            return this;
        }

        @Override
        public int getConns() {
            return 0;
        }

        @Override
        public int getMemFree() {
            return 0;
        }

        @Override
        public int getCpuUsed() {
            return 0;
        }

        @Override
        public String getTargetFormated() {
            return targetHost + targetPort;
        }
    }

}
