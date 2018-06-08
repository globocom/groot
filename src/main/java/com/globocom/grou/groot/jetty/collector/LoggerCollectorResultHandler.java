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


import com.globocom.grou.groot.jetty.listeners.CollectorInformations;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.util.Map;

/**
 *
 */
public class LoggerCollectorResultHandler
    implements com.globocom.grou.groot.jetty.collector.CollectorResultHandler
{
    private static final Logger LOGGER = Log.getLogger( LoggerCollectorResultHandler.class );


    @Override
    public void handleResponseTime( Map<String, CollectorInformations> responseTimePerPath )
    {
        for ( Map.Entry<String, CollectorInformations> entry : responseTimePerPath.entrySet() )
        {
            LOGGER.info( "path: {}, responseTime: {}", entry.getKey(), entry.getValue() );
        }
    }
}
