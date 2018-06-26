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

import com.globocom.grou.groot.jetty.generator.common.Resource;

import java.io.Serializable;

/**
 * Use this one to collect all values
 */
public class DetailledTimeReportListener
    implements Serializable, Resource.NodeListener {

    private DetailledTimeValuesReport detailledResponseTimeValuesReport = new DetailledTimeValuesReport();

    private DetailledTimeValuesReport detailledLatencyTimeValuesReport = new DetailledTimeValuesReport();

    @Override
    public void onResourceNode(Resource.Info info) {
        this.detailledLatencyTimeValuesReport.addEntry(
            new DetailledTimeValuesReport.Entry(info.getRequestTime(), //
                info.getResource().getPath(), //
                info.getStatus(), //
                info.getLatencyTime() - info.getRequestTime()));

        this.detailledResponseTimeValuesReport.addEntry(
            new DetailledTimeValuesReport.Entry(info.getRequestTime(), //
                info.getResource().getPath(), //
                info.getStatus(), //
                info.getResponseTime() - info.getRequestTime()));
    }

    public DetailledTimeValuesReport getDetailledResponseTimeValuesReport() {
        return detailledResponseTimeValuesReport;
    }

    public DetailledTimeValuesReport getDetailledLatencyTimeValuesReport() {
        return detailledLatencyTimeValuesReport;
    }
}
