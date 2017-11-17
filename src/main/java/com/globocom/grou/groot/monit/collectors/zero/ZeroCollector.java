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

package com.globocom.grou.groot.monit.collectors.zero;

import com.globocom.grou.groot.monit.collectors.MetricsCollector;

public class ZeroCollector extends MetricsCollector {

    @Override
    public int getConns() {
        return 0;
    }

    @Override
    public double getMemFree() {
        return 0.0;
    }

    @Override
    public double getMemBuffers() {
        return 0.0;
    }

    @Override
    public double getMemCached() {
        return 0.0;
    }

    @Override
    public int getCpuUsed() {
        return 0;
    }

    @Override
    public int getCpuIoWait() {
        return 0;
    }

    @Override
    public int getCpuSteal() {
        return 0;
    }

    @Override
    public int getCpuIrq() {
        return 0;
    }

    @Override
    public int getCpuSoftIrq() {
        return 0;
    }

    @Override
    public float getLoad1m() {
        return 0.0f;
    }

    @Override
    public float getLoad5m() {
        return 0.0f;
    }

    @Override
    public float getLoad15m() {
        return 0.0f;
    }

    @Override
    protected int defaultPort() {
        return 0;
    }
}
