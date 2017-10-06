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

import com.globocom.grou.groot.monit.collectors.snmp.SimpleSnmpClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.OID;

import java.io.IOException;

public class SnmpMetricsCollector extends MetricsCollector {

    private static final OID[] OID_CONN_ESTAB     = {new OID("1.3.6.1.2.1.6.9.0")};
    private static final OID[] OID_MEM_AVAIL_REAL = {new OID("1.3.6.1.4.1.2021.4.6.0")};
    private static final OID[] OID_MEM_BUFFER     = {new OID("1.3.6.1.4.1.2021.4.14.0")};
    private static final OID[] OID_MEM_CACHED     = {new OID("1.3.6.1.4.1.2021.4.15.0")};
    private static final OID[] OID_CPU_IDLE       = {new OID("1.3.6.1.4.1.2021.11.11.0")};
    private static final OID[] OID_LOAD1          = {new OID("1.3.6.1.4.1.2021.10.1.3.1")};
    private static final OID[] OID_LOAD5          = {new OID("1.3.6.1.4.1.2021.10.1.3.2")};
    private static final OID[] OID_LOAD15         = {new OID("1.3.6.1.4.1.2021.10.1.3.3")};

    private final Log log = LogFactory.getLog(this.getClass());

    @Override
    public int getConns() {
        return getSnmpValueInt(OID_CONN_ESTAB);
    }

    @Override
    public double getMemFree() {
        int memAvailReal = getSnmpValueInt(OID_MEM_AVAIL_REAL);
        int memBuffer = getSnmpValueInt(OID_MEM_BUFFER);
        int memCached = getSnmpValueInt(OID_MEM_CACHED);
        return memAvailReal + memBuffer + memCached ;
    }

    @Override
    public int getCpuUsed() {
        return 100 - getSnmpValueInt(OID_CPU_IDLE);
    }

    @Override
    public float getLoad1m() {
        try {
            return Float.valueOf(getSnmpValueStr(OID_LOAD1));
        } catch (NumberFormatException ignore) {
            return -1.0f;
        }
    }

    @Override
    public float getLoad5m() {
        try {
            return Float.valueOf(getSnmpValueStr(OID_LOAD5));
        } catch (NumberFormatException ignore) {
            return -1.0f;
        }
    }

    @Override
    public float getLoad15m() {
        try {
            return Float.valueOf(getSnmpValueStr(OID_LOAD15));
        } catch (NumberFormatException ignore) {
            return -1.0f;
        }
    }

    @Override
    protected int defaultPort() {
        return 161;
    }

    private int getSnmpValueInt(final OID[] oid) {
        int value;
        try (final SimpleSnmpClient snmpClient = new SimpleSnmpClient(uriHost, uriPort, queryParams)) {
            ResponseEvent event = snmpClient.get(oid);
            value = SimpleSnmpClient.extractSingleInt(event);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            value = -1;
        }
        return value;
    }

    private String getSnmpValueStr(final OID[] oid) {
        String value;
        try (final SimpleSnmpClient snmpClient = new SimpleSnmpClient(uriHost, uriPort, queryParams)) {
            ResponseEvent event = snmpClient.get(oid);
            value = SimpleSnmpClient.extractSingleString(event);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            value = "0.0";
        }
        return value;
    }
}
