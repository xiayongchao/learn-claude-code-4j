package org.jc.agent;

public class AgentConfig {
    private boolean enableIdlePoll;
    private boolean readInbox;
    private String shutdownResponse;
    private String workDir;

    private int idleTimeout;
    private int pollInterval;

    private AgentConfig teammateConfig;

    private AgentConfig() {

    }

    public static AgentConfig of() {
        return new AgentConfig();
    }


    public boolean isEnableIdlePoll() {
        return enableIdlePoll;
    }

    public void setEnableIdlePoll(boolean enableIdlePoll) {
        this.enableIdlePoll = enableIdlePoll;
    }

    public boolean isReadInbox() {
        return readInbox;
    }

    public void setReadInbox(boolean readInbox) {
        this.readInbox = readInbox;
    }

    public String getShutdownResponse() {
        return shutdownResponse;
    }

    public void setShutdownResponse(String shutdownResponse) {
        this.shutdownResponse = shutdownResponse;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public int getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(int pollInterval) {
        this.pollInterval = pollInterval;
    }

    public AgentConfig getTeammateConfig() {
        return teammateConfig;
    }

    public void setTeammateConfig(AgentConfig teammateConfig) {
        this.teammateConfig = teammateConfig;
    }
}
