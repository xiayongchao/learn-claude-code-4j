package org.jc.background;

/**
 * 任务完成通知
 */
public class TaskNotification {
    private String taskId;
    private String status;
    private String command;
    private String result;

    public TaskNotification(String taskId, String status, String command, String result) {
        this.taskId = taskId;
        this.status = status;
        this.command = command;
        this.result = result;
    }

    // getter
    public String getTaskId() {
        return taskId;
    }

    public String getStatus() {
        return status;
    }

    public String getCommand() {
        return command;
    }

    public String getResult() {
        return result;
    }
}
