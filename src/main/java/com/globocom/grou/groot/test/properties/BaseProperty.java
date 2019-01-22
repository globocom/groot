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

package com.globocom.grou.groot.test.properties;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;

@JsonInclude(NON_NULL)
public class BaseProperty implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log LOGGER = LogFactory.getLog(BaseProperty.class);

    private static final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.INDENT_OUTPUT, true);

    /**
     * URI request
     */
    private String uri;

    /**
     * number of simultaneous connections
     */
    private Integer numConn = 10;

    /**
     * Enable saving and using cookies
     */
    private Boolean saveCookies = false;

    /**
     * Authentication properties. Contains credentials & preemptive properties
     */
    private AuthProperty auth;

    /**
     * SSL properties.
     */
    private SslProperty ssl;

    /**
     * Body request
     */
    private String body;

    /**
     * Headers request
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * Method request
     */
    private String method = "GET";

    /**
     * Loaders requisited
     */
    private Integer parallelLoaders = 1;

    /**
     * Connection timeout
     */
    private Integer connectTimeout = 2000;

    /**
     * Enable keepalive
     */
    private Boolean keepAlive = true;

    /**
     * Enable follow redirect
     */
    private Boolean followRedirect = false;

    /**
     * Insert delay between requests (in microseconds)
     */
    private Integer fixedDelay = 45;

    /**
     * Target list to monitoring
     */
    private Object monitTargets;

    /**
     * List of properties per test to enable multiple requests
     */
    private TreeSet<RequestProperty> requests;

    /**
     * Number of threads. If absent, uses Runtime.getRuntime().availableProcessors()
     */
    private Integer threads = Runtime.getRuntime().availableProcessors();

    /**
     * Initial Warm Up iterations (statistical ignored)
     */
    private Integer warmupIterations;

    /**
     * Number of iterations. It's ignored if durationTimeMillis is defined
     */
    private Integer iterations;

    /**
     * Number of connections per user. Default = 1
     */
    private Integer connsPerUser;

    /**
     * Number of concurrent users. If omitid, it's equal "numConn" DIV "connsPerUser"
     */
    private Integer users;

    /**
     * Number of resource trees requested per second, or zero for maximum request rate
     */
    private Integer resourceRate;

    /**
     * The rate ramp up period in seconds, or zero for no ramp up
     */
    private Integer rateRampUpPeriod;

    /**
     * Number od NIO selectors (IO channels)
     */
    private Integer numberOfNIOselectors;

    /**
     * [INTERNAL] Maximum requests queued
     */
    private Integer maxRequestsQueued;

    /**
     * Connection blocking?
     */
    private Boolean blocking = false;

    /**
     * Idle timeout (in ms)
     */
    private Integer idleTimeout = 10000;

    /**
     * Duration time (in ms)
     */
    private Integer durationTimeMillis = 30000;

    /**
     * Duration time (in secs)
     */
    private Integer durationTimeSec;

    /**
     * Force reconnect
     */
    private Boolean forceReconnect = true;

    /**
     *  Define group name
     */
    private String groupName;

    /**
     * Force HTTP2. Enable use of HTTP/2 without HTTP/1.1 Upgrade. (WIP)
     */
    private Boolean forceHttp2;

    public String getUri() {
        return uri;
    }

    public BaseProperty setUri(String uri) {
        this.uri = uri;
        return this;
    }

    public Integer getNumConn() {
        return numConn;
    }

    public BaseProperty setNumConn(Integer numConn) {
        this.numConn = numConn;
        return this;
    }

    public Boolean getSaveCookies() {
        return saveCookies;
    }

    public BaseProperty setSaveCookies(Boolean saveCookies) {
        this.saveCookies = saveCookies;
        return this;
    }

    public AuthProperty getAuth() {
        return auth;
    }

    public BaseProperty setAuth(AuthProperty auth) {
        this.auth = auth;
        return this;
    }

    public SslProperty getSsl() {
        return ssl;
    }

    public BaseProperty setSsl(SslProperty ssl) {
        this.ssl = ssl;
        return this;
    }

    public String getBody() {
        return body;
    }

    public BaseProperty setBody(String body) {
        this.body = body;
        return this;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public BaseProperty setHeaders(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public BaseProperty setMethod(String method) {
        this.method = method;
        return this;
    }

    public Integer getParallelLoaders() {
        return parallelLoaders;
    }

    public BaseProperty setParallelLoaders(Integer parallelLoaders) {
        this.parallelLoaders = parallelLoaders;
        return this;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public BaseProperty setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    public BaseProperty setKeepAlive(Boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    public Boolean getFollowRedirect() {
        return followRedirect;
    }

    public BaseProperty setFollowRedirect(Boolean followRedirect) {
        this.followRedirect = followRedirect;
        return this;
    }

    public Integer getFixedDelay() {
        return fixedDelay;
    }

    public BaseProperty setFixedDelay(Integer fixedDelay) {
        this.fixedDelay = fixedDelay;
        return this;
    }

    public Object getMonitTargets() {
        return monitTargets;
    }

    public BaseProperty setMonitTargets(Object monitTargets) {
        this.monitTargets = monitTargets;
        return this;
    }

    public TreeSet<RequestProperty> getRequests() {
        return requests;
    }

    public BaseProperty setRequests(TreeSet<RequestProperty> requests) {
        this.requests = requests;
        return this;
    }

    public Integer getThreads() {
        return threads;
    }

    public BaseProperty setThreads(Integer threads) {
        this.threads = threads;
        return this;
    }

    public Integer getWarmupIterations() {
        return warmupIterations;
    }

    public BaseProperty setWarmupIterations(Integer warmupIterations) {
        this.warmupIterations = warmupIterations;
        return this;
    }

    public Integer getIterations() {
        return iterations;
    }

    public BaseProperty setIterations(Integer iterations) {
        this.iterations = iterations;
        return this;
    }

    public Integer getUsers() {
        return users;
    }

    public BaseProperty setUsers(Integer users) {
        Assert.notNull(users, "User property is NULL");
        Assert.isTrue(users > 0,  "User property is equal or less than zero");
        this.users = users;
        return this;
    }

    public Integer getConnsPerUser() {
        return connsPerUser;
    }

    public BaseProperty setConnsPerUser(Integer connsPerUser) {
        this.connsPerUser = connsPerUser;
        return this;
    }

    public Integer getResourceRate() {
        return resourceRate;
    }

    public BaseProperty setResourceRate(Integer resourceRate) {
        this.resourceRate = resourceRate;
        return this;
    }

    public Integer getRateRampUpPeriod() {
        return rateRampUpPeriod;
    }

    public BaseProperty setRateRampUpPeriod(Integer rateRampUpPeriod) {
        this.rateRampUpPeriod = rateRampUpPeriod;
        return this;
    }

    public Integer getNumberOfNIOselectors() {
        return numberOfNIOselectors;
    }

    public BaseProperty setNumberOfNIOselectors(Integer numberOfNIOselectors) {
        this.numberOfNIOselectors = numberOfNIOselectors;
        return this;
    }

    public Integer getMaxRequestsQueued() {
        return maxRequestsQueued;
    }

    public BaseProperty setMaxRequestsQueued(Integer maxRequestsQueued) {
        this.maxRequestsQueued = maxRequestsQueued;
        return this;
    }

    public Boolean getBlocking() {
        return blocking;
    }

    public BaseProperty setBlocking(Boolean blocking) {
        this.blocking = blocking;
        return this;
    }

    public Integer getIdleTimeout() {
        return idleTimeout;
    }

    public BaseProperty setIdleTimeout(Integer idleTimeout) {
        this.idleTimeout = idleTimeout;
        return this;
    }

    @Deprecated
    public Integer getDurationTimeMillis() {
        return durationTimeMillis;
    }

    @Deprecated
    public BaseProperty setDurationTimeMillis(Integer durationTimeMillis) {
        this.durationTimeMillis = durationTimeMillis;
        return this;
    }

    public Integer getDurationTimeSec() {
        //noinspection deprecation
        return durationTimeSec != null ? durationTimeSec : getDurationTimeMillis() / 1000;
    }

    public BaseProperty setDurationTimeSec(Integer durationTimeSec) {
        this.durationTimeSec = durationTimeSec;
        return this;
    }

    public Boolean getForceReconnect() {
        return forceReconnect;
    }

    public BaseProperty setForceReconnect(Boolean forceReconnect) {
        this.forceReconnect = forceReconnect;
        return this;
    }

    public Boolean getForceHttp2() {
        return forceHttp2;
    }

    public BaseProperty setForceHttp2(Boolean forceHttp2) {
        this.forceHttp2 = forceHttp2;
        return this;
    }

    public String getGroupName() {
        return groupName;
    }

    public BaseProperty setGroupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    @Override
    @JsonIgnore
    public String toString() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
            return "{}";
        }
    }
}