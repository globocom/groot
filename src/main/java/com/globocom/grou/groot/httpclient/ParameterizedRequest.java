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

package com.globocom.grou.groot.httpclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globocom.grou.groot.Application;
import com.globocom.grou.groot.entities.properties.GrootProperties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.RequestBuilder;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.util.*;

import static com.globocom.grou.groot.entities.properties.GrootProperties.*;

@SuppressWarnings("unchecked")
public class ParameterizedRequest extends RequestBuilder {

    private static final Log LOGGER = LogFactory.getLog(ParameterizedRequest.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ParameterizedRequest(final Map<String, Object> properties) {
        super(Optional.ofNullable((String)  properties.get(METHOD)).orElse(HttpMethod.GET.name()));

        try {
            LOGGER.info("Loading properties " + MAPPER.writeValueAsString(properties));
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
        }

        /* url */
        setUrl(URI.create((String) properties.get(URI_REQUEST)).toString());

        /* headers */
        final Object headersObj = properties.get(HEADERS);
        if (headersObj instanceof Map) {
            Map<String, String> headers = (Map<String, String>) headersObj;
            if (headers.containsKey("host")) {
                setVirtualHost(headers.get("host"));
            }
            headers.entrySet().stream().filter(e -> !e.getKey().isEmpty() && !e.getValue().isEmpty()).forEach(e -> setHeader(e.getKey(), e.getValue()));
        }
        setHeader("User-Agent", Application.GROOT_USERAGENT);

        /* body */
        String body = (String) properties.get(BODY);
        if (body != null) setBody(body);

        /* auth */
        final Object authObj = properties.get(GrootProperties.AUTH);
        if (authObj instanceof Map) {
            Map<String, String> authMap = (Map<String, String>) authObj;
            String credentials = authMap.get(CREDENTIALS);
            String preemptive = authMap.get(PREEMPTIVE);
            int idx;
            if (credentials != null && (idx = credentials.indexOf(":")) > -1) {
                String login = credentials.substring(0, idx);
                String password = credentials.substring(idx + 1);
                if ("true".equalsIgnoreCase(preemptive)) {
                    setRealm(Dsl.basicAuthRealm(login, password).setUsePreemptiveAuth(true).build());
                } else {
                    setRealm(Dsl.basicAuthRealm(login, password).build());
                }
            }
        }
    }

}
