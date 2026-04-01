package org.jc.agent;

public class AgentConfig {
    private boolean readInbox;
    private String workDir;

    private AgentConfig() {

    }

    public static AgentConfig of() {
        return new AgentConfig();
    }

    public AgentConfig readInbox(boolean readInbox) {
        this.readInbox = readInbox;
        return this;
    }

    public AgentConfig workDir(String workDir) {
        this.workDir = workDir;
        return this;
    }


    public boolean readInbox() {
        return this.readInbox;
    }

    public String workDir() {
        return this.workDir;
    }
}
