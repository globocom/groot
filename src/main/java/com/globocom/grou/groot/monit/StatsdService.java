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

package com.globocom.grou.groot.monit;

import io.galeb.statsd.NonBlockingStatsDClient;
import io.galeb.statsd.StatsDClient;
import org.springframework.stereotype.Service;

import static com.globocom.grou.groot.SystemEnv.*;

@Service
public class StatsdService {

    private final NonBlockingStatsDClient statsDClient;

    public StatsdService() {
        statsDClient = new NonBlockingStatsDClient(
                STATSD_PREFIX.getValue(),
                STATSD_HOST.getValue(),
                Integer.parseInt(STATSD_PORT.getValue()));
    }

    public StatsDClient client() {
        return statsDClient;
    }
}
