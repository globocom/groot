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

package com.globocom.grou.groot.jetty.generator;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;

import java.net.URI;
import java.util.*;

/**
 * <p>A resource node to be fetched by the load generator.</p>
 * <p>Resources are organized in a tree, and the load generator
 * fetches parent resources before children resources, while sibling
 * resources are sent in parallel.</p>
 * <p>A Resource without a path is a <em>group</em> resource,
 * only meant to group resources together (for example to fetch all
 * JavaScript resources as a group before fetching the image resources).</p>
 */
public class Resource {
    public static final String RESPONSE_LENGTH = "JLG-Response-Length";

    private final List<Resource> resources = new ArrayList<>();
    private final HttpFields requestHeaders = new HttpFields();
    private String method = HttpMethod.GET.asString();
    private URI uri = null;
    private int requestLength = 0;
    private int responseLength = 0;
    private byte[] content = new byte[0];
    private int order = Math.abs(new Random().nextInt());

    public Resource() {
        this((URI) null);
    }

    public Resource(Resource... resources) {
        this((URI) null, resources);
    }

    public Resource(URI uri) {
        this(uri, new Resource[0]);
    }

    public Resource(URI uri, Resource... resources) {
        this.uri = uri;
        if (resources != null) {
            Collections.addAll(this.resources, resources);
        }
    }

    public Resource(String uriStr, Resource... resources) {
        this(URI.create(uriStr), resources);
    }

    public Resource(String uriStr) {
        this.uri = URI.create(uriStr);
    }

    /**
     * @param method the HTTP method to use to fetch the resource
     * @return this Resource
     */
    public Resource method(String method) {
        this.method = method;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        if (uri == null) return null;
        final String path = uri.getPath();
        return path == null || path.isEmpty() ? "/" : path;
    }

    public URI getUri() {
        return uri;
    }

    public Resource setUri(URI uri) {
        this.uri = uri;
        return this;
    }

    /**
     * @param requestLength the request content length
     * @return this Resource
     */
    public Resource requestLength(int requestLength) {
        this.requestLength = requestLength;
        return this;
    }

    public int getRequestLength() {
        return requestLength;
    }

    /**
     * Adds a request header.
     *
     * @param name the header name
     * @param value the header value
     * @return this Resource
     */
    public Resource requestHeader(String name, String value) {
        this.requestHeaders.add( name, value);
        return this;
    }

    /**
     * Adds request headers.
     *
     * @param headers the request headers
     * @return this Resource
     */
    public Resource requestHeaders(HttpFields headers) {
        this.requestHeaders.addAll( headers);
        return this;
    }

    public HttpFields getRequestHeaders() {
        return requestHeaders;
    }

    /**
     * <p>Sets the response content length.</p>
     * <p>The response content length is conveyed as the request header
     * specified by {@link #RESPONSE_LENGTH}. Servers may ignore it
     * or honor it, responding with the desired response content length.</p>
     *
     * @param responseLength the response content length
     * @return this Resource
     */
    public Resource responseLength(int responseLength) {
        this.responseLength = responseLength;
        return this;
    }

    public int getResponseLength() {
        return responseLength;
    }

    public byte[] content() {
        return this.content;
    }

    public Resource setContent(byte[] content) {
        this.content = content;
        return this;
    }

    public int getOrder() {
        return order;
    }

    public Resource setOrder(int order) {
        this.order = order;
        return this;
    }

    public boolean hasBody() {
        return content.length > 0;
    }

    /**
     * @return the children resources
     */
    public List<Resource> getResources() {
        return resources;
    }

    public Resource addResource(Resource resource) {
        resources.add(resource);
        return this;
    }

    /**
     * Finds a descendant resource by path and query with the given URI.
     *
     * @param uri the URI with the path and query to find
     * @return a matching descendant resource, or null if there is no match
     */
    public Resource findDescendant(URI uri) {
        String pathQuery = uri.getRawPath();
        String query = uri.getRawQuery();
        if (query != null) {
            pathQuery += "?" + query;
        }
        for (Resource child : getResources()) {
            if (pathQuery.equals(child.getPath())) {
                return child;
            } else {
                Resource result = child.findDescendant(uri);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * @return the number of descendant resource nodes
     */
    public int descendantCount() {
        return descendantCount(this);
    }

    private int descendantCount(Resource resource) {
        int result = 1;
        for (Resource child : resource.getResources()) {
            result += descendantCount(child);
        }
        return result;
    }

    /**
     * @return a new Info object
     */
    public Info newInfo() {
        return new Info(this);
    }

    @Override
    public String toString() {
        return String.format("%s@%h{%s %s - %d/%d}",
                getClass().getSimpleName(),
                hashCode(),
                getMethod(),
                getPath(),
                getRequestLength(),
                getResponseLength());
    }

    /**
     * Value class containing information per-resource and per-request.
     */
    public static class Info {
        private final Resource resource;
        private long requestTime;
        private long latencyTime;
        private long responseTime;
        private long treeTime;
        private long contentLength;
        private boolean pushed;
        private int status;
        private HttpVersion version;

        private Info(Resource resource) {
            this.resource = resource;
        }

        /**
         * @return the corresponding Resource
         */
        public Resource getResource() {
            return resource;
        }

        /**
         * @return the time, in ns, the request is being sent
         */
        public long getRequestTime() {
            return requestTime;
        }

        public void setRequestTime(long requestTime) {
            this.requestTime = requestTime;
        }

        /**
         * @return the time, in ns, the response first byte arrived
         */
        public long getLatencyTime() {
            return latencyTime;
        }

        public void setLatencyTime(long latencyTime) {
            this.latencyTime = latencyTime;
        }

        /**
         * @return the time, in ns, the response last byte arrived
         */
        public long getResponseTime() {
            return responseTime;
        }

        public void setResponseTime(long responseTime) {
            this.responseTime = responseTime;
        }

        /**
         * @return the time, in ns, the last byte of the whole resource tree arrived
         */
        public long getTreeTime() {
            return treeTime;
        }

        public void setTreeTime(long treeTime) {
            this.treeTime = treeTime;
        }

        /**
         * @param bytes the number of bytes to add to the response content length
         */
        public void addContent(int bytes) {
            contentLength += bytes;
        }

        /**
         * @return the response content length in bytes
         */
        public long getContentLength() {
            return contentLength;
        }

        /**
         * @return whether the resource has been pushed by the server
         */
        public boolean isPushed() {
            return pushed;
        }

        public void setPushed(boolean pushed) {
            this.pushed = pushed;
        }

        /**
         * @return the response HTTP status code
         */
        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        /**
         * @return the response version
         */
        public HttpVersion getVersion() {
            return version;
        }

        public Info setVersion(HttpVersion version) {
            this.version = version;
            return this;
        }
    }

    public interface Listener extends EventListener {
    }

    /**
     * <p>Listener for node events.</p>
     * <p>Node events are emitted for non-warmup resource requests that completed successfully.</p>
     */
    public interface NodeListener extends Listener {
        void onResourceNode(Info info);
    }

    /**
     * <p>Listener for tree node events.</p>
     * <p>Tree node events are emitted for the non-warmup root resource.</p>
     */
    public interface TreeListener extends Listener {
        void onResourceTree(Info info);
    }

    public interface OnContentListener extends Listener {
        void onContent(int remaining);
    }
}
