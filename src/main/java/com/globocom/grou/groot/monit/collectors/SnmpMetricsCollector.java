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

import java.net.URI;

public class SnmpMetricsCollector implements MetricsCollector {

    private URI targetUri;
    private String snmpServer = "localhost";
    private String snmpPortStr = "161";
    private String snmpCommunity = "public";
    private String snmpVersion = "2c";
    private String targetFormated;

    @Override
    public MetricsCollector setUri(final URI uri) {
        targetUri = uri;
        String targetHost = uri.getHost().replaceAll("[.]", "_");
        String targetPort = uri.getPort() > 0 ? "__" + uri.getPort() : "";
        targetFormated = targetHost + targetPort;
        snmpServer = uri.getHost();
        String query = uri.getQuery();
        String[] attrs = query.split("&");
        for (String attr : attrs) {
            int indexOf;
            if ((indexOf = attr.indexOf("=")) > 0) {
                String key = attr.substring(0, indexOf);
                String value = attr.substring(indexOf + 1);
                switch (key) {
                    case "snmpServer":
                        snmpServer = value;
                        break;
                    case "snmpPort":
                        snmpPortStr = value;
                        break;
                    case "snmpCommunity":
                        snmpCommunity = value;
                        break;
                    case "snmpVersion":
                        snmpVersion = value;
                        break;
                }
            }
        }
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
        return targetFormated;
    }
}
