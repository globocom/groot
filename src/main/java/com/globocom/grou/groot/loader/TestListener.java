package com.globocom.grou.groot.loader;

import com.globo.grou.groot.generator.Resource;
import com.globocom.grou.groot.monit.MonitorService;
import org.eclipse.jetty.client.api.Request;

public class TestListener extends Request.Listener.Adapter implements Resource.NodeListener, Resource.OnContentListener {

    private final MonitorService monitorService;
    private final long start;

    public TestListener(final MonitorService monitorService, long start) {
        this.monitorService = monitorService;
        this.start = start;
    }

    @Override
    public void onResourceNode(Resource.Info info) {
        monitorService.sendStatus(String.valueOf(info.getStatus()), start);
        monitorService.sendResponseTime(start);
    }

    @Override
    public void onContent(int remaining) {
        monitorService.sendSize(remaining);
    }

    @Override
    public void onFailure(Request request, Throwable failure) {
        monitorService.fail(failure, start);
    }
}
