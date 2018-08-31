package com.globocom.grou.groot.channel.handler;

import com.globocom.grou.groot.monit.MonitorService;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.concurrent.ConcurrentLinkedQueue;

public interface RequestQueueStamper extends ChannelHandler {

    void offer(long startRequest);

    int MAX_RESPONSE_STATUS = 599;

    default void sendMetrics(
        int statusCode,
        final ConcurrentLinkedQueue<Long> requestQueueTimes,
        final MonitorService monitorService) {

        if (statusCode >= HttpResponseStatus.CONTINUE.code() && statusCode <= MAX_RESPONSE_STATUS) {
            final Long tempStartRequest;
            final long startRequest = (tempStartRequest = requestQueueTimes.poll()) != null ? tempStartRequest : 0L;
            monitorService.sendStatus(String.valueOf(statusCode), startRequest);
            monitorService.sendResponseTime(startRequest);
        }
    }

}
