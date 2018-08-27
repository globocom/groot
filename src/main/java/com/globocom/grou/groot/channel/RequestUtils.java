package com.globocom.grou.groot.channel;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;

import com.globocom.grou.groot.test.properties.AuthProperty;
import com.globocom.grou.groot.test.properties.BaseProperty;
import com.globocom.grou.groot.test.properties.RequestProperty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.HttpConversionUtil;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RequestUtils {

    private static final Log LOGGER = LogFactory.getLog(RequestUtils.class);

    public static FullHttpRequest[] convertPropertyToHttpRequest(final BaseProperty property, final AtomicReference<String> scheme) {
        final TreeSet<RequestProperty> requestsProperties = requestsProperty(property);
        property.setRequests(requestsProperty(property));
        property.setUri(null);
        property.setMethod(null);
        property.setHeaders(null);

        LOGGER.info(property);

        final FullHttpRequest[] requests = new FullHttpRequest[requestsProperties.size()];
        int requestId = 0;
        for (RequestProperty requestProperty: requestsProperties) {
            final URI uri = URI.create(requestProperty.getUri());
            if (scheme.get() == null) {
                scheme.set(uri.getScheme());
            }
            final HttpHeaders headers = new DefaultHttpHeaders()
                .add(HOST, uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""))
                .add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), convertSchemeIfNecessary(uri.getScheme()));
            Optional.ofNullable(requestProperty.getHeaders()).orElse(Collections.emptyMap()).forEach(headers::add);
            AuthProperty authProperty = Optional.ofNullable(requestProperty.getAuth()).orElse(new AuthProperty());
            final String credentials = authProperty.getCredentials();
            if (credentials != null && !credentials.isEmpty()) {
                headers.add(HttpHeaderNames.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(Charset.defaultCharset())));
            }

            HttpMethod method = HttpMethod.valueOf(requestProperty.getMethod());
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            final String bodyStr = requestProperty.getBody();
            ByteBuf body = bodyStr != null && !bodyStr.isEmpty() ?
                Unpooled.copiedBuffer(bodyStr.getBytes(Charset.defaultCharset())) : Unpooled.buffer(0);

            requests[requestId] = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path, body, headers, new DefaultHttpHeaders());
            requestId++;
        }
        return requests;
    }

    private static TreeSet<RequestProperty> requestsProperty(BaseProperty properties) {
        RequestProperty singleRequestProperties = new RequestProperty();
        String uriStr = properties.getUri();
        boolean singleRequest;
        if (singleRequest = (uriStr != null && !uriStr.isEmpty())) {
            singleRequestProperties.setOrder(0);
            singleRequestProperties.setUri(uriStr);
            singleRequestProperties.setMethod(properties.getMethod());
            singleRequestProperties.setBody(properties.getBody());
            singleRequestProperties.setAuth(properties.getAuth());
            singleRequestProperties.setHeaders(properties.getHeaders());
            singleRequestProperties.setSaveCookies(properties.getSaveCookies());
        }
        return singleRequest ? new TreeSet<RequestProperty>(){{add(singleRequestProperties);}} : properties.getRequests();
    }

    private static String convertSchemeIfNecessary(String scheme) {
        return scheme.replace("h2c", "https").replace("h2", "http");
    }

}
