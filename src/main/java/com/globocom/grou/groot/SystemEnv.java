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
     * Redis hostname.
     */
    REDIS_HOSTNAME ("REDIS_HOSTNAME", "127.0.0.1"),

    /**
     * Redis port.
     */
    REDIS_PORT ("REDIS_PORT", 6379),

    /**
     * Redis password.
     */
    REDIS_PASSWORD ("REDIS_PASSWORD", ""),

    /**
     * Redis database.
     */
    REDIS_DATABASE ("REDIS_DATABASE", ""),

    /**
     * Redis use sentinel.
     */
    REDIS_USE_SENTINEL ("REDIS_USE_SENTINEL", Boolean.FALSE),

    /**
     * Redis sentinel master name.
     */
    REDIS_SENTINEL_MASTER_NAME ("REDIS_SENTINEL_MASTER_NAME", "mymaster"),

    /**
     * Redis sentinel nodes.
     */
    REDIS_SENTINEL_NODES ("REDIS_SENTINEL_NODES", "127.0.0.1:26379"),

    /**
     * Redis max idle.
     */
    REDIS_MAXIDLE  ("REDIS_MAXIDLE", "100"),

    /**
     * Redis timeout.
     */
    REDIS_TIMEOUT  ("REDIS_TIMEOUT", "60000"),

    /**
     * Redis max total.
     */
    REDIS_MAXTOTAL ("REDIS_MAXTOTAL", "128"),

    /**
     * Max test duration (ms)
     */
    MAX_TEST_DURATION ("MAX_TEST_DURATION", 600000),

    /**
     * Statsd loader prefix key
     */
    STATSD_LOADER_KEY ("STATSD_LOADER_KEY", "loader"),

    /**
     * Statsd target prefix key
     */
    STATSD_TARGET_KEY ("STATSD_TARGET_KEY", "target"),

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
    STATSD_PORT ("STATSD_PORT", 8125),

    /**
     * Prefix tag field (Useful to Statsite OpenTSDB sink)
     */
    PREFIX_TAG ("PREFIX_TAG", "_t_");

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
