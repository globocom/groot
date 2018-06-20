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

import com.globocom.grou.groot.jetty.listeners.LoadResult;

import java.io.Closeable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 *
 */
public interface ResultStore
    extends Closeable {

    /**
     * @param setupData very generic way of passing some data/parameters to initialize the component
     */
    void initialize(Map<String, String> setupData);

    /**
     * save a {@link LoadResult} value
     *
     * @param loadResult the value to save
     */
    void save(LoadResult loadResult);

    /**
     * @param loadResultId the {@link LoadResult} unique to retrieve
     * @return the {@link LoadResult} corresponding to the id
     */
    LoadResult get(String loadResultId);

    /**
     * @param loadResultIds the {@link List} od unique id to retrieve
     * @return the {@link List} of {@link LoadResult}
     */
    List<LoadResult> get(List<String> loadResultIds);

    /**
     * @param loadResult the instance to remove from the store
     */
    void remove(LoadResult loadResult);

    /**
     * @param queryFilter the {@link QueryFilter} to search {@link LoadResult}
     * @return the {@link List} of {@link LoadResult} corresponding to the filter
     */
    List<LoadResult> find(QueryFilter queryFilter);

    /**
     * <b>might be very expensive and some implementations not implemented it</b>
     *
     * @return all {@link LoadResult}
     */
    List<LoadResult> findAll();

    /**
     * @return an unique id corresponding to the {@link ResultStore} implementation
     */
    String getProviderId();

    /**
     * @return <code>true</code> if the {@link ResultStore} implementation is active
     */
    boolean isActive(Map<String, String> setupData);

    class QueryFilter {

        private String jettyVersion;
        private String uuid;

        private Date startDate;
        private Date endDate;

        public String getJettyVersion() {
            return jettyVersion;
        }

        public void setJettyVersion(String jettyVersion) {
            this.jettyVersion = jettyVersion;
        }

        public QueryFilter jettyVersion(String jettyVersion) {
            this.jettyVersion = jettyVersion;
            return this;
        }

        public Date getStartDate() {
            return startDate;
        }

        public void setStartDate(Date startDate) {
            this.startDate = startDate;
        }

        public QueryFilter startDate(Date startDate) {
            this.startDate = startDate;
            return this;
        }

        public Date getEndDate() {
            return endDate;
        }

        public void setEndDate(Date endDate) {
            this.endDate = endDate;
        }

        public QueryFilter endDate(Date endDate) {
            this.endDate = endDate;
            return this;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public QueryFilter uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }
    }

    static List<ResultStore> getActives(Map<String, String> setupData) {
        return StreamSupport.stream(ServiceLoader.load(ResultStore.class).spliterator(), false) //
            .filter(resultStore -> resultStore.isActive(setupData)) //
            .collect(Collectors.toList());
    }

    static ResultStore getActiveFromId(String id, Map<String, String> setupData) {
        List<ResultStore> resultStores = //
            getActives(setupData).stream().filter(
                resultStore -> resultStore.getProviderId().equalsIgnoreCase(id)) //
                .collect(Collectors.toList());

        // warning if more than one with same id?
        return resultStores.isEmpty() ? null : resultStores.get(0);
    }
}
