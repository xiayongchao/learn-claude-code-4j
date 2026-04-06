package org.jc.component.tool;

import com.alibaba.fastjson2.JSON;
import com.google.inject.Inject;
import org.jc.component.enums.TaskStatus;
import org.jc.component.tool.args.TaskCreateToolArgs;
import org.jc.component.task.Task;
import org.jc.component.task.Tasks;

import java.util.ArrayList;

public class TaskCreateTool extends BaseTool<TaskCreateToolArgs> {
    private final Tasks tasks;

    @Inject
    public TaskCreateTool(Tasks tasks) {
        super("taskCreate", TaskCreateToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "taskCreate",
                        "description": "创建一个新的任务",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "subject": {
                                    "type": "string"
                                },
                                "description": {
                                    "type": "string"
                                }
                            },
                            "required": [
                                "subject"
                            ]
                        }
                    }
                }
                """);
        this.tasks = tasks;
    }

    @Override
    public String doCall(TaskCreateToolArgs arguments) {
        String subject = arguments.getSubject();
        String description = arguments.getDescription();

        Task task = new Task();
        task.setTaskId(this.tasks.nextTaskId());
        task.setOwner("");
        task.setSubject(subject);
        task.setDescription(description == null ? "" : description);
        task.setStatus(TaskStatus.PENDING.getValue());
        task.setBlockedBy(new ArrayList<>());
        task.setBlocks(new ArrayList<>());
        this.tasks.writeTask(task);
        return JSON.toJSONString(task);
    }
}
