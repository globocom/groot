package com.globocom.grou.groot.entities.properties;

public interface GrootProperties {

    /**
     * URI request [MANDATORY]
     */
    String URI_REQUEST          = "uri";

    /**
     * number of simultaneous connections required [MANDATORY]
     */
    String NUM_CONN             = "numConn";

    /**
     * Duration (in milliseconds) [MANDATORY]
     */
    String DURATION_TIME_MILLIS = "durationTimeMillis";

    /**
     * Enable saving and using cookies
     */
    String SAVE_COOKIES         = "saveCookies";

    /**
     * Authentication properties. Contains credentials & preemptive properties
     */
    String AUTH                 = "auth";

    /**
     * Credentials (format login:password)
     */
    String CREDENTIALS          = "credentials";

    /**
     * Send preemptively the credentials or wait 401
     */
    String PREEMPTIVE           = "preemptive";

    /**
     * Body request
     */
    String BODY                 = "body";

    /**
     * Headers request
     */
    String HEADERS              = "headers";

    /**
     * Method request
     */
    String METHOD               = "method";

    /**
     * Loaders requisited
     */
    String PARALLEL_LOADERS     = "parallelLoaders";

    /**
     * Connection timeout
     */
    String CONNECTION_TIMEOUT   = "connectTimeout";

    /**
     * Enable keepalive (default true)
     */
    String KEEP_ALIVE           = "keepAlive";

    /**
     * Enable follow redirect (default false)
     */
    String FOLLOW_REDIRECT      = "followRedirect";

    /**
     * Insert delay between requests (in milliseconds)
     */
    String FIXED_DELAY          = "fixedDelay";

    /**
     * Target list to monitoring
     */
    String MONIT_TARGETS        = "monitTargets";

    /**
     * List of properties per test to enable multiple requests
     */
    String REQUESTS             = "requests";

    /**
     * Request Order (if using multiples per test)
     */
    String ORDER                = "order";
}
