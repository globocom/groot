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

package com.globocom.grou.groot.jetty.collector;

import com.globocom.grou.groot.jetty.generator.Http1ClientTransportBuilder;
import com.globocom.grou.groot.jetty.generator.LoadGenerator;
import com.globocom.grou.groot.jetty.generator.Resource;
import com.globocom.grou.groot.jetty.listeners.CollectorServer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static com.globocom.grou.groot.LogUtils.format;

@RunWith(Parameterized.class)
public class CollectorTest {

    private static final Log LOGGER = LogFactory.getLog(CollectorTest.class);

    private int serverNumbers;

    private List<Server> servers;

    public CollectorTest(Integer serverNumbers) throws Exception {
        this.serverNumbers = serverNumbers;
        this.servers = new ArrayList<>(this.serverNumbers);
        for (int i = 0; i < this.serverNumbers; i++) {
            this.servers.add(startServer(new LoadHandler()));
        }

    }

    @Parameterized.Parameters(name = "servers: {0}")
    public static Collection<Integer> data() {
        List<Integer> number = new ArrayList<>();
        number.add(1);
        number.add(2);

        return number;
    }

    @After
    public void shutdown() throws Exception {
        for (Server server : this.servers) {
            server.stop();
        }
    }

    @Test
    //@Ignore("ATM not a priority ATM")
    public void collect_informations() throws Exception {
        List<LoadGenerator> loadGenerators = new ArrayList<>(serverNumbers);
        List<CollectorClient> collectorClients = new CopyOnWriteArrayList<>();
        List<TestRequestListener> testRequestListeners = new ArrayList<>(serverNumbers);
        List<CollectorResultHandler> collectorResultHandlers = Collections
            .singletonList(new LoggerCollectorResultHandler());

        for (Server server : servers) {
            CollectorServer collectorServer = new CollectorServer(0).start();
            String host = "localhost";
            int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
            Resource resource = new Resource("http://" + host + ":" + port + "/index.html");

            TestRequestListener testRequestListener = new TestRequestListener();

            testRequestListeners.add(testRequestListener);

            LoadGenerator loadGenerator = new LoadGenerator.Builder() //
                .usersPerThread(2) //
                .iterationsPerThread(10) //
                .resourceRate(20) //
//                .transport( LoadGenerator.Transport.HTTP ) //
                .httpClientTransportBuilder(new Http1ClientTransportBuilder()) //
                //.scheduler( scheduler ) //
                .resource(resource) //
                // FIXME here
                //.responseTimeListeners( collectorServer ) //
                .requestListener(testRequestListener) //
                .build();

            loadGenerator.begin();

            loadGenerators.add(loadGenerator);

            CollectorClient collectorClient = new CollectorClient.Builder() //
                .addAddress("localhost:" + collectorServer.getPort()) //
                .scheduleDelayInMillis(500) //
                .collectorResultHandlers(collectorResultHandlers) //
                .build();

            collectorClient.start();

            collectorClients.add(collectorClient);

        }

        Thread.sleep(3000);

        for (CollectorClient collectorClient : collectorClients) {
            collectorClient.stop();
        }

        for (LoadGenerator loadGenerator : loadGenerators) {
            loadGenerator.interrupt();
        }

        for (TestRequestListener testRequestListener : testRequestListeners) {
            Assert.assertTrue("successReponsesReceived :" + testRequestListener.success.get(), //
                testRequestListener.success.get() > 1);

            LOGGER.info(format("successReponsesReceived: {}", testRequestListener.success.get()));

            Assert.assertTrue("failedReponsesReceived: " + testRequestListener.failed.get(), //
                testRequestListener.failed.get() < 1);
        }

    }

    protected Server startServer(HttpServlet handler) throws Exception {
        QueuedThreadPool serverThreads = new QueuedThreadPool();
        serverThreads.setName("server");
        Server server = new Server(serverThreads);
//        server.setSessionIdManager( new HashSessionIdManager() );
        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(new HttpConfiguration()));
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.setHandlers(new Handler[]{context});

        server.setHandler(handlerCollection);
        context.addServlet(new ServletHolder(handler), "/*");
        server.start();

        return server;
    }


    private static class LoadHandler extends HttpServlet {

        private static final Log LOGGER = LogFactory.getLog(LoadHandler.class);

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

            String method = request.getMethod().toUpperCase(Locale.ENGLISH);

            HttpSession httpSession = request.getSession();

            int contentLength = request.getIntHeader("X-Download");

            LOGGER.debug(format("method: {}, contentLength: {}, id: {}, pathInfo: {}", //
                method, contentLength, httpSession.getId(), request.getPathInfo()));


        }
    }

    static class TestRequestListener extends Request.Listener.Adapter {

        AtomicLong committed = new AtomicLong(0);

        AtomicLong success = new AtomicLong(0);

        AtomicLong failed = new AtomicLong(0);

        @Override
        public void onCommit(Request request) {
            committed.incrementAndGet();
        }

        @Override
        public void onSuccess(Request request) {
            success.incrementAndGet();
        }

        @Override
        public void onFailure(Request request, Throwable failure) {
            failed.incrementAndGet();
        }
    }

}
