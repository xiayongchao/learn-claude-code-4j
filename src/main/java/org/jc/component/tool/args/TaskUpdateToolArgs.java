package org.jc.component.tool.args;

import java.util.List;

public class TaskUpdateToolArgs {
    private int taskId;
    private String status;
    private String owner;
    private List<Integer> addBlockedBy;
    private List<Integer> addBlocks;

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public List<Integer> getAddBlockedBy() {
        return addBlockedBy;
    }

    public void setAddBlockedBy(List<Integer> addBlockedBy) {
        this.addBlockedBy = addBlockedBy;
    }

    public List<Integer> getAddBlocks() {
        return addBlocks;
    }

    public void setAddBlocks(List<Integer> addBlocks) {
        this.addBlocks = addBlocks;
    }
}
