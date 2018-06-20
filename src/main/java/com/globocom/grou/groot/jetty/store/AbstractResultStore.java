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

import org.springframework.util.StringUtils;

import java.util.Map;

public abstract class AbstractResultStore implements ResultStore {

    /**
     * default implementation checking if fqcn is a sys prop with true.
     */
    @Override
    public boolean isActive(Map<String, String> setupData) {
        return Boolean.getBoolean(getClass().getName()) || Boolean.parseBoolean(
            setupData.get(getClass().getName()));
    }

    protected String getSetupValue(Map<String, String> setupData, String key, String defaultValue) {
        String value = setupData.get(key);
        return !StringUtils.isEmpty(value) ? value : System.getProperty(key, defaultValue);
    }

    protected Integer getSetupValue(Map<String, String> setupData, String key, int defaultValue) {
        String value = setupData.get(key);
        return !StringUtils.isEmpty(value) ? Integer.valueOf(value) : Integer.getInteger(key, defaultValue);
    }

}
