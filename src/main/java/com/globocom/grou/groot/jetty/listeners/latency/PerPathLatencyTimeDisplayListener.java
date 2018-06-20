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

package com.globocom.grou.groot.jetty.listeners.latency;

import com.globocom.grou.groot.jetty.generator.LoadGenerator;
import com.globocom.grou.groot.jetty.generator.Resource;
import com.globocom.grou.groot.jetty.listeners.CollectorInformations;
import com.globocom.grou.groot.jetty.listeners.HistogramConstants;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.globocom.grou.groot.LogUtils.format;

/**
 *
 */
public class PerPathLatencyTimeDisplayListener
    implements Resource.NodeListener, LoadGenerator.BeginListener, LoadGenerator.EndListener {

    private static final Log LOGGER = LogFactory.getLog(PerPathLatencyTimeDisplayListener.class);

    private volatile Map<String, Recorder> recorderPerPath;

    private ScheduledExecutorService scheduledExecutorService;

    private ValueListenerRunnable runnable;

    private final long lowestDiscernibleValue;
    private final long highestTrackableValue;
    private final int numberOfSignificantValueDigits;

    private List<ValueListener> valueListeners = new ArrayList<>();

    public PerPathLatencyTimeDisplayListener(long initial, long delay, TimeUnit timeUnit) {
        this(HistogramConstants.LOWEST_DISCERNIBLE_VALUE, //
            HistogramConstants.HIGHEST_TRACKABLE_VALUE,  //
            HistogramConstants.NUMBER_OF_SIGNIFICANT_VALUE_DIGITS,  //
            initial, //
            delay,  //
            timeUnit, //
            Collections.singletonList(new PrintValueListener()));
    }

    public PerPathLatencyTimeDisplayListener(long lowestDiscernibleValue, long highestTrackableValue,
        int numberOfSignificantValueDigits, long initial, long delay,
        TimeUnit timeUnit, List<ValueListener> valueListeners) {
        this.valueListeners.addAll(valueListeners);
        this.recorderPerPath = new ConcurrentHashMap<>();
        this.runnable = new ValueListenerRunnable(recorderPerPath, this.valueListeners);
        // FIXME configurable or using a shared one
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleWithFixedDelay(runnable, initial, delay, timeUnit);
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
    }

    @Override
    public void onBegin(LoadGenerator loadGenerator) {
        // we initialize Maps to avoid concurrent issues
        recorderPerPath = new ConcurrentHashMap<>();
        initializeMap(recorderPerPath, loadGenerator.getConfig().getResource().getResources());
    }

    private void initializeMap(Map<String, Recorder> recorderMap, List<Resource> resources) {
        for (Resource resource : resources) {
            Recorder recorder = recorderMap.get(resource.getPath());
            if (recorder == null) {
                recorder = new Recorder(lowestDiscernibleValue, //
                    highestTrackableValue, //
                    numberOfSignificantValueDigits);
                recorderMap.put(resource.getPath(), recorder);
            }
            initializeMap(recorderMap, resource.getResources());
        }
    }

    public PerPathLatencyTimeDisplayListener() {
        this(0, 5, TimeUnit.SECONDS);
    }

    private static class ValueListenerRunnable
        implements Runnable {

        private final Map<String, Recorder> recorderPerPath;

        private final List<ValueListener> valueListeners;

        private ValueListenerRunnable(Map<String, Recorder> recorderPerPath, List<ValueListener> valueListeners) {
            this.recorderPerPath = recorderPerPath;
            this.valueListeners = valueListeners;
        }

        @Override
        public void run() {
            for (Map.Entry<String, Recorder> entry : recorderPerPath.entrySet()) {
                String path = entry.getKey();
                Histogram histogram = entry.getValue().getIntervalHistogram();
                for (ValueListener valueListener : valueListeners) {
                    valueListener.onValue(path, histogram);
                }
            }
        }
    }

    @Override
    public void onResourceNode(Resource.Info info) {
        String path = info.getResource().getPath();
        long time = info.getLatencyTime() - info.getRequestTime();
        Recorder recorder = recorderPerPath.get(path);
        if (recorder == null) {
            recorder = new Recorder(lowestDiscernibleValue, //
                highestTrackableValue, //
                numberOfSignificantValueDigits);
            recorderPerPath.put(path, recorder);
        }
        try {
            recorder.recordValue(time);
        } catch (ArrayIndexOutOfBoundsException e) {
            LOGGER.warn(format("skip error recording time {}, {}", time, e.getMessage()));
        }
    }

    @Override
    public void onEnd(LoadGenerator generator) {
        scheduledExecutorService.shutdownNow();
        // last run
        runnable.run();
    }

    public interface ValueListener {

        void onValue(String path, Histogram histogram);
    }

    public static class PrintValueListener
        implements ValueListener {

        @Override
        public void onValue(String path, Histogram histogram) {
            StringBuilder message = new StringBuilder("Path:").append(path).append(System.lineSeparator());
            message.append(new CollectorInformations(histogram) //
                .toString(true)) //
                .append(System.lineSeparator());
            LOGGER.info(message.toString());
        }
    }
}
