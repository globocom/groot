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
import org.bson.types.ObjectId;

import java.io.Serializable;
import java.util.*;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class Test implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        SCHEDULED,
        ENQUEUED,
        RUNNING,
        OK,
        ERROR
    }

    @JsonIgnore
    private ObjectId id;

    private String name;

    private Project project;

    private Loader loader;

    private Map<String, Object> properties = new HashMap<>();

    private Set<String> tags = new HashSet<>();

    private Status status = Status.SCHEDULED;

    private String status_detailed = "";

    public Test() {
        this.name = Objects.toString(null);
        this.project = null;
        this.properties = Collections.emptyMap();
    }

    public Test(String name, Project project, Map<String, Object> properties) {
        this.name = name;
        this.project = project;
        this.properties = properties;
    }

    public ObjectId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Project getProject() {
        return project;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        if (tags != null) {
            this.tags = tags;
        }
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getStatus_detailed() {
        return status_detailed;
    }

    public void setStatus_detailed(String status_detailed) {
        this.status_detailed = status_detailed;
    }

    public Loader getLoader() {
        return loader;
    }

    public void setLoader(Loader loader) {
        this.loader = loader;
    }
}
