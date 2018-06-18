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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class LoadGeneratorTest {

    private static final Log LOGGER = LogFactory.getLog(LoadGeneratorTest.class);

    @Parameterized.Parameters(name = "{0}")
    public static Object[] parameters() {
        return TransportType.values();
    }

    private final ConnectionFactory connectionFactory;
    private final HTTPClientTransportBuilder clientTransportBuilder;
    private Server server;
    private ServerConnector connector;

    public LoadGeneratorTest(TransportType transportType) {
        switch (transportType) {
            case H1C:
                connectionFactory = new HttpConnectionFactory();
                clientTransportBuilder = new HTTP1ClientTransportBuilder();
                break;
            case H2C:
                connectionFactory = new HTTP2CServerConnectionFactory(new HttpConfiguration());
                clientTransportBuilder = new HTTP2ClientTransportBuilder();
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void prepare(Handler handler) throws Exception {
        server = new Server();
        connector = new ServerConnector(server, connectionFactory);
        server.addConnector(connector);
        server.setHandler(handler);
        server.start();
    }

    @After
    public void dispose() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testDefaultConfiguration() throws Exception {
        prepare(new TestHandler());

        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testMultipleThreads() throws Exception {
        prepare(new TestHandler());

        Set<String> threads = Collections.newSetFromMap(new ConcurrentHashMap<>());
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .threads(2)
                .iterationsPerThread(1)
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        threads.add(Thread.currentThread().getName());
                    }
                })
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);
        Assert.assertEquals(2, threads.size());
    }

    @Test
    public void testInterrupt() throws Exception {
        prepare(new TestHandler());

        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                // Iterate forever.
                .iterationsPerThread(0)
                .resourceRate(5)
                .build();
        CompletableFuture<Void> cf = loadGenerator.begin();

        Thread.sleep(1000);

        loadGenerator.interrupt();

        cf.handle((r, x) -> {
            Throwable cause;
            if (x == null || (cause = x.getCause()) == null) return null;
            if (cause instanceof InterruptedException) {
                return null;
            } else {
                throw new CompletionException(cause);
            }
        }).get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testRunFor() throws Exception {
        prepare(new TestHandler());

        long time = 2;
        TimeUnit unit = TimeUnit.SECONDS;
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .runFor(time, unit)
                .resourceRate(5)
                .build();
        loadGenerator.begin().get(2 * time, unit);
    }

    @Test
    public void testResourceTree() throws Exception {
        prepare(new TestHandler());

        Queue<String> resources = new ConcurrentLinkedDeque<>();
        List<Resource.Info> infos = new ArrayList<>();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .resource(new Resource("http://localhost/",
                            new Resource("http://localhost/1",
                                new Resource("http://localhost/11"))))
                .resourceListener((Resource.NodeListener)info -> {
                    resources.offer(info.getResource().getPath());
                    infos.add(info);
                })
                .resourceListener((Resource.TreeListener)info -> resources.offer(info.getResource().getPath()))
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals("/,/1,/11,/", resources.stream().collect(Collectors.joining(",")));
        Assert.assertTrue(infos.stream().allMatch(info -> info.getStatus() == 200));
    }

    @Test
    public void testResourceGroup() throws Exception {
        prepare(new TestHandler());

        Queue<String> resources = new ConcurrentLinkedDeque<>();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .resource(new Resource(new Resource("http://localhost/1")))
                .resourceListener((Resource.NodeListener)info -> resources.offer(info.getResource().getPath()))
                .resourceListener((Resource.TreeListener)info -> {
                    if (info.getResource().getPath() == null) {
                        if (resources.size() == 1) {
                            resources.offer("<group>");
                        }
                    }
                })
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals("/1,<group>", resources.stream().collect(Collectors.joining(",")));
    }

    @Test
    public void testWarmupDoesNotNotifyResourceListeners() throws Exception {
        prepare(new TestHandler());

        AtomicLong requests = new AtomicLong();
        AtomicLong resources = new AtomicLong();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .warmupIterationsPerThread(2)
                .iterationsPerThread(3)
                .resourceRate(5)
                .resource(new Resource("http://localhost/").method("POST"))
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        requests.incrementAndGet();
                    }
                })
                .resourceListener((Resource.NodeListener)info -> resources.incrementAndGet())
                .build();

        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(5, requests.get());
        Assert.assertEquals(3, resources.get());
    }

    @Test
    public void testTwoRuns() throws Exception {
        prepare(new TestHandler());

        AtomicLong requests = new AtomicLong();
        AtomicLong resources = new AtomicLong();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .iterationsPerThread(3)
                .resourceRate(5)
                .resource(new Resource("http://localhost/"))
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        requests.incrementAndGet();
                    }
                })
                .resourceListener((Resource.NodeListener)info -> resources.incrementAndGet())
                .build();

        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(3, requests.get());
        Assert.assertEquals(3, resources.get());

        requests.set(0);
        resources.set(0);
        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(3, requests.get());
        Assert.assertEquals(3, resources.get());
    }

    @Ignore
    @Test
    public void testJMX() throws Exception {
        prepare(new TestHandler());

        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                // Iterate forever.
                .iterationsPerThread(0)
                .resourceRate(5)
                .build();

        MBeanContainer mbeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        loadGenerator.addBean(mbeanContainer);

        ObjectName pattern = new ObjectName(LoadGenerator.class.getPackage().getName() + ":*");
        Set<ObjectName> objectNames = mbeanContainer.getMBeanServer().queryNames(pattern, null);
        Assert.assertTrue(objectNames.size() > 0);
        Optional<ObjectName> objectNameOpt = objectNames.stream()
                .filter(o -> o.getKeyProperty("type").equalsIgnoreCase(LoadGenerator.class.getSimpleName()))
                .findAny();
        Assert.assertTrue(objectNameOpt.isPresent());
        ObjectName objectName = objectNameOpt.get();

        CompletableFuture<Void> cf = loadGenerator.begin();

        Thread.sleep(1000);

        mbeanContainer.getMBeanServer().invoke(objectName, "interrupt", null, null);

        cf.handle((r, x) -> {
            Throwable cause = x.getCause();
            if (cause instanceof InterruptedException) {
                return null;
            } else {
                throw new CompletionException(cause);
            }
        }).get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testRateIsRespected() throws Exception {
        // Use a large resource rate to test that
        // sleep compensation works correctly.
        int rate = 2000;
        if (connectionFactory instanceof HTTP2CServerConnectionFactory) {
            ((HTTP2CServerConnectionFactory)connectionFactory).setMaxConcurrentStreams(rate);
        }
        prepare(new TestHandler());

        int iterations = 5 * rate;
        AtomicLong requests = new AtomicLong();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .iterationsPerThread(iterations)
                .resourceRate(rate)
                .socketAddressResolver(new SocketAddressResolver.Sync())
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        requests.incrementAndGet();
                    }
                })
                .build();

        long start = System.nanoTime();
        loadGenerator.begin().get(10 * rate, TimeUnit.MILLISECONDS);
        long elapsed = System.nanoTime() - start;
        long expected = TimeUnit.SECONDS.toNanos(iterations / rate);

        // prevent until 10% of tolerance
        Assert.assertTrue((Math.abs(elapsed - expected) * 0.90) < expected / 10);
        Assert.assertEquals(iterations, requests.intValue());
    }

    @Test
    public void testRateRampUp() throws Exception {
        prepare(new TestHandler());

        int rate = 10;
        long ramp = 5;
        AtomicLong requests = new AtomicLong();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .resourceRate(rate)
                .rateRampUpPeriod(ramp)
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        requests.incrementAndGet();
                    }
                })
                .runFor(ramp, TimeUnit.SECONDS)
                .build();

        loadGenerator.begin().get();

        // The number of unsent requests during ramp up is
        // half of the requests that would have been sent.
        long expected = rate * ramp / 2;
        Assert.assertTrue(expected - 1 <= requests.get());
        Assert.assertTrue(requests.get() <= expected + 1);
    }

    private enum TransportType {
        H1C, H2C
    }
}
