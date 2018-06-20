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

package com.globocom.grou.groot.jetty.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globocom.grou.groot.jetty.listeners.LoadResult;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.*;

import static com.globocom.grou.groot.LogUtils.format;

public class ElasticResultStore extends AbstractResultStore implements ResultStore {

    private static final Log LOGGER = LogFactory.getLog(ElasticResultStore.class);

    public static final String ID = "elastic";

    public static final String HOST_KEY = "elastic.host";

    public static final String PORT_KEY = "elastic.port";

    public static final String SCHEME_KEY = "elastic.scheme";

    public static final String USER_KEY = "elastic.user";

    public static final String PWD_KEY = "elastic.password";

    private HttpClient httpClient;

    private String host;
    private String scheme;
    private String username;
    private String password;
    private int port;

    static {
        Configuration.setDefaults(new Configuration.Defaults() {

            private final JsonProvider jsonProvider = new JacksonJsonProvider(
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

            private final MappingProvider mappingProvider = new JacksonMappingProvider(
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });
    }


    @Override
    public void initialize(Map<String, String> setupData) {
        host = getSetupValue(setupData, HOST_KEY, "localhost");
        port = getSetupValue(setupData, PORT_KEY, 9200);
        scheme = getSetupValue(setupData, SCHEME_KEY, "http");
        username = getSetupValue(setupData, USER_KEY, null);
        password = getSetupValue(setupData, PWD_KEY, null);

        this.httpClient = new HttpClient(new SslContextFactory(true));
        try {
            if (StringUtils.isNotEmpty(username)) {
                URI uri = new URI(scheme + "://" + host + ":" + port);
                AuthenticationStore auth = httpClient.getAuthenticationStore();
                auth.addAuthenticationResult(new BasicAuthentication.BasicResult(uri, username, password));
            }
            this.httpClient.start();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(format("elastic http client initialize to {}:{}", host, port));
            }
        } catch (Exception e) {
            LOGGER.warn(e);
            throw new RuntimeException("Cannot start http client: " + e.getMessage(), e);
        }
    }

    @Override
    public void save(LoadResult loadResult) {
        try {
            StringWriter stringWriter = new StringWriter();
            new ObjectMapper().writeValue(stringWriter, loadResult);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(format("save loadResult with UUID {}", loadResult.getUuid()));
            }

            ContentResponse contentResponse = httpClient.newRequest(host, port).scheme(scheme)
                .path("/loadresult/result/" + loadResult.getUuid())
                .content(new StringContentProvider(stringWriter.toString()))
                .method(HttpMethod.PUT)
                .header("Content-Type", "application/json")
                .send();

            if (contentResponse.getStatus() != HttpStatus.CREATED_201) {
                LOGGER.info(format("Cannot record load result: {}", contentResponse.getContentAsString()));
            } else {
                LOGGER.info("Load result recorded");
            }
        } catch (Exception e) {
            LOGGER.warn("Cannot save result:" + e.getMessage(), e);
            //throw new RuntimeException( e.getMessage(), e );
        }
    }

