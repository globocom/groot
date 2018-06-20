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

import com.globocom.grou.groot.jetty.generator.LoadGenerator;

import java.io.Serializable;

/**
 *
 */
public interface ValueListener {

    /**
     * triggered when building {@link LoadGenerator} instance
     *
     * @since 0.3
     */
    default void onLoadGeneratorStart(LoadGenerator loadGenerator) {
        //no op
    }

    /**
     * triggered before starting a {@link LoadGenerator} run
     *
     * @since 0.3
     */
    default void beforeRun(LoadGenerator loadGenerator) {
        // no op
    }

    /**
     * triggered after finishing a {@link LoadGenerator} run
     *
     * @since 0.3
     */
    default void afterRun(LoadGenerator loadGenerator) {
        // no op
    }

    /**
     * can be called
     *
     * @param loadGenerator can be <code>null</code>
     * @since 0.3
     */
    default void reset(LoadGenerator loadGenerator) {
        // no op
    }

    /**
     * triggered when the load generator is stopped
     */
    void onLoadGeneratorStop();


    class Values
        implements Serializable {

        /**
         * the timestamp in millis seconds
         */
        private long eventTimestamp;

        private String path;

        /**
         * the value in nano seconds
         */
        private long time;

        private String method;

        private long size;

        private int status;

        public Values() {
            // no op
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Values path(String path) {
            this.path = path;
            return this;
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }

        public Values time(long time) {
            this.time = time;
            return this;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public Values method(String method) {
            this.method = method;
            return this;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public Values size(long size) {
            this.size = size;
            return this;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public Values status(int status) {
            this.status = status;
            return this;
        }

        public long getEventTimestamp() {
            return eventTimestamp;
        }

        public void setEventTimestamp(long eventTimestamp) {
            this.eventTimestamp = eventTimestamp;
        }

        public Values eventTimestamp(long eventTimestamp) {
            this.eventTimestamp = eventTimestamp;
            return this;
        }

        @Override
        public String toString() {
            return "Values{" + "eventTimestamp=" + eventTimestamp + ", path='" + path + '\'' + ", time=" + time
                + ", method='" + method + '\'' + ", size=" + size + ", status=" + status + '}';
        }
    }

}
