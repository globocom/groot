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

package com.globocom.grou.groot.loader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globocom.grou.groot.SystemEnv;
import com.globocom.grou.groot.common.Proto;
import com.globocom.grou.groot.handler.Http1ClientInitializer;
import com.globocom.grou.groot.handler.Http2ClientInitializer;
import com.globocom.grou.groot.test.CookieService;
import com.globocom.grou.groot.test.Loader;
import com.globocom.grou.groot.test.Loader.Status;
import com.globocom.grou.groot.test.ReportService;
import com.globocom.grou.groot.test.Test;
import com.globocom.grou.groot.test.properties.AuthProperty;
import com.globocom.grou.groot.test.properties.BaseProperty;
import com.globocom.grou.groot.monit.MonitorService;
import com.globocom.grou.groot.monit.SystemInfo;
import com.globocom.grou.groot.test.properties.RequestProperty;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.sql.Date;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.globocom.grou.groot.SystemEnv.GROUP_NAME;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;

@Service
public class LoaderService {

    private static final String GROU_LOADER_REDIS_KEY = "grou:loader:" + GROUP_NAME.getValue() + ":" + SystemInfo.hostname();

    private static final Log LOGGER = LogFactory.getLog(LoaderService.class);

    private static final boolean IS_MAC = isMac();
    private static final boolean IS_LINUX = isLinux();

    private final MonitorService monitorService;
    private final StringRedisTemplate template;
    private final Loader myself;
    private final String buildVersion;
    private final String buildTimestamp;

    private final AtomicBoolean abortNow = new AtomicBoolean(false);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    private final ReportService reportService;
    private final CookieService cookieService;

    private long schedPeriod = 50L;

    @Autowired
    public LoaderService(final MonitorService monitorService,
                         final ReportService reportService,
                         final CookieService cookieService,
                         StringRedisTemplate template,
                         @Value("${build.version}") String buildVersion,
                         @Value("${build.timestamp}") String buildTimestamp) {
        this.monitorService = monitorService;
        this.reportService = reportService;
        this.cookieService = cookieService;
        this.template = template;
        this.buildVersion = buildVersion;
        this.buildTimestamp = buildTimestamp;
        this.myself = new Loader();
        myself.setName(SystemInfo.hostname());
        myself.setStatus(Status.IDLE);
        myself.setGroupName(GROUP_NAME.getValue());
        myself.setVersion(buildVersion + " (" + buildTimestamp + ")");
    }

    public Loader start(final Test test) throws Exception {
        final String testName = test.getName();
        final String projectName = test.getProject();
        String projectDotTest = projectName + "." + testName;
        myself.setStatusDetailed(projectDotTest);
        myself.setLastExecAt(Date.from(Instant.now()));
        final BaseProperty property = test.getProperties();
        updateStatus(Status.RUNNING);

        LOGGER.info("Starting test " + myself.getStatusDetailed());

        final Loader myselfClone;

        try {

            final TreeSet<RequestProperty> requestsProperties = requestsProperty(property);
            property.setRequests(requestsProperties);
            property.setUri(null);
            property.setMethod(null);
            property.setHeaders(null);

            LOGGER.info(property);

            AtomicReference<String> scheme = new AtomicReference<>(null);
            final FullHttpRequest[] requests = convertPropertyToHttpRequest(requestsProperties, scheme);
            if (scheme.get() == null) {
                LOGGER.error("Scheme not initialized");
                return null;
            }

            int numConn = property.getNumConn() / property.getParallelLoaders();
            int maxTestDuration = Integer.parseInt(SystemEnv.MAX_TEST_DURATION.getValue());
            @SuppressWarnings("deprecation")
            int durationSec = Math.min(maxTestDuration, Optional.ofNullable(property.getDurationTimeSec())
                .orElse(property.getDurationTimeMillis() / 1000));
            int threads = property.getThreads();

            LOGGER.info("Using " + threads + " thread(s)");

            cookieService.saveCookies(property.getSaveCookies());
            final EventLoopGroup group = getEventLoopGroup(threads);
            final Proto proto = Proto.valueOf(scheme.get().toUpperCase());
            final Bootstrap bootstrap = newBootstrap(group, property.getConnectTimeout());
            Channel[] channels = new Channel[numConn];
            double lastPerformanceRate = reportService.lastPerformanceRate();
            schedPeriod = Math.min(100, Math.max(10L, (long) (schedPeriod * lastPerformanceRate / 1.05)));

            LOGGER.info("Sched Period: " + schedPeriod + " us");

            activeChannels(numConn, proto, bootstrap, channels, requests, schedPeriod);

            final AtomicLong start = new AtomicLong(System.currentTimeMillis());
            executor.schedule(() -> {
                long now = System.currentTimeMillis();

                closeChannels(channels, 10, TimeUnit.SECONDS);
                group.shutdownGracefully(1L, 10L, TimeUnit.SECONDS);

                reportService.showReport(start.get() - (System.currentTimeMillis() - now));
                reportService.reset();
                cookieService.reset();
            }, durationSec, TimeUnit.SECONDS);

            boolean forceReconnect = property.getForceReconnect();
            reconnectIfNecessary(forceReconnect, numConn, proto, group, bootstrap, channels, requests, schedPeriod);

        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(e.getMessage(), e);
            }
        } finally {
            myselfClone = cloneMySelf();
            stop(projectDotTest);
        }

