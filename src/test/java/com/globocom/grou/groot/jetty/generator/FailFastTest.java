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

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.StatisticsServlet;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.junit.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class FailFastTest
{

    private static final Logger LOGGER = Log.getLogger( FailFastTest.class );
    protected Resource resource;
    protected Server server;
    protected ServerConnector connector;
    TestHandler testHandler;


    @Before
    public void startJetty()
        throws Exception
    {
        StatisticsHandler statisticsHandler = new StatisticsHandler();
        server = new Server( new ExecutorThreadPool( 5120) );
        connector = new ServerConnector( server, new HttpConnectionFactory( new HttpConfiguration() ) );
        server.addConnector( connector );
        server.setHandler( statisticsHandler );
        ServletContextHandler statsContext = new ServletContextHandler( statisticsHandler, "/" );
        statsContext.addServlet( new ServletHolder( new StatisticsServlet() ), "/stats" );
        testHandler = new TestHandler();
        testHandler.server = server;
        statsContext.addServlet( new ServletHolder( testHandler ), "/" );
        statsContext.setSessionHandler( new SessionHandler() );
        server.start();
    }

    @After
    public void stopJetty()
        throws Exception
    {
        if ( server.isRunning() )
        {
            server.stop();
        }
    }

    @Ignore
    @Test
    public void should_fail_fast_on_server_stop()
        throws Exception
    {
        AtomicInteger onFailure = new AtomicInteger( 0 ), onCommit = new AtomicInteger( 0 );
        int localPort = connector.getLocalPort();
        LoadGenerator.Builder builder = //
            new LoadGenerator.Builder() //
                .host( "localhost" ) //
                .port(localPort) //
                .resource( new Resource("http://localhost:" + localPort + "/index.html?fail=5" )) //
                .warmupIterationsPerThread( 1 ) //
                .usersPerThread( 1 ) //
                .threads( 1 ) //
                .resourceRate( 5 )
                .iterationsPerThread( 25 ) //
                //.runFor( 10, TimeUnit.SECONDS ) //
                .requestListener( new Request.Listener.Adapter() {
                    @Override
                    public void onFailure( Request request, Throwable failure )
                    {
                        LOGGER.info( "fail: {}", onFailure.incrementAndGet() );
                    }

                    @Override
                    public void onCommit( Request request )
                    {
                        LOGGER.info( "onCommit: {}", onCommit.incrementAndGet() );
                    }
                } );
        boolean exception = false;
        try
        {
            builder.build().begin().get();
        }
        catch ( Exception e )
        {
            exception = true;
        }
        Assert.assertTrue( exception );
        LOGGER.info( "onFailure: {}, onCommit: {}", onFailure, onCommit);
        int onFailureCall = onFailure.get();
        // the value is really dependant on machine...
        Assert.assertTrue("onFailureCall is " + onFailureCall, onFailureCall < 10);
    }

    static class TestHandler
        extends HttpServlet
    {

        AtomicInteger getNumber = new AtomicInteger( 0 );
        Server server;

        @Override
        protected void service( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException
        {
            if ( getNumber.addAndGet( 1 ) >
                Integer.parseInt( request.getParameter( "fail" ) ) )
            {
                try
                {
                    server.stop();
                }
                catch ( Exception e )
                {
                    throw new RuntimeException( e.getMessage(), e );
                }
            }
            response.getOutputStream().write( "Jetty rocks!!".getBytes() );
            response.flushBuffer();
        }
    }

}
