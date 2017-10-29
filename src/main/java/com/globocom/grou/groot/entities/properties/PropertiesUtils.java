package com.globocom.grou.groot.entities.properties;

import com.globocom.grou.groot.SystemEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

import static com.globocom.grou.groot.entities.properties.GrootProperties.*;

public interface PropertiesUtils {

    Logger LOGGER = LoggerFactory.getLogger(PropertiesUtils.class);

    @SuppressWarnings("unchecked")
    static HashMap<String, Object>[] extractAllRequestPropertiesOrdered(final Map<String, Object> properties) throws IllegalArgumentException {
        HashMap[] allproperties;
        try {
            Object requestsObj = properties.get(REQUESTS);
            if (requestsObj instanceof List) {
                return  ((List<Map<String, Object>>) requestsObj).stream().filter(r -> r.containsKey(ORDER))
                        .sorted(Comparator.comparingInt(r -> (Integer) r.get(ORDER))).toArray(HashMap[]::new);
            } else {
                allproperties = new HashMap[1];
                allproperties[0] = new HashMap<>(properties);
                return allproperties;
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            return new HashMap[0];
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void check(final Map<String, Object> testProperties) throws IllegalArgumentException {
        Object durationTimeMillis = testProperties.get(DURATION_TIME_MILLIS);
        if ((durationTimeMillis != null && durationTimeMillis instanceof Integer && (Integer) durationTimeMillis >= 1000)) {
            String maxTestDuration = SystemEnv.MAX_TEST_DURATION.getValue();
            if ((Integer) durationTimeMillis > Integer.parseInt(maxTestDuration)) {
                throw new IllegalArgumentException(DURATION_TIME_MILLIS + " property is greater than MAX_TEST_DURATION: " + maxTestDuration);
            }
        } else {
            throw new IllegalArgumentException(DURATION_TIME_MILLIS + " property undefined or less than 1000 ms");
        }
        Object numConn = testProperties.get(NUM_CONN);
        if (!(numConn != null && numConn instanceof Integer && (Integer) numConn > 0)) {
            throw new IllegalArgumentException(NUM_CONN + " property undefined or less than 1 conn");
        }
        HashMap[] allproperties = extractAllRequestPropertiesOrdered(testProperties);
        if (allproperties.length == 0) throw new IllegalArgumentException("Request properties is empty or invalid (is \"order\" property missing?)");
        for (HashMap properties: allproperties) {
            Object uri = properties.get(URI_REQUEST);
            if (uri == null || ((String) uri).isEmpty()) {
                throw new IllegalArgumentException(URI_REQUEST + " property undefined");
            }
            URI uriTested = URI.create((String) uri);
            String schema = uriTested.getScheme();
            if (!schema.matches("(http[s]?|ws[s]?)")) {
                throw new IllegalArgumentException("The URI scheme, of the URI " + uri + ", must be equal (ignoring case) to ‘http’, ‘https’, ‘ws’, or ‘wss’");
            }
            String method = (String) properties.get(METHOD);
            if (method != null && method.matches("(POST|PUT|PATCH)")) {
                String body = Optional.ofNullable((String) properties.get(BODY)).orElseThrow(() -> new IllegalArgumentException(BODY + " property undefined"));
                if (body.isEmpty()) throw new IllegalArgumentException("body is empty");
            }
        }
    }
}
