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

package com.globocom.grou.groot.jetty.listeners.responsetime;

import com.globocom.grou.groot.jetty.generator.LoadGenerator;
import com.globocom.grou.groot.jetty.generator.Resource;
import com.globocom.grou.groot.jetty.listeners.CollectorInformations;
import com.globocom.grou.groot.jetty.listeners.HistogramConstants;
import org.HdrHistogram.AtomicHistogram;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.globocom.grou.groot.LogUtils.format;

/**
 * <p>Use {@link AtomicHistogram} to tracker response/latency time per path</p>
 * <p>
 * Print out general statistics when stopping.
 * To prevent that and only get the values simply use the constructor with <code>false</code>
 * </p>
 */
public class TimePerPathListener
    implements Resource.NodeListener, LoadGenerator.EndListener, LoadGenerator.BeginListener, Serializable
{

    private static final Log LOGGER = LogFactory.getLog(TimePerPathListener.class);

    private Map<String, AtomicHistogram> responseTimePerPath = new ConcurrentHashMap<>();

    private Map<String, AtomicHistogram> latencyTimePerPath = new ConcurrentHashMap<>();

    private boolean printOnEnd = true;

    private long lowestDiscernibleValue = HistogramConstants.LOWEST_DISCERNIBLE_VALUE;

    private long highestTrackableValue = HistogramConstants.HIGHEST_TRACKABLE_VALUE;

    private int numberOfSignificantValueDigits = HistogramConstants.NUMBER_OF_SIGNIFICANT_VALUE_DIGITS;

    private boolean nanoDisplay = true;

    public TimePerPathListener( boolean printOnEnd, long lowestDiscernibleValue, long highestTrackableValue,
                                int numberOfSignificantValueDigits )
    {
        this( printOnEnd );
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
    }


    public TimePerPathListener( boolean printOnEnd, boolean nanoDisplay )
    {
        this.printOnEnd = printOnEnd;
        this.nanoDisplay = nanoDisplay;
    }

    public TimePerPathListener( boolean printOnEnd )
    {
        this.printOnEnd = printOnEnd;
    }


    public TimePerPathListener()
    {
        this( true );
    }

    @Override
    public void onBegin( LoadGenerator loadGenerator )
    {
        // we initialize Maps to avoid concurrent issues
        responseTimePerPath = new ConcurrentHashMap<>();
        initializeMap( responseTimePerPath, loadGenerator.getConfig().getResource().getResources() );
        latencyTimePerPath = new ConcurrentHashMap<>();
        initializeMap( latencyTimePerPath, loadGenerator.getConfig().getResource().getResources() );
    }

    private void initializeMap( Map<String, AtomicHistogram> histogramMap, List<Resource> resources )
    {
        for ( Resource resource : resources )
        {
            AtomicHistogram atomicHistogram = histogramMap.get( resource.getPath() );
            if ( atomicHistogram == null )
            {
                atomicHistogram = new AtomicHistogram( lowestDiscernibleValue, //
                                         highestTrackableValue, //
                                         numberOfSignificantValueDigits );
                histogramMap.put( resource.getPath(), atomicHistogram );
            }
            initializeMap( histogramMap, resource.getResources() );
        }
    }

    @Override
    public void onResourceNode( Resource.Info info )
    {
        String path = info.getResource().getPath();
        long responseTime = info.getResponseTime() - info.getRequestTime();
        AtomicHistogram atomicHistogram = responseTimePerPath.get( path );
        if ( atomicHistogram == null )
        {
            atomicHistogram = new AtomicHistogram( lowestDiscernibleValue, //
                                     highestTrackableValue, //
                                     numberOfSignificantValueDigits );
            responseTimePerPath.put( path, atomicHistogram );
        }
        try
        {
            atomicHistogram.recordValue( responseTime );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn(format("skip error recording time {}, {}", responseTime, e.getMessage()));
        }

        long time = info.getLatencyTime() - info.getRequestTime();
        atomicHistogram = latencyTimePerPath.get( path );
        if ( atomicHistogram == null )
        {
            atomicHistogram = new AtomicHistogram( lowestDiscernibleValue, //
                                     highestTrackableValue, //
                                     numberOfSignificantValueDigits );
            latencyTimePerPath.put( path, atomicHistogram );
        }
        try
        {
            atomicHistogram.recordValue( time );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn(format("skip error recording time {}, {}", time, e.getMessage()));
        }
    }

    @Override
    public void onEnd( LoadGenerator generator )
    {
        if ( printOnEnd )
        {
            StringBuilder reportMessage = new StringBuilder();
            if ( !latencyTimePerPath.isEmpty() )
            {
                StringBuilder latencyTimeMessage = new StringBuilder( "--------------------------------------" ) //
                    .append( System.lineSeparator() ) //
                    .append( "   Latency Time Summary               " ).append( System.lineSeparator() ) //
                    .append( "--------------------------------------" ).append( System.lineSeparator() ); //

                for ( Map.Entry<String, AtomicHistogram> entry : latencyTimePerPath.entrySet() )
                {
                    latencyTimeMessage.append( "Path:" ).append( entry.getKey() ).append( System.lineSeparator() );
                    CollectorInformations collectorInformations =
                        new CollectorInformations( entry.getValue() );
                    latencyTimeMessage.append( nanoDisplay
                                                   ? collectorInformations.toStringInNanos( true )
                                                   : collectorInformations.toString( true ) ) //
                        .append( System.lineSeparator() );

                }

                latencyTimeMessage.append( System.lineSeparator() );

                reportMessage.append( latencyTimeMessage );
            }

            if ( !responseTimePerPath.isEmpty() )
            {

                StringBuilder responseTimeMessage =  //
                    new StringBuilder( System.lineSeparator() ) //
                        .append( "--------------------------------------" ).append( System.lineSeparator() ) //
                        .append( "   Response Time Summary              " ).append( System.lineSeparator() ) //
                        .append( "--------------------------------------" ).append( System.lineSeparator() ); //

                for ( Map.Entry<String, AtomicHistogram> entry : responseTimePerPath.entrySet() )
                {
                    responseTimeMessage.append( "Path:" ).append( entry.getKey() ).append( System.lineSeparator() );
                    CollectorInformations collectorInformations =
                        new CollectorInformations( entry.getValue() );

                    responseTimeMessage.append( nanoDisplay
                                                    ? collectorInformations.toStringInNanos( true )
                                                    : collectorInformations.toString( true ) ) //
                        .append( System.lineSeparator() );

                }

                responseTimeMessage.append( System.lineSeparator() );
                reportMessage.append( responseTimeMessage );
            }
            System.out.println( reportMessage );
        }

    }

    public Map<String, AtomicHistogram> getResponseTimePerPath()
    {
        return responseTimePerPath;
    }


    public Map<String, AtomicHistogram> getLatencyTimePerPath()
    {
        return latencyTimePerPath;
    }
}