        return myselfClone;
    }

    private String convertSchemeIfNecessary(String scheme) {
        return scheme.replace("h2c", "https").replace("h2", "http");
    }

    private FullHttpRequest[] convertPropertyToHttpRequest(final TreeSet<RequestProperty> requestsProperties, final AtomicReference<String> scheme) {
        final FullHttpRequest[] requests = new FullHttpRequest[requestsProperties.size()];
        int requestId = 0;
        for (RequestProperty requestProperty: requestsProperties) {
            final URI uri = URI.create(requestProperty.getUri());
            if (scheme.get() == null) {
                scheme.set(uri.getScheme());
            }
            final HttpHeaders headers = new DefaultHttpHeaders()
                .add(HOST, uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""))
                .add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), convertSchemeIfNecessary(uri.getScheme()));
            Optional.ofNullable(requestProperty.getHeaders()).orElse(Collections.emptyMap()).forEach(headers::add);
            AuthProperty authProperty = Optional.ofNullable(requestProperty.getAuth()).orElse(new AuthProperty());
            final String credentials = authProperty.getCredentials();
            if (credentials != null && !credentials.isEmpty()) {
                headers.add(HttpHeaderNames.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(Charset.defaultCharset())));
            }

            HttpMethod method = HttpMethod.valueOf(requestProperty.getMethod());
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            final String bodyStr = requestProperty.getBody();
            ByteBuf body = bodyStr != null && !bodyStr.isEmpty() ?
                Unpooled.copiedBuffer(bodyStr.getBytes(Charset.defaultCharset())) : Unpooled.buffer(0);

            requests[requestId] = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path, body, headers, new DefaultHttpHeaders());
            requestId++;
        }
        return requests;
    }

    private TreeSet<RequestProperty> requestsProperty(BaseProperty properties) {
        RequestProperty singleRequestProperties = new RequestProperty();
        String uriStr = properties.getUri();
        boolean singleRequest;
        if (singleRequest = (uriStr != null && !uriStr.isEmpty())) {
            singleRequestProperties.setOrder(0);
            singleRequestProperties.setUri(uriStr);
            singleRequestProperties.setMethod(properties.getMethod());
            singleRequestProperties.setBody(properties.getBody());
            singleRequestProperties.setAuth(properties.getAuth());
            singleRequestProperties.setHeaders(properties.getHeaders());
            singleRequestProperties.setSaveCookies(properties.getSaveCookies());
        }
        return singleRequest ? new TreeSet<RequestProperty>(){{add(singleRequestProperties);}} : properties.getRequests();
    }

    private void reconnectIfNecessary(boolean reconnect, int numConn, final Proto proto, final EventLoopGroup group, Bootstrap bootstrap, Channel[] channels, final FullHttpRequest[] requests, long schedPeriod) {
        while (reconnect && !group.isShutdown() && !group.isShuttingDown()) {
            activeChannels(numConn, proto, bootstrap, channels, requests, schedPeriod);
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                // ignored
            }
        }
    }

    private Bootstrap newBootstrap(EventLoopGroup group, int connectTimeout) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.
            group(group).
            channel(getSocketChannelClass()).
            option(ChannelOption.SO_KEEPALIVE, true).
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout).
            option(ChannelOption.TCP_NODELAY, true).
            option(ChannelOption.SO_REUSEADDR, true);
        return bootstrap;
    }

    private void closeChannels(Channel[] channels, int timeout, TimeUnit unit) {
        CountDownLatch latch = new CountDownLatch(channels.length - 1);
        for (Channel channel : channels) {
            try {
                if (channel != null && channel.isActive()) {
                    channel.close();
                }
            } finally {
                latch.countDown();
            }
        }
        try {
            latch.await(timeout, unit);
        } catch (InterruptedException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
    }

    private synchronized void activeChannels(int numConn, final Proto proto, final Bootstrap bootstrap, final Channel[] channels, final FullHttpRequest[] requests, long schedPeriod) {
        for (int chanId = 0; chanId < numConn; chanId++) {
            if (channels[chanId] == null || !channels[chanId].isActive()) {

                Channel channel = newChannel(bootstrap, proto, requests, schedPeriod);
                if (channel != null) {
                    channels[chanId] = channel;
                }
            }
        }
    }

    private Channel newChannel(final Bootstrap bootstrap, Proto proto, final FullHttpRequest[] requests, long schedPeriod) {
        try {
            if (!bootstrap.config().group().isShuttingDown() && !bootstrap.config().group().isShutdown()) {
                URI uri = URI.create(proto.name().toLowerCase() + "://" + requests[0].headers().get(HttpHeaderNames.HOST) + requests[0].uri());
                final Channel channel = bootstrap
                    .clone()
                    .handler(initializer(proto))
                    .connect(uri.getHost(), uri.getPort())
                    .sync()
                    .channel();
                channel.eventLoop().scheduleAtFixedRate(() -> {
                    if (channel.isActive()) {
                        for (FullHttpRequest request : requests) {
                            reportService.writeCounterIncr();
                            cookieService.applyCookies(request.headers());
                            channel.writeAndFlush(request.copy());
                        }
                    }
                }, schedPeriod, schedPeriod, TimeUnit.MICROSECONDS);
                return channel;
            }
        } catch (Exception e) {
            reportService.failedIncr(e);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
        return null;
    }

    private SslContext sslContext(boolean ssl) {
        if (ssl) {
            try {
                final SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
                return SslContextBuilder.forClient()
                    .sslProvider(provider)
                    /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                     * Please refer to the HTTP/2 specification for cipher requirements. */
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                        Protocol.ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                        SelectorFailureBehavior.NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                        SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                    .build();
            } catch (SSLException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return null;
    }

    private ChannelInitializer initializer(Proto proto) {
        if (proto == Proto.H2 || proto == Proto.H2C) {
            return new Http2ClientInitializer(sslContext(proto.isSsl()), Integer.MAX_VALUE, reportService, cookieService);
        }
        return new Http1ClientInitializer(sslContext(proto.isSsl()), reportService, cookieService);
    }

    private EventLoopGroup getEventLoopGroup(int numCores) {
        // @formatter:off
        return IS_MAC   ? new KQueueEventLoopGroup(numCores) :
               IS_LINUX ? new EpollEventLoopGroup(numCores) :
                          new NioEventLoopGroup(numCores);
        // @formatter:on
    }

    private Class<? extends Channel> getSocketChannelClass() {
        // @formatter:off
        return IS_MAC   ? KQueueSocketChannel.class :
               IS_LINUX ? EpollSocketChannel.class :
                          NioSocketChannel.class;
        // @formatter:on
    }

    private static String getOS() {
        return System.getProperty("os.name", "UNDEF").toLowerCase();
    }

    private static boolean isMac() {
        boolean result = getOS().startsWith("mac");
        if (result) {
            LOGGER.warn("Hello. I'm Mac");
        }
        return result;
    }

    private static boolean isLinux() {
        boolean result = getOS().startsWith("linux");
        if (result) {
            LOGGER.warn("Hello. I'm Linux");
        }
        return result;
    }

    private Loader cloneMySelf() {
        Loader loader = new Loader();
        loader.setName(myself.getName());
        loader.setStatus(myself.getStatus());
        loader.setStatusDetailed(myself.getStatusDetailed());
        loader.setVersion(myself.getVersion());
        loader.setLastExecAt(myself.getLastExecAt());
        return loader;
    }

    private void stop(String projectDotTest) {
        monitorService.reset();
        updateStatus(Status.IDLE);
        abortNow.set(false);
        LOGGER.info("Finished test " + projectDotTest);
        myself.setStatusDetailed("");
    }

    private void updateStatus(Status loaderStatus) {
        myself.setStatus(loaderStatus);
        updateStatusKey();
    }

    private void updateStatusKey() {
        String loaderJson = newUndefLoaderStr();
        try {
            loaderJson = mapper.writeValueAsString(myself);
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
        }
        template.opsForValue().set(GROU_LOADER_REDIS_KEY, loaderJson, 15000, TimeUnit.MILLISECONDS);
    }

    private String newUndefLoaderStr() {
        Loader undefLoader = new Loader();
        undefLoader.setStatus(Status.ERROR);
        undefLoader.setStatusDetailed(JsonProcessingException.class.getName());
        undefLoader.setVersion(buildVersion + "." + buildTimestamp);
        try {
            return mapper.writeValueAsString(undefLoader);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @Scheduled(fixedRate = 10000)
    public void register() {
        updateStatusKey();
        checkAbortNow();
    }

    private void checkAbortNow() {
        String currentTest = myself.getStatusDetailed();
        String abortKey = "ABORT:" + currentTest + "#" + SystemInfo.hostname();
        String redisAbortKey = template.opsForValue().get(abortKey);
        if (redisAbortKey != null) {
            abortNow.set(true);
            myself.setStatusDetailed(Test.Status.ABORTED.toString());
            myself.setStatus(Status.ERROR);
            template.expire(abortKey, 10, TimeUnit.MILLISECONDS);
            LOGGER.warn("TEST ABORTED: " + currentTest);
        }
    }

    @PreDestroy
    public void shutdown() {
        template.delete(GROU_LOADER_REDIS_KEY);
    }

}
