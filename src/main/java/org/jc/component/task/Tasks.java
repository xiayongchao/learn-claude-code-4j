package org.jc.component.task;

import com.alibaba.fastjson2.JSON;
import org.jc.component.enums.TaskStatus;
import org.jc.component.state.States;
import org.jc.component.util.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Tasks {
    // ===================== 保存配置 =====================
    public void writeTask(Task task) {
        try {
            int taskId = task.getTaskId();
            Path taskPath = FileUtils.resolve(States.get().getWorkDir()
                    , String.format("tasks/task_%s.json", taskId), true);
            FileUtils.write(taskPath, task);
        } catch (Exception e) {
            throw new RuntimeException("保存任务失败", e);
        }
    }

    public Task readTask(int taskId) {
        try {
            Path taskPath = FileUtils.resolve(States.get().getWorkDir()
                    , String.format("tasks/task_%s.json", taskId), true);
            return FileUtils.read(taskPath, Task.class);
        } catch (Exception e) {
            throw new RuntimeException("读取任务失败", e);
        }
    }

    /**
     * 查询列表
     *
     * @return
     */
    public List<Task> list() {
        List<Task> tasks = new ArrayList<>();
        try {
            Path tasksDir = FileUtils.resolve(States.get().getWorkDir()
                    , "tasks", true);
            Files.list(tasksDir)
                    .sorted()
                    .filter(p -> p.getFileName().toString().matches("task_\\d+\\.json"))
                    .forEach(p -> {
                        try {
                            tasks.add(JSON.parseObject(Files.readString(p), Task.class));
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
        return tasks;
    }


    /**
     * 清理依赖：任务完成后，从所有其他任务的 blockedBy 中移除
     *
     * @param completedId
     */
    public void clearDependency(int completedId) {
        List<Task> taskList = this.list();
        if (taskList == null) {
            return;
        }

        for (Task task : taskList) {
            List<Integer> blockedBy = task.getBlockedBy();
            if (blockedBy != null && blockedBy.contains(completedId)) {
                blockedBy.remove(Integer.valueOf(completedId));
                task.setBlockedBy(blockedBy);
                this.writeTask(task);
            }
        }
    }


    /**
     * 获取任务 ID
     *
     * @return
     */
    public int nextTaskId() {
        return this.maxTaskId() + 1;
    }

    /**
     * 获取任务 ID
     *
     * @return
     */
    public int maxTaskId() {
        try {
            Path tasksDir = FileUtils.resolve(States.get().getWorkDir()
                    , "tasks", true);
            List<Integer> ids = Files.list(tasksDir)
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(String::valueOf)
                    .filter(name -> name.startsWith("task_") && name.endsWith(".json"))
                    .map(name -> name.replace("task_", "").replace(".json", ""))
                    .filter(this::isInt)
                    .map(Integer::parseInt)
                    .toList();

            return ids.isEmpty() ? 0 : Collections.max(ids);
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public Task getUnclaimedTask() {
        List<Task> tasks = this.scanUnclaimedTasks();
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        return tasks.get(0);
    }

    public List<Task> scanUnclaimedTasks() {
        List<Task> unclaimed = new ArrayList<>();

        // 遍历所有 task_*.json 文件（排序 + 匹配文件名）
        List<Task> taskList = this.list();
        if (taskList == null) {
            return unclaimed;
        }

        for (Task task : taskList) {
            // 匹配条件：status=pending + 无owner + 无blockedBy
            String status = task.getStatus();
            String owner = task.getOwner();
            List<Integer> blockedBy = task.getBlockedBy();

            if (TaskStatus.PENDING.is(status)
                    && (owner == null || owner.isBlank())
                    && (blockedBy == null || blockedBy.isEmpty())) {
                unclaimed.add(task);
            }
        }

        return unclaimed;
    }

    public boolean claimTask(int taskId, String owner) {
        try {
            Task task = this.readTask(taskId);
            if (task == null) {
                return false;
            }

            // 2. 已被领取
            String existingOwner = task.getOwner();
            if (existingOwner != null && !existingOwner.isBlank()) {
                return false;
            }

            // 3. 状态不是 pending
            String status = task.getStatus();
            if (!TaskStatus.PENDING.is(status)) {
                return false;
            }

            // 4. 被其他任务阻塞
            List<Integer> blockedBy = task.getBlockedBy();
            if (blockedBy != null && !blockedBy.isEmpty()) {
                return false;
            }

            // 更新任务信息
            task.setOwner(owner);
            task.setStatus(TaskStatus.IN_PROGRESS.getValue());

            // 写回文件
            this.writeTask(task);

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
