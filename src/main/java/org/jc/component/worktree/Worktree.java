package org.jc.component.worktree;

public class Worktree {
    private String name;
    private String path;
    private String branch;
    private Integer taskId;
    private String status;
    private double createdAt;
    private double removedAt;
    private double keptAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }

    public double getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(double removedAt) {
        this.removedAt = removedAt;
    }

    public double getKeptAt() {
        return keptAt;
    }

    public void setKeptAt(double keptAt) {
        this.keptAt = keptAt;
    }
}