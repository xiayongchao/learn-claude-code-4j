package org.jc.component.worktree;

import java.util.Map;

public class WorktreeEvent {
    private String event;
    private double ts;
    private Map<String, Object> task;
    private Map<String, Object> worktree;
    private String error;

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public double getTs() {
        return ts;
    }

    public void setTs(double ts) {
        this.ts = ts;
    }

    public Map<String, Object> getTask() {
        return task;
    }

    public void setTask(Map<String, Object> task) {
        this.task = task;
    }

    public Map<String, Object> getWorktree() {
        return worktree;
    }

    public void setWorktree(Map<String, Object> worktree) {
        this.worktree = worktree;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}