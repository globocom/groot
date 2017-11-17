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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class MetricsCollector {

    private String key = null;

    private String uriHost = null;
    private int uriPort = -1;
    private Map<String, String> queryParams = Collections.emptyMap();

    public MetricsCollector setUri(URI uri) {
        this.uriHost = (uri != null) ? uri.getHost() : "localhost";
        this.uriPort = (uri == null || uri.getPort() < 0) ? defaultPort() : uri.getPort();
        this.queryParams = extractQueryParams(uri);
        String keyParams = queryParams.get("key");
        this.key = (keyParams != null) ? sanitizeToStatsd(keyParams) : sanitizeToStatsd(uriHost);
        return this;
    }

    public abstract int getConns();
    public abstract double getMemFree();
    public abstract double getMemBuffers();
    public abstract double getMemCached();
    public abstract int getCpuUsed();
    public abstract int getCpuIoWait();
    public abstract int getCpuSteal();
    public abstract int getCpuIrq();
    public abstract int getCpuSoftIrq();
    public abstract float getLoad1m();
    public abstract float getLoad5m();
    public abstract float getLoad15m();

    protected abstract int defaultPort();

    public String getKey() {
        return key;
    }

    private String sanitizeToStatsd(String str) {
        return str.replaceAll("[.: ]", "_");
    }

    private Map<String, String> extractQueryParams(final URI uri) {
        if (uri == null) return Collections.emptyMap();
        final Map<String, String> result = new HashMap<>();
        String query = uri.getQuery();
        if (query != null) {
            String[] attrs = query.split("&");
            for (String attr : attrs) {
                int indexOf;
                if ((indexOf = attr.indexOf("=")) > 0) {
                    String key = attr.substring(0, indexOf);
                    String value = attr.substring(indexOf + 1);
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    public String getUriHost() {
        return uriHost;
    }

    public int getUriPort() {
        return uriPort;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }
}
