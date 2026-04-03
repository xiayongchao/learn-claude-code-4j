package org.jc.task;

public class TaskClaimResult {
    private final boolean error;
    private final String info;

    private TaskClaimResult(boolean error, String info) {
        this.error = error;
        this.info = info;
    }

    public static TaskClaimResult of(boolean error, String info) {
        return new TaskClaimResult(error, info);
    }

    public boolean isError() {
        return error;
    }

    public String getInfo() {
        return info;
    }
}
