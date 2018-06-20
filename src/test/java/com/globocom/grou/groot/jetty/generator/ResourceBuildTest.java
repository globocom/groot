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

import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.stream.Collectors;

public class ResourceBuildTest {

    @Test
    public void simple_build() throws Exception {
        Resource resourceProfile = new Resource(new Resource("http://localhost/index.html"));

        Assert.assertEquals(1, resourceProfile.getResources().size());
        Assert.assertEquals("/index.html", resourceProfile.getResources().get(0).getPath());
        Assert.assertEquals("GET", resourceProfile.getResources().get(0).getMethod());
    }

    @Test
    public void simple_two_resources() throws Exception {
        Resource resourceProfile = new Resource(
            new Resource("http://localhost/index.html"),
            new Resource("http://localhost/beer.html").method(HttpMethod.POST.asString())
        );

        Assert.assertEquals(2, resourceProfile.getResources().size());
        Assert.assertEquals("/index.html", resourceProfile.getResources().get(0).getPath());
        Assert.assertEquals("GET", resourceProfile.getResources().get(0).getMethod());
        Assert.assertEquals("/beer.html", resourceProfile.getResources().get(1).getPath());
        Assert.assertEquals("POST", resourceProfile.getResources().get(1).getMethod());
    }

    @Test
    public void website_profile() throws Exception {
        Resource sample = new Resource(
            new Resource("http://localhost/index.html",
                new Resource("http://localhost/style.css",
                    new Resource("http://localhost/logo.gif"),
                    new Resource("http://localhost/spacer.png")
                ),
                new Resource("http://localhost/fancy.css"),
                new Resource("http://localhost/script.js",
                    new Resource("http://localhost/library.js"),
                    new Resource("http://localhost/morestuff.js")
                ),
                new Resource("http://localhost/anotherScript.js"),
                new Resource("http://localhost/iframeContents.html"),
                new Resource("http://localhost/moreIframeContents.html"),
                new Resource("http://localhost/favicon.ico")
            ));

        web_profile_assert(sample);
    }

    @Test
    public void website_profile_with_xml() throws Exception {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("website_profile.xml")) {
            Resource sample = (Resource) new XmlConfiguration(inputStream).configure();
            web_profile_assert(sample);
        }
    }

    public static String read(InputStream input) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input, Charset.defaultCharset()))) {
            return buffer.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    @Test
    public void website_profile_with_groovy()
        throws Exception {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("website_profile.groovy")) {
            Resource sample = (Resource) evaluateScript(read(inputStream));
            web_profile_assert(sample);
        }
    }


    public Object evaluateScript(String script) throws Exception {
        CompilerConfiguration config = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
        config.setDebug(true);
        config.setVerbose(true);
        GroovyShell interpreter = new GroovyShell(config);
        return interpreter.evaluate(script);
    }

    protected void web_profile_assert(Resource sample) {
        /*
        GET index.html
                style.css
                    logo.gif
                    spacer.png
                fancy.css
                script.js
                    library.js
                    morestuff.js
                anotherScript.js
                iframeContents.html
                moreIframeContents.html
                favicon.ico
        */

        Assert.assertEquals(1, sample.getResources().size());
        Assert.assertEquals(7, sample.getResources().get(0).getResources().size());
        Assert.assertEquals("/style.css", sample.getResources().get(0).getResources().get(0).getPath());
        Assert.assertEquals("/logo.gif", sample.getResources().get(0)
            .getResources().get(0).getResources().get(0).getPath());
        Assert.assertEquals("/spacer.png", sample.getResources().get(0)
            .getResources().get(0).getResources().get(1).getPath());
        Assert.assertEquals(2, sample.getResources().get(0)
            .getResources().get(0).getResources().size());
        Assert.assertEquals(2, sample.getResources().get(0)
            .getResources().get(2).getResources().size());
        Assert.assertEquals("/library.js", sample.getResources().get(0)
            .getResources().get(2).getResources().get(0).getPath());
        Assert.assertEquals("/morestuff.js", sample.getResources().get(0)
            .getResources().get(2).getResources().get(1).getPath());
        Assert.assertEquals("/anotherScript.js", sample.getResources().get(0)
            .getResources().get(3).getPath());
        Assert.assertEquals("/moreIframeContents.html", sample.getResources().get(0)
            .getResources().get(5).getPath());
        Assert.assertEquals("/favicon.ico", sample.getResources().get(0)
            .getResources().get(6).getPath());
    }
}
