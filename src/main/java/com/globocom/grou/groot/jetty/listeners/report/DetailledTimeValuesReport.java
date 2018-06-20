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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Contains all response time values!
 */
public class DetailledTimeValuesReport {

    private List<Entry> entries = new CopyOnWriteArrayList<>();

    public DetailledTimeValuesReport() {
        // no op
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    public void addEntry(Entry entry) {
        this.entries.add(entry);
    }

    public static class Entry {

        // in  millis
        private long timeStamp;

        private String path;

        private int httpStatus;

        // in nano s
        private long time;

        public Entry(long timeStamp, String path, int httpStatus, long time) {
            this.timeStamp = timeStamp;
            this.path = path;
            this.httpStatus = httpStatus;
            this.time = time;
        }

        public String getPath() {
            return path;
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        public long getTime() {
            return time;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public void setHttpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        public void setTime(long time) {
            this.time = time;
        }

        public long getTimeStamp() {
            return timeStamp;
        }

        public void setTimeStamp(long timeStamp) {
            this.timeStamp = timeStamp;
        }
    }

}
