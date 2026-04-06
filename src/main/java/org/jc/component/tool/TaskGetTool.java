package org.jc.component.tool;

import com.alibaba.fastjson2.JSON;
import com.google.inject.Inject;
import org.jc.component.tool.args.TaskGetToolArgs;
import org.jc.component.task.Tasks;

public class TaskGetTool extends BaseTool<TaskGetToolArgs> {
    private final Tasks tasks;

    @Inject
    public TaskGetTool(Tasks tasks) {
        super("taskGet", TaskGetToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "taskGet",
                        "description": "根据taskId获取任务的详细信息",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "taskId": {
                                    "type": "integer"
                                }
                            },
                            "required": [
                                "taskId"
                            ]
                        }
                    }
                }
                """);
        this.tasks = tasks;
    }

    @Override
    public String doCall(TaskGetToolArgs arguments) {
        return JSON.toJSONString(this.tasks.readTask(arguments.getTaskId()));
    }
}
