package org.jc.agent;

public class AgentConfig {
    private boolean readInbox;
    private String workDir;
    private Object trackerLock;

    private AgentConfig() {

    }

    public static AgentConfig of() {
        return new AgentConfig();
    }

    public boolean isReadInbox() {
        return readInbox;
    }

    public void setReadInbox(boolean readInbox) {
        this.readInbox = readInbox;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public Object getTrackerLock() {
        return trackerLock;
    }

    public void setTrackerLock(Object trackerLock) {
        this.trackerLock = trackerLock;
    }
}
