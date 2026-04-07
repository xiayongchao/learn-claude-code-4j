package org.jc.component.tool.args;

public class TaskBindWorktreeToolArgs {
    private Integer taskId;
    private String worktree;
    private String owner;

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public String getWorktree() {
        return worktree;
    }

    public void setWorktree(String worktree) {
        this.worktree = worktree;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}