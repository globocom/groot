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

    private String id;

    private String name;

    private String project;

    private String loader;

    private Map<String, Object> properties = new HashMap<>();

    private Set<String> tags = new HashSet<>();

    private Status status = Status.SCHEDULED;

    private String statusDetailed = "";

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getProject() {
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

    public String getStatusDetailed() {
        return statusDetailed;
    }

    public void setStatusDetailed(String status_detailed) {
        this.statusDetailed = status_detailed;
    }

    public String getLoader() {
        return loader;
    }

    public void setLoader(String loader) {
        this.loader = loader;
    }
}