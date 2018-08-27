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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;

@JsonInclude(NON_NULL)
public class AuthProperty implements Serializable {

    /**
     * Credentials (format login:password)
     */
    private String credentials;

    /**
     * Send preemptively the credentials or wait 401
     */
    private Boolean preemptive = true;

    public String getCredentials() {
        return credentials;
    }

    public AuthProperty setCredentials(String credentials) {
        this.credentials = credentials;
        return this;
    }

    @Deprecated
    public Boolean getPreemptive() {
        return preemptive;
    }

    @Deprecated
    public AuthProperty setPreemptive(Boolean preemptive) {
        this.preemptive = preemptive;
        return this;
    }
}
