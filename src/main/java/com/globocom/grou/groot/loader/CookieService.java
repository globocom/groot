package com.globocom.grou.groot.loader;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CookieService {

    private final AtomicBoolean saveCookies = new AtomicBoolean(false);
    private final List<Cookie> cookies = new CopyOnWriteArrayList<>();
    private final AtomicBoolean cookiesLoaded = new AtomicBoolean(false);
    private final AtomicBoolean cookiesApplied = new AtomicBoolean(false);

    public void loadCookies(final HttpHeaders headers) {
        if (saveCookies.get() && !cookiesLoaded.getAndSet(true)) {
            List<String> cookiesFromResponse;
            if ((cookiesFromResponse = headers.getAll(HttpHeaderNames.SET_COOKIE)) != null) {
                cookies.addAll(cookiesFromResponse.stream()
                    .map(ClientCookieDecoder.LAX::decode).collect(Collectors.toSet()));
            }
        }
    }

    public void applyCookies(final HttpHeaders headers) {
        if (saveCookies.get() && cookiesLoaded.get() && !cookiesApplied.getAndSet(true)) {
            cookies.forEach(cookie ->
                headers.add(HttpHeaderNames.COOKIE, ClientCookieEncoder.LAX.encode(cookies)));
        }
    }

    public synchronized void saveCookies(boolean save) {
        saveCookies.set(save);
    }

    public synchronized void reset() {
        cookies.clear();
        cookiesLoaded.set(false);
        cookiesApplied.set(false);
    }

}
