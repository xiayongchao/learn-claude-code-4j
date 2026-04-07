package org.jc.component.tool.args;

public class WorktreeRemoveToolArgs {
    private String name;
    private Boolean force;
    private Boolean completeTask;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }

    public Boolean getCompleteTask() {
        return completeTask;
    }

    public void setCompleteTask(Boolean completeTask) {
        this.completeTask = completeTask;
    }
}