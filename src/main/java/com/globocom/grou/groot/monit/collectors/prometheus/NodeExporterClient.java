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

package com.globocom.grou.groot.monit.collectors.prometheus;

import com.globocom.grou.groot.Application;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.config;

public class NodeExporterClient {

    private final AsyncHttpClient asyncHttpClient;

    public NodeExporterClient() {
        DefaultAsyncHttpClientConfig.Builder config = config()
                .setFollowRedirect(false)
                .setSoReuseAddress(true)
                .setKeepAlive(true)
                .setConnectTimeout(2000)
                .setMaxConnectionsPerHost(100)
                .setMaxConnections(100)
                .setUseInsecureTrustManager(true)
                .setUserAgent(Application.GROOT_USERAGENT);
        asyncHttpClient = asyncHttpClient(config);
    }

    public Map<String, String> get(String url) throws ExecutionException, InterruptedException, IOException {
        Map<String, String> result = new HashMap<>();
        Response response = asyncHttpClient.prepareGet(url).execute().get();
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.getResponseBodyAsStream(), Charset.defaultCharset().name()));

        String line;
        while ((line = reader.readLine()) != null) {
            int index = line.indexOf(" ");
            if (index > 0 && !"#".equals(line.substring(0,1))) {
                String key = line.substring(0, index);
                String value = line.substring(index + 1);
                result.put(key, value);
            }
        }
        return result;
    }
}
