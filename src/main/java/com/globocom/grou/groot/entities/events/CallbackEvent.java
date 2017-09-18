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

package com.globocom.grou.groot.entities.events;

import com.globocom.grou.groot.entities.Loader;
import com.globocom.grou.groot.entities.Test;

import java.io.Serializable;

public class CallbackEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Test test;
    private final Loader loader;

    public CallbackEvent(Test test, Loader loader) {
        this.test = test;
        this.loader = loader;
    }

    public Test getTest() {
        return test;
    }

    public Loader getLoader() {
        return loader;
    }
}
