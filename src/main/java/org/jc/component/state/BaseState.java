package org.jc.component.state;

import com.openai.models.chat.completions.ChatCompletionMessageParam;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class BaseState implements State {
    protected String name;
    protected String role;
    protected String model;
    protected String prompt;
    protected String workDir;
    protected List<ChatCompletionMessageParam> messages;
    protected ReentrantLock shutdownLock;
    protected ReentrantLock planLock;
    protected ReentrantLock claimTaskLock;


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

    @Override
    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public List<ChatCompletionMessageParam> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatCompletionMessageParam> messages) {
        this.messages = messages;
    }

    public ChatCompletionMessageParam getLastMessage() {
        return messages == null || messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public ReentrantLock getShutdownLock() {
        return shutdownLock;
    }

    public void setShutdownLock(ReentrantLock shutdownLock) {
        this.shutdownLock = shutdownLock;
    }

    public ReentrantLock getPlanLock() {
        return planLock;
    }

    public void setPlanLock(ReentrantLock planLock) {
        this.planLock = planLock;
    }

    public ReentrantLock getClaimTaskLock() {
        return claimTaskLock;
    }

    public void setClaimTaskLock(ReentrantLock claimTaskLock) {
        this.claimTaskLock = claimTaskLock;
    }
}
