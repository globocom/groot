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

package com.globocom.grou.groot.jetty.listeners;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;

public class ServerInfo {

    private String jettyVersion;

    private int availableProcessors;

    private long totalMemory;

    private String gitHash;

    private String javaVersion;

    public String getJettyVersion() {
        return jettyVersion;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public void setJettyVersion(String jettyVersion) {
        this.jettyVersion = jettyVersion;
    }

    public void setAvailableProcessors(int availableProcessors) {
        this.availableProcessors = availableProcessors;
    }

    public void setTotalMemory(long totalMemory) {
        this.totalMemory = totalMemory;
    }

    public String getGitHash() {
        return gitHash;
    }

    public void setGitHash(String gitHash) {
        this.gitHash = gitHash;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    @Override
    public String toString() {
        return "ServerInfo{" + "jettyVersion='" + jettyVersion + '\'' + ", availableProcessors=" + availableProcessors
            + ", totalMemory=" + totalMemory + ", gitHash='" + gitHash + '\'' + ", javaVersion='" + javaVersion + '\''
            + '}';
    }

    public static ServerInfo retrieveServerInfo(String scheme, String host, int port, String path)
        throws Exception {
        HttpClient httpClient = new HttpClient();

        try {
            httpClient.start();
            ContentResponse contentResponse = httpClient.newRequest(host, port).scheme(scheme).path(path).send();

            return new ObjectMapper() //
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) //
                .readValue(contentResponse.getContent(), ServerInfo.class);
        } finally {
            httpClient.stop();
        }

    }

}
