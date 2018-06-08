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
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This listener will record time between start of send and onCommit event
 * Then display the number of request per second
 */
public class QpsListenerDisplay
    extends Request.Listener.Adapter
    implements Request.Listener, LoadGenerator.EndListener
{

    private static final Logger LOGGER = Log.getLogger( QpsListenerDisplay.class );

    private ScheduledExecutorService scheduledExecutorService;

    private volatile Recorder recorder;

    private List<ValueListener> valueListeners = new ArrayList<>();

    public QpsListenerDisplay( long initial, long delay, TimeUnit timeUnit )
    {
        this( HistogramConstants.LOWEST_DISCERNIBLE_VALUE, //
              HistogramConstants.HIGHEST_TRACKABLE_VALUE,  //
              HistogramConstants.NUMBER_OF_SIGNIFICANT_VALUE_DIGITS,  //
              initial, //
              delay,  //
              timeUnit );
    }

    public QpsListenerDisplay( long lowestDiscernibleValue, long highestTrackableValue,
                               int numberOfSignificantValueDigits, long initial, long delay, TimeUnit timeUnit )
    {
        this.recorder = new Recorder( lowestDiscernibleValue, //
                                      highestTrackableValue, //
                                      numberOfSignificantValueDigits );
        String hostname = "";
        try
        {
            hostname = InetAddress.getLocalHost().getHostName();
        }
        catch ( Exception e )
        {
            LOGGER.info( "ignore cannot get hostname:" + e.getMessage() );
        }
        scheduledExecutorService = Executors.newScheduledThreadPool( 1 );
        this.valueListeners.add( new PrintValueListener( hostname ) );
        scheduledExecutorService.scheduleWithFixedDelay( new ValueDisplayRunnable( recorder, valueListeners ), //
                                                         initial, delay, timeUnit );
    }


    @Override
    public void onCommit( Request request )
    {
        // we only care about total count and start/end so just record 1 :-)
        this.recorder.recordValue( 1 );
    }

    @Override
    public void onFailure( Request request, Throwable failure )
    {
        // gcloud log doesn't show stack trace to turn it to a String
        LOGGER.debug( "fail to send request {}", Utils.toString( failure ) );
    }

    private static class ValueDisplayRunnable
        implements Runnable
    {
        private volatile Recorder recorder;

        private final List<ValueListener> valueListeners;

        public ValueDisplayRunnable( Recorder recorder, List<ValueListener> valueListeners )
        {
            this.recorder = recorder;
            this.valueListeners = valueListeners;
        }

        @Override
        public void run()
        {
            Histogram histogram = this.recorder.getIntervalHistogram();
            for (ValueListener valueListener : valueListeners)
            {
                valueListener.onValue( histogram );
            }
        }
    }

    public interface ValueListener
    {
        void onValue(Histogram histogram);
    }

    public static class PrintValueListener implements ValueListener
    {

        private String hostname;

        public PrintValueListener( String hostname )
        {
            this.hostname = hostname;
        }

        @Override
        public void onValue( Histogram histogram )
        {
            long totalRequestCommitted = histogram.getTotalCount();
            long start = histogram.getStartTimeStamp();
            long end = histogram.getEndTimeStamp();
            CollectorInformations collectorInformations = new CollectorInformations( histogram );
            LOGGER.info( "----------------------------------------" );
            LOGGER.info( "--------    QPS estimation    ----------" );
            LOGGER.info( "----------------------------------------" );
            long timeInSeconds = TimeUnit.SECONDS.convert( end - start, TimeUnit.MILLISECONDS );
            long qps = totalRequestCommitted / timeInSeconds;
            LOGGER.info( "host '" + hostname + "' estimated_live QPS : " + qps );
            LOGGER.info( "----------------------------------------" );
            LOGGER.info( "--------  Request commit time  ----------" );
            LOGGER.info( "-----------------------------------------" );
            LOGGER.info( collectorInformations.toString( true ) );
        }
    }

    @Override
    public void onEnd( LoadGenerator generator )
    {
        this.scheduledExecutorService.shutdownNow();
    }
}
