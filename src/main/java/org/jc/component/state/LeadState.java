package org.jc.component.state;

import com.openai.models.chat.completions.ChatCompletionMessageParam;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class LeadState implements State {
    private String name;
    private String role;
    private String model;
    private String prompt;
    private String workDir;
    private List<ChatCompletionMessageParam> messages;
    private ReentrantLock shutdownLock;
    private ReentrantLock planLock;

    public LeadState() {
    }

    public LeadState(String name, String role, String model, String prompt, String workDir
            , List<ChatCompletionMessageParam> messages, ReentrantLock shutdownLock, ReentrantLock planLock) {
        this.name = name;
        this.role = role;
        this.model = model;
        this.prompt = prompt;
        this.workDir = workDir;
        this.messages = messages;
        this.shutdownLock = shutdownLock == null ? new ReentrantLock() : shutdownLock;
        this.planLock = planLock == null ? new ReentrantLock() : planLock;
    }

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String role;
        private String model;
        private String prompt;
        private String workDir;
        private ReentrantLock shutdownLock;
        private ReentrantLock planLock;
        private List<ChatCompletionMessageParam> messages;


        public Builder name(String name) {
            this.name = name;
            return this;
        }


        public Builder role(String role) {
            this.role = role;
            return this;
        }


        public Builder model(String model) {
            this.model = model;
            return this;
        }


        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder workDir(String workDir) {
            this.workDir = workDir;
            return this;
        }


        public Builder messages(List<ChatCompletionMessageParam> messages) {
            this.messages = messages;
            return this;
        }

        public Builder shutdownLock(ReentrantLock shutdownLock) {
            this.shutdownLock = shutdownLock;
            return this;
        }

        public Builder planLock(ReentrantLock planLock) {
            this.planLock = planLock;
            return this;
        }

        public LeadState build() {
            return new LeadState(name, role, model, prompt, workDir, messages, shutdownLock, planLock);
        }
    }
}
