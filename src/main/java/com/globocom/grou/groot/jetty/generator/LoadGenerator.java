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

package com.globocom.grou.groot.jetty.generator;

import static com.globocom.grou.groot.LogUtils.format;

import com.globocom.grou.groot.jetty.generator.common.Resource;
import com.globocom.grou.groot.jetty.generator.common.Resource.Info;
import com.globocom.grou.groot.jetty.generator.common.Config;
import com.globocom.grou.groot.jetty.generator.builders.Http1ClientTransportBuilder;
import com.globocom.grou.groot.jetty.generator.builders.HttpClientTransportBuilder;
import com.globocom.grou.groot.jetty.generator.common.Listener;
import com.globocom.grou.groot.jetty.generator.common.Listener.BeginListener;
import com.globocom.grou.groot.jetty.generator.common.Listener.EndListener;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.client.HttpAuthenticationStore;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.CountingCallback;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;

import java.net.CookieStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class LoadGenerator {

    private static final Log LOGGER = LogFactory.getLog(LoadGenerator.class);

    private final Config config;
    private final CyclicBarrier barrier;
    private final AuthenticationStore authenticationStore;
    private final AtomicReference<CookieStore> cookieStore;
    private ExecutorService executorService;
    private volatile boolean interrupt;

    private LoadGenerator(Config config) {
        this.config = config;
        this.barrier = new CyclicBarrier(config.getThreads());
        this.authenticationStore = new HttpAuthenticationStore();
        this.cookieStore = new AtomicReference<>(null);
    }

    private CompletableFuture<Void> proceed() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            executorService = Executors.newCachedThreadPool();
            interrupt = false;
            fireBeginEvent(this);
            result.complete(null);
        } catch (Throwable x) {
            result.completeExceptionally(x);
        }
        return result;
    }

    private CompletableFuture<Void> halt() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            fireEndEvent(this);
            interrupt();
            executorService.shutdown();
            result.complete(null);
        } catch (Throwable x) {
            result.completeExceptionally(x);
        }
        return result;
    }

    public Config getConfig() {
        return config;
    }

    public CompletableFuture<Void> begin() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(format("generating load, {}", config));
        }
        return proceed().thenCompose(x -> {
            CompletableFuture[] futures = new CompletableFuture[config.getThreads()];
            for (int i = 0; i < futures.length; ++i) {
                futures[i] = CompletableFuture.supplyAsync(this::process, executorService)
                    .thenCompose(Function.identity());
            }
            return CompletableFuture.allOf(futures);
        }).thenComposeAsync(x -> halt(), executorService);
    }

    public void interrupt() {
        interrupt = true;
    }

    private CompletableFuture<Void> process() {
        CompletableFuture<Void> process = new CompletableFuture<>();
        CompletableFuture<Void> result = process;
        try {
            barrier.await();

            String threadName = Thread.currentThread().getName();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(format("sender thread {} running", threadName));
            }

            HttpClient[] clients = new HttpClient[config.getUsersPerThread()];
            // HttpClient cannot be stopped from one of its own threads.
            result = process.whenCompleteAsync((r, x) -> {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("stopping http clients");
                }
                Arrays.stream(clients).forEach(this::stopHttpClient);
            }, executorService);
            for (int i = 0; i < clients.length; ++i) {
                HttpClient client = clients[i] = newHttpClient(getConfig());
                client.start();
            }

            Callback processCallback = new Callback() {
                @Override
                public void succeeded() {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(format("sender thread {} completed", threadName));
                    }
                    process.complete(null);
                }

                @Override
                public void failed(Throwable x) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(format("sender thread {} failed", threadName), x);
                    }
                    process.completeExceptionally(x);
                }
            };

            // This callback only forwards failure, success is notified explicitly.
            Callback callback = new Callback.Nested(processCallback) {
                @Override
                public void succeeded() {
                }
            };

            int rate = config.getResourceRate();
            long period = rate > 0 ? TimeUnit.SECONDS.toNanos(config.getThreads()) / rate : 0;
            long rateRampUpPeriod = TimeUnit.SECONDS.toNanos(config.getRateRampUpPeriod());

            long runFor = config.getRunFor();
            int warmupIterations = config.getWarmupIterationsPerThread();
            int iterations = runFor > 0 ? 0 : config.getIterationsPerThread();

            long begin = System.nanoTime();
            long total = 0;
            long unsent = 0;
            int clientIndex = 0;

            send:
            while (true) {
                long batch = 1;
                if (period > 0) {
                    TimeUnit.NANOSECONDS.sleep(period);
                    // We need to compensate for oversleeping.
                    long elapsed = System.nanoTime() - begin;
                    long expected = Math.round((double) elapsed / period);
                    if (rateRampUpPeriod > 0 && elapsed < rateRampUpPeriod) {
                        long send = Math.round(0.5D * elapsed * elapsed / rateRampUpPeriod / period);
                        unsent = expected - send;
                        expected = send;
                    } else {
                        expected -= unsent;
                    }
                    batch = expected - total;
                    total = expected;
                }

                while (batch > 0) {
                    HttpClient client = clients[clientIndex];

                    boolean warmup = false;
                    boolean lastIteration = false;
                    if (warmupIterations > 0) {
                        warmup = --warmupIterations >= 0;
                    } else if (iterations > 0) {
                        lastIteration = --iterations == 0;
                    }
                    // Sends the resource one more time after the time expired,
                    // but guarantees that the callback is notified correctly.
                    boolean ranEnough =
                        runFor > 0 && TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - begin) >= runFor;
                    Callback c = lastIteration || ranEnough ? processCallback : callback;

                    sendResourceTree(client, config.getResource(), warmup, c);
                    --batch;

                    if (interrupt || lastIteration || ranEnough || process.isCompletedExceptionally()) {
                        break send;
                    }

                    if (++clientIndex == clients.length) {
                        clientIndex = 0;
                    }
                }
            }
        } catch (Throwable x) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(x);
            }
            process.completeExceptionally(x);
        }
        return result;
    }

    private HttpClient newHttpClient(Config config) {
        HttpClient httpClient = new HttpClient(config.getHttpClientTransportBuilder().build(),
            config.getSslContextFactory());
        httpClient.setExecutor(config.getExecutor());
        httpClient.setScheduler(config.getScheduler());
        httpClient.setMaxConnectionsPerDestination(config.getChannelsPerUser());
        httpClient.setMaxRequestsQueuedPerDestination(config.getMaxRequestsQueued());
        httpClient.setSocketAddressResolver(config.getSocketAddressResolver());
        httpClient.setConnectBlocking(config.isConnectBlocking());
        httpClient.setConnectTimeout(config.getConnectTimeout());
        httpClient.setIdleTimeout(config.getIdleTimeout());
        httpClient.setUserAgentField(new HttpField(HttpHeader.USER_AGENT, config.getUserAgent()));
        if (config.isSaveCookies()) {
            cookieStore.compareAndSet(null, new HttpCookieStore());
            httpClient.setCookieStore(cookieStore.get());
        } else {
            httpClient.setCookieStore(new HttpCookieStore.Empty());
        }
        return httpClient;
    }

    @SuppressWarnings("checkstyle:EmptyCatchBlock")
    private void stopHttpClient(HttpClient client) {
        try {
            if (client != null) {
                client.stop();
            }
        } catch (Throwable ignore) {
        }
    }

    private Request newRequest(HttpClient client, Config config, final Resource resource) {
        HttpFields requestHeaders = resource.getRequestHeaders();
        String contentType = requestHeaders.get(HttpHeader.CONTENT_TYPE.asString());
        requestHeaders.remove(HttpHeader.CONTENT_TYPE.asString());
        String method = resource.getMethod();
        Request request = client.newRequest(resource.getUri()).method(method);
        request.getHeaders().addAll(requestHeaders);
        final String username = config.getUsername();
        if (username != null && !username.isEmpty() && client.getAuthenticationStore() != authenticationStore) {
            synchronized (this) {
                if (client.getAuthenticationStore() != authenticationStore) {
                    client.setAuthenticationStore(authenticationStore);
                    if (config.isAuthPreemptive()) {
                        authenticationStore.addAuthenticationResult(
                            new BasicAuthentication.BasicResult(request.getURI(), config.getPassword(),
                                config.getPassword()));
                    } else {
                        authenticationStore.addAuthentication(
                            new BasicAuthentication(request.getURI(), "Realm", config.getUsername(),
                                config.getPassword()));
                    }
                }
            }
        }
        if (resource.hasBody()) {
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }
            final ContentProvider contentProvider = new BytesContentProvider(contentType, resource.content());
            request.content(contentProvider, contentType);
            long requestLength = contentProvider.getLength();
            if (requestLength > 0) {
                request.header(HttpHeader.CONTENT_LENGTH.asString(), String.valueOf(requestLength));
            }
        }

        return request;
    }

    private void sendResourceTree(HttpClient client, Resource resource, boolean warmup, Callback callback) {
        int nodes = resource.descendantCount();
        Resource.Info info = resource.newInfo();
        CountingCallback treeCallback = new CountingCallback(new Callback() {
            @Override
            public void succeeded() {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(format("completed tree for {}", resource));
                }
                info.setTreeTime(System.nanoTime());
                if (!warmup) {
                    fireResourceTreeEvent(info);
                }
                callback.succeeded();
            }

            @Override
            public void failed(Throwable x) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(format("failed tree for {}", resource));
                }
                callback.failed(x);
            }
        }, nodes);
        Sender sender = new Sender(this, client, warmup, treeCallback);
        sender.offer(Collections.singletonList(info));
        sender.send();
    }

    private void fireBeginEvent(LoadGenerator generator) {
        config.getListeners().stream()
            .filter(l -> l instanceof BeginListener)
            .map(l -> (BeginListener) l)
            .forEach(l -> l.onBegin(generator));
    }

    private void fireEndEvent(LoadGenerator generator) {
        config.getListeners().stream()
            .filter(l -> l instanceof EndListener)
            .map(l -> (EndListener) l)
            .forEach(l -> l.onEnd(generator));
    }

    private void fireResourceNodeEvent(Resource.Info info) {
        config.getResourceListeners().stream()
            .filter(l -> l instanceof Resource.NodeListener)
            .map(l -> (Resource.NodeListener) l)
            .forEach(l -> l.onResourceNode(info));
    }

    private void fireOnContent(int remaining) {
        config.getResourceListeners().stream()
            .filter(l -> l instanceof Resource.OnContentListener)
            .map(l -> (Resource.OnContentListener) l)
            .forEach(l -> l.onContent(remaining));
    }

    private void fireResourceTreeEvent(Resource.Info info) {
        config.getResourceListeners().stream()
            .filter(l -> l instanceof Resource.TreeListener)
            .map(l -> (Resource.TreeListener) l)
            .forEach(l -> l.onResourceTree(info));
    }

    public static class Builder extends Config {

        /**
         * @param threads the number of sender threads
         * @return this Builder
         */
        public Builder threads(int threads) {
            if (threads < 1) {
                throw new IllegalArgumentException();
            }
            this.threads = threads;
            return this;
        }

        /**
         * @param warmupIterationsPerThread the number of warmup iterations that each sender thread performs
         * @return this Builder
         */
        public Builder warmupIterationsPerThread(int warmupIterationsPerThread) {
            this.warmupIterationsPerThread = warmupIterationsPerThread;
            return this;
        }

        /**
         * @param iterationsPerThread the number of iterations that each sender thread performs, or zero to run forever
         * @return this Builder
         */
        public Builder iterationsPerThread(int iterationsPerThread) {
            this.iterationsPerThread = iterationsPerThread;
            return this;
        }

        /**
         * <p>Configures the amount of time that the load generator should run.</p>
         * <p>This setting always takes precedence over {@link #iterationsPerThread}.</p>
         *
         * @param time the time the load generator runs
         * @param unit the unit of time
         * @return this Builder
         */
        public Builder runFor(long time, TimeUnit unit) {
            if (time > 0) {
                this.runFor = unit.toSeconds(time);
            }
            return this;
        }

        /**
         * @param usersPerThread the number of users/browsers for each sender thread
         * @return this Builder
         */
        public Builder usersPerThread(int usersPerThread) {
            if (usersPerThread < 0) {
                throw new IllegalArgumentException();
            }
            this.usersPerThread = usersPerThread;
            return this;
        }

        /**
         * @param channelsPerUser the number of connections/streams per user
         * @return this Builder
         */
        public Builder channelsPerUser(int channelsPerUser) {
            if (channelsPerUser < 0) {
                throw new IllegalArgumentException();
            }
            this.channelsPerUser = channelsPerUser;
            return this;
        }

        /**
         * @param resourceRate number of resource trees requested per second, or zero for maximum request rate
         * @return this Builder
         */
        public Builder resourceRate(int resourceRate) {
            this.resourceRate = resourceRate;
            return this;
        }

        /**
         * @param rateRampUpPeriod the rate ramp up period in seconds, or zero for no ramp up
         * @return this Builder
         */
        public Builder rateRampUpPeriod(long rateRampUpPeriod) {
            this.rateRampUpPeriod = rateRampUpPeriod;
            return this;
        }

        /**
         * @param username the credential username
         * @return this Builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * @param password the credential password
         * @return this Builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder saveCookies(boolean saveCookies) {
            this.saveCookies = saveCookies;
            return this;
        }

        public Builder authPreemptive(boolean authPreemptive) {
            this.authPreemptive = authPreemptive;
            return this;
        }

        public Builder userAgent(String userAgent) {
            if (userAgent != null && !userAgent.isEmpty()) this.userAgent = userAgent;
            return this;
        }

        /**
         * @param httpClientTransportBuilder the HttpClient transport builder
         * @return this Builder
         */
        public Builder httpClientTransportBuilder(HttpClientTransportBuilder httpClientTransportBuilder) {
            this.httpClientTransportBuilder = Objects.requireNonNull(httpClientTransportBuilder);
            return this;
        }

        /**
         * @param sslContextFactory the SslContextFactory to use for https requests
         * @return this Builder
         */
        public Builder sslContextFactory(SslContextFactory sslContextFactory) {
            this.sslContextFactory = sslContextFactory;
            return this;
        }

        /**
         * @param scheduler the shared scheduler
         * @return this Builder
         */
        public Builder scheduler(Scheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler);
            return this;
        }

        /**
         * @param executor the shared executor between all HttpClient instances if {@code null} each HttpClient will use
         * its own
         * @return this Builder
         */
        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor);
            return this;
        }

        /**
         * @param socketAddressResolver the shared SocketAddressResolver
         * @return this Builder
         */
        public Builder socketAddressResolver(SocketAddressResolver socketAddressResolver) {
            this.socketAddressResolver = Objects.requireNonNull(socketAddressResolver);
            return this;
        }

        /**
         * @param resource the root Resource
         * @return this Builder
         */
        public Builder resource(Resource resource) {
            this.resource = resource;
            return this;
        }

        public Builder maxRequestsQueued(int maxRequestsQueued) {
            this.maxRequestsQueued = maxRequestsQueued;
            return this;
        }

        public Builder listener(Listener listener) {
            listeners.add(listener);
            return this;
        }

        public Builder requestListener(Request.Listener listener) {
            requestListeners.add(listener);
            return this;
        }

        public Builder resourceListener(Resource.Listener listener) {
            resourceListeners.add(listener);
            return this;
        }

        public Builder connectBlocking(boolean connectBlocking) {
            this.connectBlocking = connectBlocking;
            return this;
        }

        public Builder connectTimeout(long connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder idleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public LoadGenerator build() {
            if (httpClientTransportBuilder == null) {
                httpClientTransportBuilder = new Http1ClientTransportBuilder();
            }
            return new LoadGenerator(this);
        }
    }

    public static class Sender {

        private static final Log LOGGER = LogFactory.getLog(Sender.class);

        private LoadGenerator loadGenerator;
        private final Queue<Info> queue = new ArrayDeque<>();
        private final Set<URI> pushCache = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final HttpClient client;
        private final boolean warmup;
        private final CountingCallback callback;
        private boolean active;

        public Sender(LoadGenerator loadGenerator, HttpClient client, boolean warmup, CountingCallback callback) {
            this.loadGenerator = loadGenerator;
            this.client = client;
            this.warmup = warmup;
            this.callback = callback;
        }

        void offer(List<Info> resources) {
            synchronized (this) {
                queue.addAll(resources);
            }
        }

        public void send() {
            synchronized (this) {
                if (active) {
                    return;
                }
                active = true;
            }

            List<Info> resources = new ArrayList<>();
            while (true) {
                synchronized (this) {
                    if (queue.isEmpty()) {
                        active = false;
                        return;
                    }
                    resources.addAll(queue);
                    queue.clear();
                }

                send(resources);
                resources.clear();
            }
        }

        private void send(List<Info> resources) {
            for (Info info : resources) {
                Resource resource = info.getResource();
                info.setRequestTime(System.nanoTime());
                if (resource.getPath() != null) {
                    HttpRequest httpRequest = (HttpRequest) loadGenerator
                        .newRequest(client, loadGenerator.getConfig(), resource);

                    if (pushCache.contains(httpRequest.getURI())) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug(format("skip sending pushed {}", resource));
                        }
                    } else {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug(format("sending {}{}", warmup ? "warmup " : "", resource));
                        }

                        httpRequest.pushListener((request, pushed) -> {
                            @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
                            URI pushedURI = pushed.getURI();
                            Resource child = resource.findDescendant(pushedURI);
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug(format("pushed {}", child));
                            }
                            if (child != null && pushCache.add(pushedURI)) {
                                Info pushedInfo = child.newInfo();
                                pushedInfo.setRequestTime(System.nanoTime());
                                pushedInfo.setPushed(true);
                                return new ResponseHandler(pushedInfo);
                            } else {
                                return null;
                            }
                        });

                        Request request = loadGenerator.getConfig().getRequestListeners().stream()
                            .reduce(httpRequest, Request::listener, (r1, r2) -> r1);
                        request.send(new ResponseHandler(info));
                    }
                } else {
                    info.setResponseTime(System.nanoTime());
                    // Don't fire the resource event for "group" resources.
                    callback.succeeded();
                    sendChildren(resource);
                }
            }
        }

        private void sendChildren(Resource resource) {
            List<Resource> children = resource.getResources();
            if (!children.isEmpty()) {
                offer(children.stream().sorted(Comparator.comparingInt(Resource::getOrder)).map(Resource::newInfo)
                    .collect(Collectors.toList()));
                send();
            }
        }

        private class ResponseHandler extends Response.Listener.Adapter {

            private final Info info;

            private ResponseHandler(Info info) {
                this.info = info;
            }

            @Override
            public void onBegin(Response response) {
                // Record time to first byte.
                info.setLatencyTime(System.nanoTime());
                info.setVersion(response.getVersion());
            }

            @Override
            public void onContent(Response response, ByteBuffer buffer) {
                // Record content length.
                int remaining = buffer.remaining();
                info.addContent(remaining);
                if (!warmup) {
                    loadGenerator.fireOnContent(remaining);
                }
            }

            @Override
            public void onComplete(Result result) {
                Resource resource = info.getResource();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(format("completed {}: {}", resource, result));
                }
                if (result.isSucceeded()) {
                    info.setResponseTime(System.nanoTime());
                    info.setStatus(result.getResponse().getStatus());
                    if (!warmup) {
                        loadGenerator.fireResourceNodeEvent(info);
                    }
                    callback.succeeded();
                } else {
                    callback.failed(result.getFailure());
                }
                sendChildren(resource);
            }
        }
    }
}
