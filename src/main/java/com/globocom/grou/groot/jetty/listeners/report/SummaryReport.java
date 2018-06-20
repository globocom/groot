/*
 * Copyright (c) 2017-2018 Globo.com
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

package com.globocom.grou.groot.jetty.listeners.report;

import com.globocom.grou.groot.jetty.listeners.CollectorInformations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class SummaryReport {

    private Map<String, CollectorInformations> responseTimeInformationsPerPath = new ConcurrentHashMap<>();

    private Map<String, CollectorInformations> latencyTimeInformationsPerPath = new ConcurrentHashMap<>();

    private String buildId;

    public SummaryReport(String buildId) {
        this.buildId = buildId;
    }

    public Map<String, CollectorInformations> getResponseTimeInformationsPerPath() {
        return responseTimeInformationsPerPath;
    }

    public void setResponseTimeInformationsPerPath(Map<String, CollectorInformations> responseTimeInformationsPerPath) {
        this.responseTimeInformationsPerPath = responseTimeInformationsPerPath;
    }

    public void addResponseTimeInformations(String path, CollectorInformations collectorInformations) {
        this.responseTimeInformationsPerPath.put(path, collectorInformations);
    }

    public Map<String, CollectorInformations> getLatencyTimeInformationsPerPath() {
        return latencyTimeInformationsPerPath;
    }

    public void setLatencyTimeInformationsPerPath(Map<String, CollectorInformations> latencyTimeInformationsPerPath) {
        this.latencyTimeInformationsPerPath = latencyTimeInformationsPerPath;
    }

    public void addLatencyTimeInformations(String path, CollectorInformations collectorInformations) {
        this.latencyTimeInformationsPerPath.put(path, collectorInformations);
    }
}
