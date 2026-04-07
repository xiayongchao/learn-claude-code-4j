package org.jc.component.state;

import com.openai.models.chat.completions.ChatCompletionMessageParam;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class LeadState extends BaseState {
    public LeadState() {
    }

    public LeadState(String name, String role, String model, String prompt, String workDir
            , List<ChatCompletionMessageParam> messages, ReentrantLock shutdownLock, ReentrantLock planLock
            , ReentrantLock claimTaskLock) {
        this.name = name;
        this.role = role;
        this.model = model;
        this.prompt = prompt;
        this.workDir = workDir;
        this.messages = messages;
        this.shutdownLock = shutdownLock == null ? new ReentrantLock() : shutdownLock;
        this.planLock = planLock == null ? new ReentrantLock() : planLock;
        this.claimTaskLock = claimTaskLock == null ? new ReentrantLock() : claimTaskLock;
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
        private ReentrantLock claimTaskLock;
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

        public Builder claimTaskLock(ReentrantLock claimTaskLock) {
            this.claimTaskLock = claimTaskLock;
            return this;
        }

        public LeadState build() {
            return new LeadState(name, role, model, prompt, workDir, messages, shutdownLock, planLock, claimTaskLock);
        }
    }
}
