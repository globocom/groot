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

package com.globocom.grou.groot.monit.collectors;

import java.net.URI;

public interface MetricsCollector {
    MetricsCollector setUri(URI uri);
    int getConns();
    double getMemFree();
    int getCpuUsed();
    float getLoad1m();
    float getLoad5m();
    float getLoad15m();
    String getTargetFormated();
}
