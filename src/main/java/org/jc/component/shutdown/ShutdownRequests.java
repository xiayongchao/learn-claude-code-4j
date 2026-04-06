package org.jc.component.shutdown;

import com.google.common.collect.Maps;

import java.util.Map;

public class ShutdownRequests {
    private Map<String, ShutdownRequest> requests = Maps.newConcurrentMap();

    public void put(String requestId, ShutdownRequest request) {
        this.requests.put(requestId, request);
    }

    public ShutdownRequest get(String requestId) {
        return this.requests.get(requestId);
    }
}
