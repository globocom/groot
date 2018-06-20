package com.globocom.grou.groot.entities.properties;

//@formatter:off
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

    /**
     * Number of threads. If absent, uses Runtime.getRuntime().availableProcessors()
     */
    String THREADS              = "threads";

    /**
     * Initial Warm Up iterations (statistical ignored)
     */
    String WARMUP_ITERATIONS    = "warmupIterations";

    /**
     * Number of iterations. It's ignored if durationTimeMillis is defined
     */
    String ITERATIONS           = "iterations";

    /**
     * Number of concurrent users. If omitid, it's equal "numConn" DIV "connsPerUser"
     */
    String USERS                = "users";

    /**
     * Number of connections per user. Default = 1
     */
    String CONNS_PER_USER    = "connsPerUser";

    /**
     * Number of resource trees requested per second, or zero for maximum request rate
     */
    String RESOURCE_RATE        = "resourceRate";

    /**
     * The rate ramp up period in seconds, or zero for no ramp up
     */
    String RATE_RAMPUP_PERIOD   = "rateRampUpPeriod";

    /**
     * Number od NIO selectors (IO channels)
     */
    String NIO_SELECTORS        = "numberOfNIOselectors";

    /**
     * [INTERNAL] Maximum requests queued
     */
    String MAX_REQUESTS_QUEUED  = "maxRequestsQueued";

    /**
     * Connection blocking?
     */
    String BLOCKING             = "blocking";

    /**
     * Idle timeout (in ms)
     */
    String IDLE_TIMEOUT         = "idleTimeout";
}
//@formatter:on