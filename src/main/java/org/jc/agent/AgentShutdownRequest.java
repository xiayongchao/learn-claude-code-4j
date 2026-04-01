package org.jc.agent;

public class AgentShutdownRequest {
    private String target;
    private String status;

    public AgentShutdownRequest() {
    }

    public AgentShutdownRequest(String target, String status) {
        this.target = target;
        this.status = status;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
