package org.jc.background;

/**
 * 后台任务实体
 */
public class TaskInfo {
    private String status;    // running / completed / timeout / error
    private String result;    // 输出结果
    private String command;   // 执行的命令

    public TaskInfo(String status, String result, String command) {
        this.status = status;
        this.result = result;
        this.command = command;
    }

    // getter + setter
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
