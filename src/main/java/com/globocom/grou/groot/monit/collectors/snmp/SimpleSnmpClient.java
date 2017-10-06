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

package com.globocom.grou.groot.monit.collectors.snmp;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Ref: https://blog.jayway.com/2010/05/21/introduction-to-snmp4j
 */


public class SimpleSnmpClient implements AutoCloseable {

    private final String snmpHost;
    private final int snmpPort;
    private final String community;
    private final String version;

    private Snmp snmp;

    public SimpleSnmpClient(String uriHost, int uriPort, final Map<String, String> queryParams) {
        super();
        this.snmpHost = uriHost;
        this.snmpPort = uriPort;
        this.community = Optional.ofNullable(queryParams.get("community")).orElse("public");
        this.version = Optional.ofNullable(queryParams.get("version")).orElse("2c");
        try {
            start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        snmp.close();
    }

    private void start() throws IOException {
        TransportMapping<? extends Address> transport = new DefaultUdpTransportMapping();
        snmp = new Snmp(transport);
        transport.listen();
    }

    private PDU getPDU(OID oids[]) {
        PDU pdu = new PDU();
        for (OID oid : oids) {
            pdu.add(new VariableBinding(oid));
        }

        pdu.setType(PDU.GET);
        return pdu;
    }

    public ResponseEvent get(OID oids[]) throws IOException {
        ResponseEvent event = snmp.send(getPDU(oids), getTarget(), null);
        if(event != null) {
            return event;
        }
        throw new RuntimeException("GET timed out");
    }

    private Target getTarget() {
        Address targetAddress = GenericAddress.parse("udp:" + snmpHost + "/" + snmpPort);
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(community));
        target.setAddress(targetAddress);
        target.setRetries(2);
        target.setTimeout(1500);
        target.setVersion("1".equals(version) ? SnmpConstants.version1 : SnmpConstants.version2c);
        return target;
    }

    public static String extractSingleString(ResponseEvent event) {
        final PDU response = event.getResponse();
        if (response != null && response.size() > 0) {
            return response.get(0).getVariable().toString();
        }
        return "";
    }

    public static int extractSingleInt(ResponseEvent event) {
        final PDU response = event.getResponse();
        if (response != null && response.size() > 0) {
            return response.get(0).getVariable().toInt();
        }
        return -1;
    }
}