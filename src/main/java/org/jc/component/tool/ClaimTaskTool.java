package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.tool.args.ClaimTaskToolArgs;
import org.jc.component.enums.TaskStatus;
import org.jc.component.state.States;
import org.jc.component.task.Task;
import org.jc.component.task.Tasks;

import java.util.List;
import java.util.Objects;

public class ClaimTaskTool extends BaseTool<ClaimTaskToolArgs> {
    private final Tasks tasks;

    @Inject
    public ClaimTaskTool(Tasks tasks) {
        super("claimTask", ClaimTaskToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "claimTask",
                        "description": "从任务板认领一个任务（通过taskId）。",
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
    public String doCall(ClaimTaskToolArgs arguments) {
        int taskId = arguments.getTaskId();
        String owner = States.get().getName();

        States.get().getClaimTaskLock().lock();
        try {
            List<Task> taskList = this.tasks.list();
            if (taskList != null) {
                for (Task task : taskList) {
                    if (Objects.equals(name(), task.getOwner()) && TaskStatus.IN_PROGRESS.is(task.getStatus())) {
                        return String.format("错误：任务 %s 还没有处理完成，无法认领新任务，请继续处理", task.getTaskId());
                    }
                }
            }

            Task task = this.tasks.readTask(taskId);
            if (task == null) {
                return String.format("错误：任务 %s 不存在", taskId);
            }

            // 2. 已被领取
            String existingOwner = task.getOwner();
            if (existingOwner != null && !existingOwner.isBlank()) {
                return String.format("错误：任务 %s 已被 %s 认领", taskId, existingOwner);
            }

            // 3. 状态不是 pending
            String status = task.getStatus();
            if (!TaskStatus.PENDING.is(status)) {
                return String.format("错误：任务 %s 无法认领，因其当前状态为 %s", taskId, status);
            }

            // 4. 被其他任务阻塞
            List<Integer> blockedBy = task.getBlockedBy();
            if (blockedBy != null && !blockedBy.isEmpty()) {
                return String.format("错误：任务 %s 被其他任务阻塞，暂时无法认领", taskId);
            }

            // 更新任务信息
            task.setOwner(owner);
            task.setStatus(TaskStatus.IN_PROGRESS.getValue());

            // 写回文件
            this.tasks.writeTask(task);

            return String.format("已为 %s 认领任务 %s", owner, task.getTaskId());
        } catch (Exception e) {
            return String.format("错误：认领任务失败 - %s", e.getMessage());
        } finally {
            States.get().getClaimTaskLock().unlock();
        }
    }
}
