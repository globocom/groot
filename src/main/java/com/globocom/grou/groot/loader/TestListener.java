package com.globocom.grou.groot.loader;

import com.globo.grou.groot.generator.Resource;
import com.globocom.grou.groot.monit.MonitorService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpVersion;

public class TestListener extends Request.Listener.Adapter implements Resource.NodeListener, Resource.OnContentListener {

    private static final Log LOGGER = LogFactory.getLog(TestListener.class);

    private final MonitorService monitorService;
    private final long start;

    public TestListener(final MonitorService monitorService, long start) {
        this.monitorService = monitorService;
        this.start = start;
    }

    @Override
    public void onResourceNode(Resource.Info info) {
        if (info != null) {
            monitorService.sendStatus(String.valueOf(info.getStatus()), start);
            monitorService.sendResponseTime(start);
        }
    }

    @Override
    public void onContent(int remaining) {
        monitorService.sendSize(remaining);
    }

    @Override
    public void onFailure(Request request, Throwable failure) {
        try {
            if (failure != null) monitorService.fail(failure, start);
        } catch (NullPointerException ignore) {
            // ignored
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
