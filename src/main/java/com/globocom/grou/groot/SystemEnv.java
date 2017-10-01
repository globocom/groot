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

package com.globocom.grou.groot;

import java.util.Optional;

/**
 * The enum System environments.
 */
@SuppressWarnings("unused")
public enum SystemEnv {

    /**
     * JMS timeout
     */
    JMS_TIMEOUT ("JMS_TIMEOUT", 10000L),

    /**
     * Broker connection URIs
     */
    BROKER_CONN ("BROKER_CONN", "tcp://localhost:61616?blockOnDurableSend=false&consumerWindowSize=0&protocols=Core"),

    /**
     * Broker user
     */
    BROKER_USER ("BROKER_USER", "guest"),

    /**
     * Broker password
     */
    BROKER_PASS ("BROKER_PASS", "guest"),

    /**
     * Broker HA enable
     */
    BROKER_HA ("BROKER_HA", Boolean.FALSE),

    /**
     * Max test duration (ms)
     */
    MAX_TEST_DURATION ("MAX_TEST_DURATION", 600000),

    /**
     * Statsd loader prefix key
     */
    STATSD_LOADER_KEY ("STATSD_LOADER_KEY", "loaders"),

    /**
     * Statsd target prefix key
     */
    STATSD_TARGET_KEY ("STATSD_TARGET_KEY", "targets"),

    /**
     * Statsd response prefix key
     */
    STATSD_RESPONSE_KEY ("STATSD_RESPONSE_KEY", "response"),

    /**
     * Statsd prefix
     */
    STATSD_PREFIX ("STATSD_PREFIX", "grou"),

    /**
     * Statsd host
     */
    STATSD_HOST ("STATSD_HOST", "localhost"),

    /**
     * Statsd port
     */
    STATSD_PORT ("STATSD_PORT", 8125);

    /**
     * Gets SystemEnv value.
     *
     * @return the value
     */
    public String getValue() {
        return value;
    }

    private final String value;

    SystemEnv(String env, Object def) {
        this.value = Optional.ofNullable(System.getenv(env)).orElse(String.valueOf(def));
    }
}
