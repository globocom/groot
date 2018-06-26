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
import com.globocom.grou.groot.jetty.listeners.HistogramConstants;
import org.HdrHistogram.Recorder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.client.api.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import static com.globocom.grou.groot.LogUtils.format;

/**
 * This will collect a global histogram for all response and latency times
 */
public class GlobalSummaryListener
    extends Request.Listener.Adapter
    implements Resource.NodeListener {

    private static final Log LOGGER = LogFactory.getLog(GlobalSummaryListener.class);

    private Recorder responseHistogram;
    private Recorder latencyHistogram;

    private List<Integer> excludeHttpStatusFamily = new ArrayList<>();

    private final LongAdder responses1xx = new LongAdder();

    private final LongAdder responses2xx = new LongAdder();

    private final LongAdder responses3xx = new LongAdder();

    private final LongAdder responses4xx = new LongAdder();

    private final LongAdder responses5xx = new LongAdder();

    private final LongAdder requestCommitTotal = new LongAdder();

    public GlobalSummaryListener(long lowestDiscernibleValue, long highestTrackableValue,
        int numberOfSignificantValueDigits) {
        this.responseHistogram =
            new Recorder(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
        this.latencyHistogram =
            new Recorder(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
    }

    public GlobalSummaryListener() {
        this(HistogramConstants.LOWEST_DISCERNIBLE_VALUE, //
            HistogramConstants.HIGHEST_TRACKABLE_VALUE, //
            HistogramConstants.NUMBER_OF_SIGNIFICANT_VALUE_DIGITS);
    }

    /**
     * @param httpStatusFamilies if you want to exclude 1xx or 5xx, add 100 or 500
     */
    public GlobalSummaryListener addExcludeHttpStatusFamily(int... httpStatusFamilies) {
        if (httpStatusFamilies == null) {
            return this;
        }
        for (int status : httpStatusFamilies) {
            this.excludeHttpStatusFamily.add(status / 100);
        }
        return this;
    }

    @Override
    public void onResourceNode(Resource.Info info) {
        switch (info.getStatus() / 100) {
            case 1:
                responses1xx.increment();
                break;
            case 2:
                responses2xx.increment();
                break;
            case 3:
                responses3xx.increment();
                break;
            case 4:
                responses4xx.increment();
                break;
            case 5:
                responses5xx.increment();
                break;
            default:
                break;
        }

        if (this.excludeHttpStatusFamily.contains(info.getStatus() / 100)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(format("exclude http status: {}", info.getStatus()));
            }
            return;
        }
        try {
            long latencyTime = info.getLatencyTime() - info.getRequestTime();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(format("latencyTime: {} resource: {}, status: {}", //
                    TimeUnit.MILLISECONDS.convert(latencyTime, TimeUnit.NANOSECONDS), //
                    info.getResource().getPath(), //
                    info.getStatus()));
            }
            latencyHistogram.recordValue(latencyTime);
        } catch (ArrayIndexOutOfBoundsException e) {
            LOGGER.warn(format("fail to record latency value: {}", info.getLatencyTime()));
        }
        try {
            long responseTime = info.getResponseTime() - info.getRequestTime();
            LOGGER.debug(format("responseTime: {} resource: {}, status: {}", //
                TimeUnit.MILLISECONDS.convert(responseTime, TimeUnit.NANOSECONDS), //
                info.getResource().getPath(), //
                info.getStatus()));
            responseHistogram.recordValue(responseTime);
        } catch (ArrayIndexOutOfBoundsException e) {
            LOGGER.warn(format("fail to record response time value: {}", info.getLatencyTime()));
        }
    }


    public Recorder getResponseTimeHistogram() {
        return responseHistogram;
    }

    public Recorder getLatencyTimeHistogram() {
        return latencyHistogram;
    }

    public LongAdder getResponses1xx() {
        return responses1xx;
    }

    public LongAdder getResponses2xx() {
        return responses2xx;
    }

    public LongAdder getResponses3xx() {
        return responses3xx;
    }

    public LongAdder getResponses4xx() {
        return responses4xx;
    }

    public LongAdder getResponses5xx() {
        return responses5xx;
    }

    public long getTotalResponse() {
        return responses1xx.longValue() //
            + responses2xx.longValue() //
            + responses3xx.longValue() //
            + responses4xx.longValue() //
            + responses5xx.longValue();
    }

    @Override
    public void onCommit(Request request) {
        requestCommitTotal.increment();
    }

    public long getRequestCommitTotal() {
        return requestCommitTotal.longValue();
    }
}