    @Override
    public void remove(LoadResult loadResult) {
        try {
            ContentResponse contentResponse = httpClient.newRequest(host, port)
                .scheme(scheme)
                .path("/loadresult/result/" + loadResult.getUuid())
                .method(HttpMethod.DELETE)
                .send();
            if (contentResponse.getStatus() != HttpStatus.OK_200) {
                LOGGER.info(format("Cannot delete load result: {}", contentResponse.getContentAsString()));
            } else {
                LOGGER.info("Load result deleted");
            }
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public LoadResult get(String loadResultId) {
        try {
            ContentResponse contentResponse = httpClient.newRequest(host, port)
                .scheme(scheme)
                .path("/loadresult/result/_search/" + loadResultId)
                .method(HttpMethod.GET)
                .send();
            if (contentResponse.getStatus() != HttpStatus.OK_200) {
                LOGGER.info(format("Cannot get load result: {}", contentResponse.getContentAsString()));
                return null;
            }

            List<LoadResult> loadResults = map(contentResponse);

            LOGGER.debug(format("result {}", loadResults));
            return loadResults == null || loadResults.isEmpty() ? null : loadResults.get(0);

        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public List<LoadResult> searchResultsByExternalId(String anExternalId) {
        try {
            final Map<String, Object> externalId = new HashMap<>();
            externalId.put("externalId", anExternalId);
            final Map<String, Object> term = new HashMap<>();
            term.put("term", externalId);

            final Map<String, Object> filter = new HashMap<>();
            filter.put("filter", term);

            final Map<String, Object> order = new HashMap<>();
            order.put("order", "desc");
            final Map<String, Object> timestamp = new HashMap<>();
            timestamp.put("timestamp", order);

            final Map<String, Object> constant_score = new HashMap<>();
            constant_score.put("constant_score", filter);

            final Map<String, Object> json = new HashMap<>();
            json.put("query", constant_score);
            json.put("sort", timestamp);

            StringWriter stringWriter = new StringWriter();
            new ObjectMapper().writeValue(stringWriter, json);

            ContentResponse contentResponse = getHttpClient()
                .newRequest(host, port)
                .scheme(scheme)
                .header("Content-Type", "application/json")
                .method(HttpMethod.GET)
                .path("/loadresult/result/_search?sort=timestamp")
                .content(new StringContentProvider(stringWriter.toString()))
                .send();
            return map(contentResponse);
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<LoadResult> get(List<String> loadResultIds) {
        try {
            // we need this type of Json
            //{
            //  "query": {
            //    "ids" : {
            //        "values" : ["192267e6-7f74-4806-867a-c13ef777d6eb", "80a2dc5b-4a92-48ba-8f5b-f2de1588318a"]
            //    }
            //  }
            //}
            final Map<String, Object> values = new HashMap<>();
            values.put("values", loadResultIds);
            final Map<String, Object> ids = new HashMap<>();
            ids.put("ids", values);
            final Map<String, Object> query = new HashMap<>();
            query.put("query", ids);
            final StringWriter stringWriter = new StringWriter();
            final ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writeValue(stringWriter, query);

            final ContentResponse contentResponse = getHttpClient()
                .newRequest(host, port)
                .scheme(scheme)
                .method(HttpMethod.GET)
                .header("Content-Type", "application/json")
                .path("/loadresult/result/_search?sort=timestamp")
                .content(new StringContentProvider(stringWriter.toString()))
                .send();
            return map(contentResponse);

        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public String search(String searchPost) {
        try {
            ContentResponse contentResponse = getHttpClient()
                .newRequest(host, port)
                .scheme(scheme)
                .method(HttpMethod.GET)
                .header("Content-Type", "application/json")
                .path("/loadresult/result/_search?pretty")
                .content(new StringContentProvider(searchPost))
                .send();
            return contentResponse.getContentAsString();
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<LoadResult> find(QueryFilter queryFilter) {
        return null;
    }

    @Override
    public List<LoadResult> findAll() {
        try {
            ContentResponse contentResponse = httpClient.newRequest(host, port)
                .scheme(scheme)
                .path("/loadresult/result/_search?pretty")
                .method(HttpMethod.GET)
                .send();
            if (contentResponse.getStatus() != HttpStatus.OK_200) {
                LOGGER.info(format("Cannot get load result: {}", contentResponse.getContentAsString()));
                return Collections.emptyList();
            }

            List<LoadResult> loadResults = map(contentResponse);

            LOGGER.debug(format("result {}", loadResults));
            return loadResults;

        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            this.httpClient.stop();
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    public static List<LoadResult> map(ContentResponse contentResponse) {
        return map(Collections.singletonList(contentResponse));
    }

    public static List<LoadResult> map(List<ContentResponse> contentResponses) {
        List<LoadResult> results = new ArrayList<>();
        contentResponses.forEach(
            contentResponse -> results.addAll(
                JsonPath.parse(contentResponse.getContentAsString())
                    .read("$.hits.hits[*]._source", new TypeRef<List<LoadResult>>() {
                    })));
        return results;
    }

    private HttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public String getProviderId() {
        return ID;
    }
}
