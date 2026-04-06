package org.jc.component.state;

import com.openai.models.chat.completions.ChatCompletionMessageParam;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class TeammateState implements State {
    private String name;
    private String role;
    private String model;
    private String prompt;
    private String userPrompt;
    private Integer maxLoopTimes;
    private String workDir;
    private String lead;
    private boolean shutdown;
    private boolean idle;
    private long idleTimeout;
    private long pollInterval;
    private List<ChatCompletionMessageParam> messages;
    private ReentrantLock shutdownLock;
    private ReentrantLock planLock;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

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

    @Override
    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
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

    public List<ChatCompletionMessageParam> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatCompletionMessageParam> messages) {
        this.messages = messages;
    }

    @Override
    public ReentrantLock getShutdownLock() {
        return shutdownLock;
    }

    public void setShutdownLock(ReentrantLock shutdownLock) {
        this.shutdownLock = shutdownLock;
    }

    @Override
    public ReentrantLock getPlanLock() {
        return planLock;
    }

    public void setPlanLock(ReentrantLock planLock) {
        this.planLock = planLock;
    }
}
