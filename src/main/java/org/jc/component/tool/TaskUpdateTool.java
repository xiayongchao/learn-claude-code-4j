package org.jc.component.tool;

import com.alibaba.fastjson2.JSON;
import com.google.inject.Inject;
import org.jc.component.tool.args.TaskUpdateToolArgs;
import org.jc.component.enums.TaskStatus;
import org.jc.component.task.Task;
import org.jc.component.task.Tasks;

import java.util.*;
import java.util.stream.Collectors;

public class TaskUpdateTool extends BaseTool<TaskUpdateToolArgs> {
    private final Tasks tasks;

    @Inject
    public TaskUpdateTool(Tasks tasks) {
        super("taskUpdate", TaskUpdateToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "taskUpdate",
                        "description": "更新任务的状态或依赖关系",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "taskId": {
                                    "type": "integer"
                                },
                                "status": {
                                    "type": "string",
                                    "enum": [\"%s\"]
                                },
                                "addBlockedBy": {
                                    "type": "array",
                                    "items": {
                                        "type": "integer"
                                    }
                                },
                                "addBlocks": {
                                    "type": "array",
                                    "items": {
                                        "type": "integer"
                                    }
                                }
                            },
                            "required": [
                                "taskId"
                            ]
                        }
                    }
                }
                """.formatted(Arrays.stream(TaskStatus.values()).map(TaskStatus::getValue)
                .collect(Collectors.joining("\", \""))));
        this.tasks = tasks;
    }

    @Override
    public String doCall(TaskUpdateToolArgs arguments) {
        int taskId = arguments.getTaskId();
        String status = arguments.getStatus();
        List<Integer> addBlockedBy = arguments.getAddBlockedBy();
        List<Integer> addBlocks = arguments.getAddBlocks();

        Task task = this.tasks.readTask(taskId);

        // 更新状态
        if (status != null && !status.isBlank()) {
            if (!TaskStatus.has(status)) {
                throw new IllegalArgumentException("未知状态: " + status);
            }

            task.setStatus(status);

            // 完成时自动清理所有依赖
            if (TaskStatus.COMPLETED.is(status)) {
                this.tasks.clearDependency(taskId);
            }
        }

        // 添加依赖
        if (addBlockedBy != null && !addBlockedBy.isEmpty()) {
            Set<Integer> set = new HashSet<>(task.getBlockedBy());
            set.addAll(addBlockedBy);
            task.setBlockedBy(new ArrayList<>(set));
        }

        // 添加阻塞（双向更新）
        if (addBlocks != null && !addBlocks.isEmpty()) {
            Set<Integer> blocks = new HashSet<>(task.getBlocks());
            blocks.addAll(addBlocks);
            task.setBlocks(new ArrayList<>(blocks));

            for (Integer blockedId : addBlocks) {
                try {
                    Task blocked = this.tasks.readTask(blockedId);
                    List<Integer> blockedBy = blocked.getBlockedBy();
                    if (!blockedBy.contains(taskId)) {
                        blockedBy.add(taskId);
                        blocked.setBlockedBy(blockedBy);
                        this.tasks.writeTask(blocked);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        this.tasks.writeTask(task);
        return JSON.toJSONString(task);
    }
}
