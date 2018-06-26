import com.globocom.grou.groot.jetty.generator.common.Resource

return new Resource(
        new Resource("http://localhostindex.html",
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
        new Resource("http://localhost/favicon.ico"))
)
