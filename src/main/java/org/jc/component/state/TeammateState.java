package org.jc.component.state;

public class TeammateState extends BaseState {
    private String userPrompt;
    private Integer maxLoopTimes;

    private String lead;
    private boolean shutdown;
    private boolean idle;
    private long idleTimeout;
    private long pollInterval;

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }

    public Integer getMaxLoopTimes() {
        return maxLoopTimes;
    }

    public void setMaxLoopTimes(Integer maxLoopTimes) {
        this.maxLoopTimes = maxLoopTimes;
    }

    public String getLead() {
        return lead;
    }

    public void setLead(String lead) {
        this.lead = lead;
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public void setShutdown(boolean shutdown) {
        this.shutdown = shutdown;
    }

    public boolean isIdle() {
        return idle;
    }

    public void setIdle(boolean idle) {
        this.idle = idle;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public long getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(long pollInterval) {
        this.pollInterval = pollInterval;
    }
}
