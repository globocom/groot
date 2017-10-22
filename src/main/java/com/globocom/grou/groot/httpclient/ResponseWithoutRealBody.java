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

import io.netty.handler.codec.http.HttpHeaders;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.netty.NettyResponse;

import java.util.List;

public class ResponseWithoutRealBody extends NettyResponse {

    private final int realBodySize;

    ResponseWithoutRealBody(HttpResponseStatus status, HttpHeaders headers, List<HttpResponseBodyPart> bodyParts, int realBodySize) {
        super(status, headers, bodyParts);
        this.realBodySize = realBodySize;
    }

    public int getBodySize() {
        return realBodySize;
    }
}
