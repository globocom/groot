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

package com.globocom.grou.groot.statsd;

import com.sun.management.UnixOperatingSystemMXBean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;

public final class SystemInfo {

    private static final Log log = LogFactory.getLog(SystemInfo.class);
    private static final UnixOperatingSystemMXBean OS = (UnixOperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private SystemInfo() { }

    public static int totalSocketsTcpEstablished() {
        try {
            if (getOS().startsWith("linux")) {
                final BufferedReader br = exec("/bin/sh", "-c", "ss -s");
                br.readLine();
                return Integer.valueOf(br.readLine().replaceAll(".*estab ([0-9]+).*", "$1"));
            }
            if (getOS().startsWith("mac")) {
                final BufferedReader br = exec("/bin/sh", "-c", "netstat -an -p tcp | grep EST | wc -l");
                return Integer.valueOf(br.readLine().trim());
            }
            return -1;
        } catch (IOException e) {
            log.error(e);
            return -1;
        }
    }

    public static String getOS() {
        return System.getProperty("os.name", "UNDEF").toLowerCase();
    }

    public static double cpuLoad() {
        return OS.getSystemCpuLoad();
    }

    public static long memFree() {
        return OS.getFreePhysicalMemorySize();
    }

    public static String hostname() {
        String hostname;
        if ((hostname = System.getenv("HOSTNAME")) == null) {
            hostname = ManagementFactory.getRuntimeMXBean().getName();
        }
        return hostname;
    }
    
    private static BufferedReader exec(String... command) throws IOException {
        final Process process = Runtime.getRuntime().exec(command);
        return new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

}
