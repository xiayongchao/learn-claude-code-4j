package org.jc.component.tool;

import com.alibaba.fastjson2.JSON;
import com.google.inject.Inject;
import org.jc.component.task.Task;
import org.jc.component.task.Tasks;

import java.util.*;

public class TaskListTool extends BaseTool<Void> {
    private final Tasks tasks;

    @Inject
    public TaskListTool(Tasks tasks) {
        super("taskList", Void.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "taskList",
                        "description": "列出所有任务的状态摘要",
                        "parameters": {
                            "type": "object",
                            "properties": {}
                        }
                    }
                }
                """);
        this.tasks = tasks;
    }

    @Override
    public String doCall(Void arguments) {
        List<Task> tasks = this.tasks.list();

        if (tasks == null || tasks.isEmpty()) {
            return "暂无任务";
        }

        List<String> lines = new ArrayList<>();
        for (Task t : tasks) {
            String status = t.getStatus();
            String marker = switch (status) {
                case "pending" -> "[ ]";
                case "in_progress" -> "[>]";
                case "completed" -> "[x]";
                default -> "[?]";
            };

            List<Integer> blockedBy = t.getBlockedBy();
            String blocked = (blockedBy != null && !blockedBy.isEmpty())
                    ? " (依赖: " + JSON.toJSONString(blockedBy) + ")"
                    : "";
            lines.add(marker + " " + t.getTaskId() + ": " + t.getSubject() + blocked);
        }

        return String.join("\n", lines);
    }
}
