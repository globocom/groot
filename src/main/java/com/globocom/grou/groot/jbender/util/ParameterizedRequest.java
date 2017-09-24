package com.globocom.grou.groot.jbender.util;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("unchecked")
public class ParameterizedRequest extends HttpEntityEnclosingRequestBase {

    private static final String GROOT_USERAGENT = "Grou$Groot/1.0";

    private final String method;
    private final HttpClientContext context;

    public ParameterizedRequest(final Map<String, Object> properties) throws Exception {
        final URI uri = URI.create(Optional.ofNullable((String) properties.get("uri")).orElseThrow(() -> new IllegalArgumentException("uri property undefined")));
        this.method = Optional.ofNullable((String) properties.get("method")).orElse(HttpMethod.GET.name());
        setURI(uri);
        Optional.ofNullable((Map<String, String>) properties.get("headers")).orElse(Collections.emptyMap()).forEach(this::setHeader);
        setHeader(HttpHeaders.USER_AGENT, GROOT_USERAGENT);
        if (method.matches("(POST|PUT|PATCH)")) {
            String body = Optional.ofNullable((String) properties.get("body")).orElseThrow(() -> new IllegalArgumentException("body property undefined"));
            if (body.isEmpty()) throw new IllegalArgumentException("body is empty");
            setEntity(new ByteArrayEntity(body.getBytes(Charset.defaultCharset())));
        }
        final String auth = (String) properties.get("auth");
        if (auth != null) {
            context = HttpClientContext.create();
            String[] authArray = auth.split(":");
            if (authArray.length > 1) {
                String login = authArray[0];
                String password = authArray[1];
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(new AuthScope(uri.getHost(), uri.getPort()), new UsernamePasswordCredentials(login, password));
                AuthCache authCache = new BasicAuthCache();
                BasicScheme basicAuth = new BasicScheme();
                authCache.put(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()), basicAuth);
                context.setCredentialsProvider(credentialsProvider);
                context.setAuthCache(authCache);
            }
        } else {
            context = null;
        }
    }

    @Override
    public String getMethod() {
        return method;
    }

    public HttpClientContext getContext() {
        return context;
    }
}
