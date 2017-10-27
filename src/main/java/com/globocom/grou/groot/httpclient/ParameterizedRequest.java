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

import com.globocom.grou.groot.Application;
import com.globocom.grou.groot.entities.Test;
import com.globocom.grou.groot.entities.properties.GrootProperties;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.RequestBuilder;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unchecked")
public class ParameterizedRequest extends RequestBuilder {

    private final AtomicReference<String> body = new AtomicReference<>("");
    private final AtomicReference<String> url = new AtomicReference<>("");

    public ParameterizedRequest(Test test) {
        super(Optional.ofNullable((String)  test.getProperties().get(GrootProperties.METHOD)).orElse(HttpMethod.GET.name()));

        final Map<String, Object> properties = test.getProperties();
        final URI uri = URI.create(Optional.ofNullable((String) properties.get(GrootProperties.URI)).orElseThrow(() -> new IllegalArgumentException(GrootProperties.URI + " property undefined")));
        setUrl(url.updateAndGet(u -> uri.toString()));

        final Object headersObj = properties.get(GrootProperties.HEADERS);
        if (headersObj instanceof Map) {
            Map<String, String> headers = (Map<String, String>) headersObj;
            headers.entrySet().stream().filter(e -> !e.getKey().isEmpty() && !e.getValue().isEmpty()).forEach(e -> setHeader(e.getKey(), e.getValue()));
        }
        setHeader("User-Agent", Application.GROOT_USERAGENT);
        if (method.matches("(POST|PUT|PATCH)")) {
            body.set(Optional.ofNullable((String) properties.get(GrootProperties.BODY)).orElseThrow(() -> new IllegalArgumentException(GrootProperties.BODY + " property undefined")));
            if (body.get().isEmpty()) throw new IllegalArgumentException("body is empty");
            setBody(body.get());
            System.out.println(body.get());
        }

        final Object authObj = properties.get(GrootProperties.AUTH);
        if (authObj instanceof Map) {
            Map<String, String> authMap = (Map<String, String>) authObj;
            String credentials = authMap.get(GrootProperties.CREDENTIALS);
            String preemptive = authMap.get(GrootProperties.PREEMPTIVE);
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
