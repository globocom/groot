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

package com.globocom.grou.groot.jetty.listeners;

import org.HdrHistogram.Histogram;

import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

public class CollectorInformations {

    private long totalCount;

    private long minValue;

    private long maxValue;

    private long value50;

    private long value90;

    private double mean;

    private double stdDeviation;

    private long startTimeStamp;

    private long endTimeStamp;

    public CollectorInformations() {
        // no op to help json mapper
    }


    /**
     * per default values will be in nanos
     */
    public CollectorInformations(Histogram histogram) {
        this(histogram, TimeUnit.NANOSECONDS, TimeUnit.NANOSECONDS);
    }

    /**
     * @param source the {@link TimeUnit} of the source values
     * @param target the {@link TimeUnit} the stored values
     */
    public CollectorInformations(Histogram histogram, TimeUnit source, TimeUnit target) {
        this.totalCount = histogram.getTotalCount();
        this.minValue = target.convert(histogram.getMinValue(), source);
        this.maxValue = target.convert(histogram.getMaxValue(), source);
        this.mean = target.convert(Math.round(histogram.getMean()), source);
        this.value50 = target.convert(histogram.getValueAtPercentile(50D), source);
        this.value90 = target.convert(histogram.getValueAtPercentile(90D), source);
        this.startTimeStamp = histogram.getStartTimeStamp();
        this.endTimeStamp = histogram.getEndTimeStamp();
        this.stdDeviation = target.convert(Math.round(histogram.getStdDeviation()), source);
    }

    public long getEndTimeStamp() {
        return endTimeStamp;
    }

    public void setEndTimeStamp(long endTimeStamp) {
        this.endTimeStamp = endTimeStamp;
    }

    public CollectorInformations endTimeStamp(long endTimeStamp) {
        this.endTimeStamp = endTimeStamp;
        return this;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public CollectorInformations totalCount(long totalCount) {
        this.totalCount = totalCount;
        return this;
    }

    public long getMinValue() {
        return minValue;
    }

    public void setMinValue(long minValue) {
        this.minValue = minValue;
    }

    public CollectorInformations minValue(long minValue) {
        this.minValue = minValue;
        return this;
    }

    public long getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(long maxValue) {
        this.maxValue = maxValue;
    }

    public CollectorInformations maxValue(long maxValue) {
        this.maxValue = maxValue;
        return this;
    }

    public double getMean() {
        return mean;
    }

    public void setMean(double mean) {
        this.mean = mean;
    }

    public CollectorInformations mean(double mean) {
        this.mean = mean;
        return this;
    }

    public double getStdDeviation() {
        return stdDeviation;
    }

    public void setStdDeviation(double stdDeviation) {
        this.stdDeviation = stdDeviation;
    }

    public CollectorInformations stdDeviation(double stdDeviation) {
        this.stdDeviation = stdDeviation;
        return this;
    }

    public long getStartTimeStamp() {
        return startTimeStamp;
    }

    public void setStartTimeStamp(long startTimeStamp) {
        this.startTimeStamp = startTimeStamp;
    }

    public CollectorInformations startTimeStamp(long startTimeStamp) {
        this.startTimeStamp = startTimeStamp;
        return this;
    }

    public long getValue50() {
        return value50;
    }

    public void setValue50(long value50) {
        this.value50 = value50;
    }

    public CollectorInformations value50(long value50) {
        this.value50 = value50;
        return this;
    }

    public long getValue90() {
        return value90;
    }

    public void setValue90(long value90) {
        this.value90 = value90;
    }

    public CollectorInformations value90(long value90) {
        this.value90 = value90;
        return this;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean ls) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss z");
        return "CollectorInformations millis:" + (ls ? System.lineSeparator() : "") //
            + "totalCount=" + totalCount //
            + (ls ? System.lineSeparator() : "") //
            + ", minValue=" + TimeUnit.NANOSECONDS.toMillis(getMinValue()) //
            + (ls ? System.lineSeparator() : "") //
            + ", maxValue=" + TimeUnit.NANOSECONDS.toMillis(getMaxValue()) + (ls ? System.lineSeparator() : "") //
            + ", mean=" + TimeUnit.NANOSECONDS.toMillis(Math.round(getMean())) //
            + (ls ? System.lineSeparator() : "") //
            + ", stdDeviation=" + TimeUnit.NANOSECONDS.toMillis(Math.round(getStdDeviation())) //
            + (ls ? System.lineSeparator() : "") //
            + ", value 50%=" + TimeUnit.NANOSECONDS.toMillis(Math.round(getValue50())) //
            + (ls ? System.lineSeparator() : "") //
            + ", value 90%=" + TimeUnit.NANOSECONDS.toMillis(Math.round(getValue90())) //
            + (ls ? System.lineSeparator() : "") //
            + ", startTimeStamp=" + simpleDateFormat.format(startTimeStamp) + (ls ? System.lineSeparator() : "") //
            + ", endTimeStamp=" + simpleDateFormat.format(endTimeStamp);
    }


    public String toStringInNanos(boolean ls) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss z");
        return "CollectorInformations nanos:" + (ls ? System.lineSeparator() : "") //
            + "totalCount=" + totalCount //
            + (ls ? System.lineSeparator() : "") //
            + ", minValue=" + getMinValue()  //
            + (ls ? System.lineSeparator() : "") //
            + ", maxValue=" + getMaxValue() + (ls ? System.lineSeparator() : "") //
            + ", mean=" + Math.round(getMean())  //
            + (ls ? System.lineSeparator() : "") //
            + ", stdDeviation=" + Math.round(getStdDeviation()) //
            + (ls ? System.lineSeparator() : "") //
            + ", value 50%=" + Math.round(getValue50())  //
            + (ls ? System.lineSeparator() : "") //
            + ", value 90%=" + Math.round(getValue90())  //
            + (ls ? System.lineSeparator() : "") //
            + ", startTimeStamp=" + simpleDateFormat.format(startTimeStamp) + (ls ? System.lineSeparator() : "") //
            + ", endTimeStamp=" + simpleDateFormat.format(endTimeStamp);
    }
}
