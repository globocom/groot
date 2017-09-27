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
import org.apache.http.HttpHeaders;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.RequestBuilder;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("unchecked")
public class ParameterizedRequest extends RequestBuilder {

    private final String testName;
    private final String testProject;

    public ParameterizedRequest(Test test) {
        super(Optional.ofNullable((String) test.getProperties().get("method")).orElse(HttpMethod.GET.name()));
        this.testName = test.getName();
        this.testProject = test.getProject();

        final Map<String, Object> properties = test.getProperties();
        final URI uri = URI.create(Optional.ofNullable((String) properties.get("uri")).orElseThrow(() -> new IllegalArgumentException("uri property undefined")));

        setUrl(uri.toString());

        Optional.ofNullable((Map<String, String>) properties.get("headers")).orElse(Collections.emptyMap()).forEach(this::setHeader);
        setHeader(HttpHeaders.USER_AGENT, Application.GROOT_USERAGENT);
        if (method.matches("(POST|PUT|PATCH)")) {
            String body = Optional.ofNullable((String) properties.get("body")).orElseThrow(() -> new IllegalArgumentException("body property undefined"));
            if (body.isEmpty()) throw new IllegalArgumentException("body is empty");
            setBody(body);
        }
        final String auth = (String) properties.get("auth");
        if (auth != null && auth.contains(":")) {
            String[] authArray = auth.split(":");
            if (authArray.length > 1) {
                String login = authArray[0];
                String password = authArray[1];
                setRealm(Dsl.basicAuthRealm(login, password).build());
            }
        }
    }

    public String getTestName() {
        return testName;
    }

    public String getTestProject() {
        return testProject;
    }
}
