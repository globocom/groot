import com.globocom.grou.groot.jetty.generator.Resource

return new Resource("http://localhost/index.html",
        new Resource("http://localhost/css/bootstrap.css",
                new Resource("http://localhost/css/bootstrap-theme.css"),
                new Resource("http://localhost/js/jquery-3.1.1.min.js"),
                new Resource("http://localhost/js/jquery-3.1.1.min.js"),
                new Resource("http://localhost/js/jquery-3.1.1.min.js"),
                new Resource("http://localhost/js/jquery-3.1.1.min.js")
        ),
        new Resource("http://localhost/js/bootstrap.js",
                new Resource("http://localhost/js/bootstrap.js"),
                new Resource("http://localhost/js/bootstrap.js"),
                new Resource("http://localhost/js/bootstrap.js")
        ),
        new Resource("http://localhost/hello"),
        new Resource("http://localhost/dump.jsp?wine=foo&foo=bar"),
        new Resource("http://localhost/not_here.html"),
        new Resource("http://localhost/hello?name=foo"),
        new Resource("http://localhost/hello?name=foo"),
        new Resource("http://localhost/upload").method("PUT").requestLength(8192)
)
