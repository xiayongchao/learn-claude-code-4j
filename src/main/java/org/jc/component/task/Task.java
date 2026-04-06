package org.jc.component.task;

import java.util.List;

public class Task {
    private int taskId;
    private String subject;
    private String description;
    private String status;
    private List<Integer> blockedBy;
    private List<Integer> blocks;
    private String owner;

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Integer> getBlockedBy() {
        return blockedBy;
    }

    public void setBlockedBy(List<Integer> blockedBy) {
        this.blockedBy = blockedBy;
    }

    public List<Integer> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<Integer> blocks) {
        this.blocks = blocks;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}
