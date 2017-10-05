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
import java.net.URI;

/**
 * Ref: https://blog.jayway.com/2010/05/21/introduction-to-snmp4j
 */


public class SimpleSnmpClient implements AutoCloseable {

    private String snmpHostTarget = "localhost";
    private String snmpPortStr = "161";
    private String snmpCommunity = "public";
    private String snmpVersion = "2c";

    private Snmp snmp;

    public SimpleSnmpClient(URI uri) {
        super();
        extractQueryParams(uri);
        try {
            start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void extractQueryParams(final URI uri) {
        String query = uri.getQuery();
        String[] attrs = query.split("&");
        for (String attr : attrs) {
            int indexOf;
            if ((indexOf = attr.indexOf("=")) > 0) {
                String key = attr.substring(0, indexOf);
                String value = attr.substring(indexOf + 1);
                switch (key) {
                    case "snmpTarget":
                        snmpHostTarget = value;
                        break;
                    case "snmpPort":
                        snmpPortStr = value;
                        break;
                    case "snmpCommunity":
                        snmpCommunity = value;
                        break;
                    case "snmpVersion":
                        snmpVersion = value;
                        break;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        snmp.close();
    }

    private void start() throws IOException {
        TransportMapping<? extends Address> transport = new DefaultUdpTransportMapping();
        snmp = new Snmp(transport);
        // Do not forget this line!
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
        Address targetAddress = GenericAddress.parse("udp:" + snmpHostTarget + "/" + snmpPortStr);
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(snmpCommunity));
        target.setAddress(targetAddress);
        target.setRetries(2);
        target.setTimeout(1500);
        target.setVersion("2c".equals(snmpVersion) ? SnmpConstants.version2c : SnmpConstants.version1);
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