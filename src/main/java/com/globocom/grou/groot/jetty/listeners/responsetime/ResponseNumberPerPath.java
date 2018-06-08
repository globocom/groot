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

import com.globocom.grou.groot.jetty.generator.Resource;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class ResponseNumberPerPath
    implements Resource.NodeListener, Serializable
{

    private final Map<String, AtomicInteger> responseNumberPerPath = new ConcurrentHashMap<>();

    @Override
    public void onResourceNode( Resource.Info info )
    {
        String path = info.getResource().getPath();

        // response number record
        {

            AtomicInteger number = responseNumberPerPath.get( path );
            if ( number == null )
            {
                number = new AtomicInteger( 1 );
                responseNumberPerPath.put( path, number );
            }
            else
            {
                number.incrementAndGet();
            }

        }
    }

    public Map<String, AtomicInteger> getResponseNumberPerPath()
    {
        return responseNumberPerPath;
    }

}
