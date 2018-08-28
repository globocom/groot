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

package com.globocom.grou.groot.channel;

import static com.globocom.grou.groot.channel.handler.CookieStorageHandler.COOKIE_STORAGE_ENABLED_ATTR;

import com.globocom.grou.groot.test.properties.BaseProperty;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BootstrapBuilder {

    private static final Log LOGGER = LogFactory.getLog(BootstrapBuilder.class);

    public static final AttributeKey<Integer> IDLE_TIMEOUT_ATTR = AttributeKey.newInstance("idleTimeout");

    private static final boolean IS_MAC = isMac();
    private static final boolean IS_LINUX = isLinux();

    public static Bootstrap build(final BaseProperty property) {
        int threads = property.getThreads();
        int idleTimeout = property.getIdleTimeout();
        int connectTimeout = property.getConnectTimeout();
        boolean cookieStorage = property.getSaveCookies();

        LOGGER.info("Using " + threads + " thread(s)");

        final EventLoopGroup group = getEventLoopGroup(threads);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.
            group(group).
            channel(getSocketChannelClass()).
            attr(IDLE_TIMEOUT_ATTR, idleTimeout).
            attr(COOKIE_STORAGE_ENABLED_ATTR, cookieStorage).
            option(ChannelOption.SO_KEEPALIVE, true).
            option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout).
            option(ChannelOption.TCP_NODELAY, true).
            option(ChannelOption.SO_REUSEADDR, true);
        return bootstrap;
    }

    private static Class<? extends Channel> getSocketChannelClass() {
        // @formatter:off
        return IS_MAC   ? KQueueSocketChannel.class :
               IS_LINUX ? EpollSocketChannel.class :
                          NioSocketChannel.class;
        // @formatter:on
    }

    public static EventLoopGroup getEventLoopGroup(int numCores) {
        // @formatter:off
        return IS_MAC   ? new KQueueEventLoopGroup(numCores) :
               IS_LINUX ? new EpollEventLoopGroup(numCores) :
                          new NioEventLoopGroup(numCores);
        // @formatter:on
    }

    private static boolean isMac() {
        boolean result = getOS().startsWith("mac");
        if (result) {
            LOGGER.warn("Hello. I'm Mac");
        }
        return result;
    }

    private static boolean isLinux() {
        boolean result = getOS().startsWith("linux");
        if (result) {
            LOGGER.warn("Hello. I'm Linux");
        }
        return result;
    }

    private static String getOS() {
        return System.getProperty("os.name", "UNDEF").toLowerCase();
    }
}
