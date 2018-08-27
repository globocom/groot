package com.globocom.grou.groot.test.properties;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface PropertiesUtils {

    Logger LOGGER = LoggerFactory.getLogger(PropertiesUtils.class);

    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void check(final BaseProperty properties) throws IllegalArgumentException {
        Integer numConn = properties.getNumConn();
        if (!(numConn != null && numConn > 0)) {
            throw new IllegalArgumentException("numConn undefined or less than 1");
        }
        String uri = properties.getUri();
        if (uri != null && !uri.isEmpty()) {
            checkUri(uri);
            checkBody(properties.getMethod(), properties.getBody());
        } else {
            if (properties.getRequests() == null || properties.getRequests().isEmpty()) {
                throw new IllegalArgumentException("URI is Null and 'requests' is Empty");
            }
            for (RequestProperty requestProperty: properties.getRequests()) {
                checkUri(requestProperty.getUri());
                checkBody(requestProperty.getMethod(), requestProperty.getBody());
            }
        }
    }

    static void checkBody(String method, String body) {
        if (method != null && method.matches("(POST|PUT|PATCH)")) {
            if (body == null || body.isEmpty()) {
                throw new IllegalArgumentException("body is empty and mandatory (method: " + method + ")");
            }
        }
    }

    static void checkUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            throw new IllegalArgumentException("URI undefined");
        }
        URI uriTested = URI.create(uri);
        String schema = uriTested.getScheme();
        if (!schema.matches("(http[s]?|ws[s]?|h2[c]?)")) {
            throw new IllegalArgumentException("The URI scheme, of the URI " + uri + ", "
                + "must be equal (ignoring case) to ‘http’, ‘https’, 'h2', h2c', ‘ws’, or ‘wss’");
        }
    }
}