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

package com.globocom.grou.groot.test.properties;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class RequestProperty implements Serializable, Comparable<RequestProperty> {

    private static final long serialVersionUID = 1L;

    /**
     * Request Order (if using multiples per test)
     */
    private Integer order;

    /**
     * URI request
     */
    private String uri;

    /**
     * Authentication properties. Contains credentials & preemptive properties
     */
    private AuthProperty auth;

    /**
     * Body request
     */
    private String body;

    /**
     * Headers request
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * Method request
     */
    private String method = "GET";

    public Integer getOrder() {
        return order;
    }

    public RequestProperty setOrder(Integer order) {
        this.order = order;
        return this;
    }

    public String getUri() {
        return uri;
    }

    public RequestProperty setUri(String uri) {
        this.uri = uri;
        return this;
    }

    public AuthProperty getAuth() {
        return auth;
    }

    public RequestProperty setAuth(AuthProperty auth) {
        this.auth = auth;
        return this;
    }

    public String getBody() {
        return body;
    }

    public RequestProperty setBody(String body) {
        this.body = body;
        return this;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public RequestProperty setHeaders(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public RequestProperty setMethod(String method) {
        this.method = method;
        return this;
    }

    @Override
    public int compareTo(RequestProperty o) {
        return getOrder() - o.getOrder();
    }
}
