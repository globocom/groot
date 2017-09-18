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

package com.globocom.grou.groot.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.http.util.Asserts;
import org.bson.types.ObjectId;

import java.io.Serializable;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private ObjectId id;

    private String name;

    public Project() {
        this.name = "UNDEF";
    }

    public Project(String name) {
        Asserts.notNull(name, "Name is NULL");
        this.name = name;
    }

    public ObjectId getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
