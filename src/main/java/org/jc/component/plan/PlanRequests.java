package org.jc.component.plan;

import com.google.common.collect.Maps;

import java.util.Map;

public class PlanRequests {
    private Map<String, PlanRequest> requests = Maps.newConcurrentMap();

    public void put(String requestId, PlanRequest request) {
        this.requests.put(requestId, request);
    }

    public PlanRequest get(String requestId) {
        return this.requests.get(requestId);
    }

}
