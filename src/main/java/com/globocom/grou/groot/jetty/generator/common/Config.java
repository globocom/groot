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

package com.globocom.grou.groot.jetty.generator.common;

import com.globocom.grou.groot.jetty.generator.builders.HttpClientTransportBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;

/**
 * Read-only configuration for the load generator.
 */
@ManagedObject("LoadGenerator Configuration")
public class Config {

    protected int threads = 1;
    protected int warmupIterationsPerThread = 0;
    protected int iterationsPerThread = 1;
    protected long runFor = 0;
    protected int usersPerThread = 1;
    protected int channelsPerUser = 1024;
    protected int resourceRate = 1;
    protected long rateRampUpPeriod = 0;
    protected HttpClientTransportBuilder httpClientTransportBuilder;
    protected SslContextFactory sslContextFactory;
    protected Scheduler scheduler;
    protected Executor executor;
    protected SocketAddressResolver socketAddressResolver = new SocketAddressResolver.Sync();
    protected Resource resource = new Resource("http://localhost/");
    protected final List<Listener> listeners = new ArrayList<>();
    protected final List<Request.Listener> requestListeners = new ArrayList<>();
    protected final List<Resource.Listener> resourceListeners = new ArrayList<>();
    protected int maxRequestsQueued = 128 * 1024;
    protected boolean connectBlocking = true;
    protected long connectTimeout = 5000;
    protected long idleTimeout = 15000;
    protected String username = null;
    protected String password = null;
    protected boolean saveCookies = false;
    protected boolean authPreemptive = false;
    protected String userAgent = "UNDEF";

    @ManagedAttribute("Number of sender threads")
    public int getThreads() {
        return threads;
    }

    @ManagedAttribute("Number of warmup iterations per sender thread")
    public int getWarmupIterationsPerThread() {
        return warmupIterationsPerThread;
    }

    @ManagedAttribute("Number of iterations per sender thread")
    public int getIterationsPerThread() {
        return iterationsPerThread;
    }

    @ManagedAttribute("Time in seconds for how long to run")
    public long getRunFor() {
        return runFor;
    }

    @ManagedAttribute("Number of users per sender thread")
    public int getUsersPerThread() {
        return usersPerThread;
    }

    @ManagedAttribute("Number of concurrent request channels per user")
    public int getChannelsPerUser() {
        return channelsPerUser;
    }

    @ManagedAttribute("Send rate in resource trees per second")
    public int getResourceRate() {
        return resourceRate;
    }

    @ManagedAttribute("Rate ramp up period in seconds")
    public long getRateRampUpPeriod() {
        return rateRampUpPeriod;
    }

    @ManagedAttribute("Credential username")
    public String getUsername() {
        return username;
    }

    @ManagedAttribute("Credential password")
    public String getPassword() {
        return password;
    }

    @ManagedAttribute("Save Cookies")
    public boolean isSaveCookies() {
        return saveCookies;
    }

    @ManagedAttribute("Auth Preemptive")
    public boolean isAuthPreemptive() {
        return authPreemptive;
    }

    @ManagedAttribute("User Agent")
    public String getUserAgent() {
        return userAgent;
    }

    public HttpClientTransportBuilder getHttpClientTransportBuilder() {
        return httpClientTransportBuilder;
    }

    public SslContextFactory getSslContextFactory() {
        return sslContextFactory;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public Executor getExecutor() {
        return executor;
    }

    public SocketAddressResolver getSocketAddressResolver() {
        return socketAddressResolver;
    }

    public Resource getResource() {
        return resource;
    }

    @ManagedAttribute("Maximum number of queued requests")
    public int getMaxRequestsQueued() {
        return maxRequestsQueued;
    }

    public List<Listener> getListeners() {
        return listeners;
    }

    public List<Request.Listener> getRequestListeners() {
        return requestListeners;
    }

    public List<Resource.Listener> getResourceListeners() {
        return resourceListeners;
    }

    @ManagedAttribute("Whether the connect operation is blocking")
    public boolean isConnectBlocking() {
        return connectBlocking;
    }

    @ManagedAttribute("Connect timeout in milliseconds")
    public long getConnectTimeout() {
        return connectTimeout;
    }

    @ManagedAttribute("Idle timeout in milliseconds")
    public long getIdleTimeout() {
        return idleTimeout;
    }

    @Override
    public String toString() {
        return String.format("%s[t=%d,i=%d,u=%d,c=%d,r=%d,rf=%ds]",
            Config.class.getSimpleName(),
            threads,
            runFor > 0 ? -1 : iterationsPerThread,
            usersPerThread,
            channelsPerUser,
            resourceRate,
            runFor > 0 ? runFor : -1);
    }
}
