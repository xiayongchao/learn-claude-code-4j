package org.jc.task;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TaskBoard {
    private final Object claimLock = new Object();
    private final Path tasksDir;
    private int nextId;

    public TaskBoard(String tasksDir) {
        this.tasksDir = Paths.get(tasksDir);
        try {
            Files.createDirectories(this.tasksDir);
        } catch (Exception e) {
            throw new RuntimeException("创建任务目录失败", e);
        }
        this.nextId = this.findMaxId() + 1;
    }

    // 获取最大任务 ID
    private int findMaxId() {
        try {
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

    //查询列表
    public List<Task> list() {
        List<Task> tasks = new ArrayList<>();
        try {
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

    public String render(String arguments) {
        int taskId = JSON.parseObject(arguments).getIntValue("taskId");
        return JSON.toJSONString(this.loadTask(taskId));
    }

    public String renderList() {
        List<Task> tasks = this.list();

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

    // 保存任务
    private void saveTask(Task task) {
        int taskId = task.getTaskId();
        Path path = tasksDir.resolve("task_" + taskId + ".json");
        try {
            String json = JSON.toJSONString(task); // 格式化输出
            Files.writeString(path, json);
        } catch (Exception e) {
            throw new RuntimeException("保存任务失败", e);
        }
    }


    // 创建任务
    public String create(String arguments) {
        JSONObject object = JSON.parseObject(arguments);
        return create(object.getString("subject"), object.getString("description"));
    }

    public String create(String subject, String description) {
        Task task = new Task();
        task.setTaskId(nextId++);
        task.setOwner("");
        task.setSubject(subject);
        task.setDescription(description == null ? "" : description);
        task.setStatus("pending");
        task.setBlockedBy(new ArrayList<>());
        task.setBlocks(new ArrayList<>());
        this.saveTask(task);
        return JSON.toJSONString(task);
    }

    //查询单个任务
    private Task loadTask(int taskId) {
        Path path = tasksDir.resolve("task_" + taskId + ".json");
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Task " + taskId + " not found");
        }
        try {
            String content = Files.readString(path);
            return JSON.parseObject(content, Task.class);
        } catch (Exception e) {
            throw new RuntimeException("读取任务失败", e);
        }
    }

    //查询没有认领的任务

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

            if ("pending".equals(status)
                    && (owner == null || owner.isBlank())
                    && (blockedBy == null || blockedBy.isEmpty())) {
                unclaimed.add(task);
            }
        }

        return unclaimed;
    }

    public TaskClaimResult claimTask(int taskId, String owner) {
        synchronized (claimLock) {
            // 加锁
            try {
                Task task = this.loadTask(taskId);

                // 2. 已被领取
                String existingOwner = task.getOwner();
                if (existingOwner != null && !existingOwner.isBlank()) {
                    return TaskClaimResult.of(true, "错误：任务 " + taskId + " 已被 " + existingOwner + " 认领");
                }

                // 3. 状态不是 pending
                String status = task.getStatus();
                if (!"pending".equals(status)) {
                    return TaskClaimResult.of(true, "错误：任务 " + taskId + " 无法认领，因其当前状态为 " + status);
                }

                // 4. 被其他任务阻塞
                List<Integer> blockedBy = task.getBlockedBy();
                if (blockedBy != null && !blockedBy.isEmpty()) {
                    return TaskClaimResult.of(true, "错误：任务 " + taskId + " 被其他任务阻塞，暂时无法认领");
                }

                // 更新任务信息
                task.setOwner(owner);
                task.setStatus("in_progress");

                // 写回文件
                this.saveTask(task);

                return TaskClaimResult.of(false, "已为 " + owner + " 认领任务 " + taskId);
            } catch (Exception e) {
                return TaskClaimResult.of(true, "错误：认领任务失败 - " + e.getMessage());
            }
        }
    }

    //
    // 更新任务：状态、依赖、阻塞
    public String update(String arguments) {
        JSONObject object = JSON.parseObject(arguments);
        String addBlockedBy = object.getString("addBlockedBy");
        String addBlocks = object.getString("addBlocks");

        return update(object.getInteger("taskId")
                , object.getString("status")
                , addBlockedBy == null || addBlockedBy.isBlank() ? null : JSON.parseArray(addBlockedBy, Integer.class)
                , addBlocks == null || addBlocks.isBlank() ? null : JSON.parseArray(addBlocks, Integer.class)
        );
    }

    public String update(int taskId, String status,
                         List<Integer> addBlockedBy,
                         List<Integer> addBlocks) {

        Task task = this.loadTask(taskId);

        // 更新状态
        if (status != null && !status.isBlank()) {
            if (!List.of("pending", "in_progress", "completed").contains(status)) {
                throw new IllegalArgumentException("未知状态: " + status);
            }
            task.setStatus(status);

            // 完成时自动清理所有依赖
            if ("completed".equals(status)) {
                clearDependency(taskId);
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
                    Task blocked = this.loadTask(blockedId);
                    List<Integer> blockedBy = blocked.getBlockedBy();
                    if (!blockedBy.contains(taskId)) {
                        blockedBy.add(taskId);
                        blocked.setBlockedBy(blockedBy);
                        saveTask(blocked);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        saveTask(task);
        return JSON.toJSONString(task);
    }

    // 清理依赖：任务完成后，从所有其他任务的 blockedBy 中移除
    private void clearDependency(int completedId) {
        List<Task> taskList = this.list();
        if (taskList == null) {
            return;
        }

        for (Task task : taskList) {
            List<Integer> blockedBy = task.getBlockedBy();
            if (blockedBy != null && blockedBy.contains(completedId)) {
                blockedBy.remove(Integer.valueOf(completedId));
                task.setBlockedBy(blockedBy);
                this.saveTask(task);
            }
        }
    }

}
