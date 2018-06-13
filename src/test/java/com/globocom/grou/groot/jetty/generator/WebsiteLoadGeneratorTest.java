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
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.junit.After;

import java.io.IOException;

public abstract class WebsiteLoadGeneratorTest {
    protected Resource resource;
    protected Server server;
    protected ServerConnector connector;
    protected Scheduler scheduler;
    protected StatisticsHandler serverStats;

    public WebsiteLoadGeneratorTest() {
        // A dump of the resources needed by the webtide.com website.
        HttpFields headers = new HttpFields();
        headers.put("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:52.0) Gecko/20100101 Firefox/52.0");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.5");
        headers.put("Cookie", "__utma=124097164.2025215041.1465995519.1483973120.1485461487.58; __utmz=124097164.1480932641.29.9.utmcsr=localhost:8080|utmccn=(referral)|utmcmd=referral|utmcct=/; wp-settings-3=editor%3Dhtml%26wplink%3D1%26post_dfw%3Doff%26posts_list_mode%3Dlist; wp-settings-time-3=1483536385; wp-settings-time-4=1485794804; wp-settings-4=editor%3Dhtml; _ga=GA1.2.2025215041.1465995519; wordpress_google_apps_login=30a7b62f9ae5db1653367cafa3accacd; PHPSESSID=r8rr7hnl7kttpq40q7bkbcn5c2; ckon1703=sject1703_bfc34a0618c85; JCS_INENREF=; JCS_INENTIM=1489507850637; _gat=1");
        resource = new Resource("http://localhost/",
                new Resource("http://localhost/styles.css").requestHeaders(headers),
                new Resource("http://localhost/pagenavi-css.css").requestHeaders(headers),
                new Resource("http://localhost/style.css").requestHeaders(headers),
                new Resource("http://localhost/genericicons.css").requestHeaders(headers),
                new Resource("http://localhost/font-awesome.min.css").requestHeaders(headers),
                new Resource("http://localhost/jquery.js").requestHeaders(headers),
                new Resource("http://localhost/jquery-migrate.min.js").requestHeaders(headers),
                new Resource("http://localhost/picturefill.min.js").requestHeaders(headers),
                new Resource("http://localhost/jscripts.php").requestHeaders(headers),
                new Resource("http://localhost/cropped-WTLogo-2.png").requestHeaders(headers),
                new Resource("http://localhost/pexels-photo-40120-1.jpeg").requestHeaders(headers),
                new Resource("http://localhost/Keyboard.jpg").requestHeaders(headers),
                new Resource("http://localhost/Jetty-Code-2x.jpg").requestHeaders(headers),
                new Resource("http://localhost/rocket.png").requestHeaders(headers),
                new Resource("http://localhost/aperture2.png").requestHeaders(headers),
                new Resource("http://localhost/dev.png").requestHeaders(headers),
                new Resource("http://localhost/jetty-avatar.png").requestHeaders(headers),
                new Resource("http://localhost/megaphone.png").requestHeaders(headers),
                new Resource("http://localhost/jquery.form.min.js").requestHeaders(headers),
                new Resource("http://localhost/scripts.js").requestHeaders(headers),
                new Resource("http://localhost/jquery.circle2.min.js").requestHeaders(headers),
                new Resource("http://localhost/jquery.circle2.swipe.min.js").requestHeaders(headers),
                new Resource("http://localhost/waypoints.min.js").requestHeaders(headers),
                new Resource("http://localhost/jquery.counterup.min.js").requestHeaders(headers),
                new Resource("http://localhost/navigation.min.js").requestHeaders(headers),
                new Resource("http://localhost/spacious-custom.min.js").requestHeaders(headers),
                new Resource("http://localhost/jscripts-ftr-min.js").requestHeaders(headers),
                new Resource("http://localhost/wp-embed.min.js").requestHeaders(headers),
                new Resource("http://localhost/wp-emoji-release.min.js").requestHeaders(headers),
                new Resource("http://localhost/fontawesome-webfont.woff2").requestHeaders(headers)
        ).requestHeaders(headers);
    }

    protected void prepareServer(ConnectionFactory connectionFactory, Handler handler) throws Exception {
        server = new Server();
        connector = new ServerConnector(server, connectionFactory);
        server.addConnector(connector);

        // The request log ensures that the request
        // is inspected how an application would do.
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        NCSARequestLog requestLog = new NCSARequestLog() {
            @Override
            public void write(String log) throws IOException {
                // Do not write the log.
            }
        };
        requestLog.setExtended(true);
        requestLog.setLogCookies(true);
        requestLog.setLogLatency(true);
        requestLog.setLogServer(true);
        requestLogHandler.setRequestLog(requestLog);
        serverStats = new StatisticsHandler();

        server.setHandler(requestLogHandler);
        requestLogHandler.setHandler(serverStats);
        serverStats.setHandler(handler);

        scheduler = new ScheduledExecutorScheduler();
        server.addBean(scheduler, true);

        server.start();
    }

    protected LoadGenerator.Builder prepareLoadGenerator(HTTPClientTransportBuilder clientTransportBuilder) {
        return new LoadGenerator.Builder()
                .threads(1)
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .resource(resource)
                .scheduler(scheduler);
    }

    @After
    public void dispose() throws Exception {
        if (server != null) {
            server.stop();
        }
    }
}
