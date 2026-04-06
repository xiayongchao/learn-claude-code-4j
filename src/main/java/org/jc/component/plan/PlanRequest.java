package org.jc.component.plan;

public class PlanRequest {
    private String from;
    private String plan;
    private String status;

    public PlanRequest() {
    }

    public PlanRequest(String from, String plan, String status) {
        this.from = from;
        this.plan = plan;
        this.status = status;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
