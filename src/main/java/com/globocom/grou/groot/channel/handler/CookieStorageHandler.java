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

package com.globocom.grou.groot.channel.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

public class CookieStorageHandler extends ChannelDuplexHandler {

    private static final Set<Cookie> COOKIES = new ConcurrentSkipListSet<>();

    public static final AttributeKey<Boolean> COOKIE_STORAGE_ENABLED_ATTR = AttributeKey.newInstance("cookieStorageEnabled");

    private final Object lock = new Object();

    public static void reset() {
        synchronized (CookieStorageHandler.class) {
            COOKIES.clear();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        final Attribute<Boolean> cookieStorageEnabledAttr = ctx.channel().attr(COOKIE_STORAGE_ENABLED_ATTR);
        if (cookieStorageEnabledAttr != null && cookieStorageEnabledAttr.get()) {
            if (msg instanceof HttpResponse) {
                synchronized (lock) {
                    final HttpResponse response = (HttpResponse) msg;
                    COOKIES.addAll(response.headers()
                        .getAll(HttpHeaderNames.SET_COOKIE).stream()
                        .map(ClientCookieDecoder.LAX::decode).collect(Collectors.toCollection(HashSet::new)));
                }
            }
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        final Attribute<Boolean> cookieStorageEnabledAttr = ctx.channel().attr(COOKIE_STORAGE_ENABLED_ATTR);
        if (cookieStorageEnabledAttr != null && cookieStorageEnabledAttr.get()) {
            if (msg instanceof HttpRequest && !COOKIES.isEmpty()) {
                synchronized (lock) {
                    final HttpRequest request = (HttpRequest) msg;
                    final Set<String> cookies = new HashSet<>(request.headers().getAll(HttpHeaderNames.COOKIE));
                    cookies.add(ClientCookieEncoder.LAX.encode(COOKIES));
                    cookies.forEach(cookie -> request.headers().add(HttpHeaderNames.COOKIE, cookie));
                }
            }
        }
        super.write(ctx, msg, promise);
    }
}
