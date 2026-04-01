package org.jc.task;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class TaskManager {

    private final Path taskDir;
    private int nextId;

    // 构造方法：传入目录字符串
    public TaskManager(String tasksDir) {
        this.taskDir = Paths.get(tasksDir);
        try {
            Files.createDirectories(this.taskDir);
        } catch (Exception e) {
            throw new RuntimeException("创建任务目录失败", e);
        }
        this.nextId = this.findMaxId() + 1;
    }

    // 获取最大任务 ID
    private int findMaxId() {
        try {
            List<Integer> ids = Files.list(taskDir)
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(String::valueOf)
                    .filter(name -> name.startsWith("task_") && name.endsWith(".json"))
                    .map(name -> name.replace("task_", "").replace(".json", ""))
                    .filter(this::isInt)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            return ids.isEmpty() ? 0 : Collections.max(ids);
        } catch (Exception e) {
            return 0;
        }
    }

    // 加载任务
    private JSONObject loadTask(int taskId) {
        Path path = taskDir.resolve("task_" + taskId + ".json");
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Task " + taskId + " not found");
        }
        try {
            String content = Files.readString(path);
            return JSON.parseObject(content);
        } catch (Exception e) {
            throw new RuntimeException("读取任务失败", e);
        }
    }

    // 保存任务
    private void saveTask(JSONObject task) {
        int id = task.getIntValue("id");
        Path path = taskDir.resolve("task_" + id + ".json");
        try {
            String json = JSON.toJSONString(task); // 格式化输出
            Files.writeString(path, json);
        } catch (Exception e) {
            throw new RuntimeException("保存任务失败", e);
        }
    }

    // 创建任务
    public String create(String args) {
        JSONObject object = JSON.parseObject(args);
        return create(object.getString("subject"), object.getString("description"));
    }

    public String create(String subject, String description) {
        JSONObject task = new JSONObject();
        task.put("id", nextId);
        task.put("subject", subject);
        task.put("description", description == null ? "" : description);
        task.put("status", "pending");
        task.put("blockedBy", new ArrayList<>());
        task.put("blocks", new ArrayList<>());
        task.put("owner", "");

        saveTask(task);
        nextId++;
        return JSON.toJSONString(task);
    }

    // 获取单个任务
    public String get(String args) {
        return get(Integer.parseInt(args));
    }

    public String get(int taskId) {
        JSONObject task = loadTask(taskId);
        return JSON.toJSONString(task);
    }

    // 更新任务：状态、依赖、阻塞
    public String update(String args) {
        JSONObject object = JSON.parseObject(args);
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

        JSONObject task = loadTask(taskId);

        // 更新状态
        if (status != null && !status.isBlank()) {
            if (!List.of("pending", "in_progress", "completed").contains(status)) {
                throw new IllegalArgumentException("未知状态: " + status);
            }
            task.put("status", status);

            // 完成时自动清理所有依赖
            if ("completed".equals(status)) {
                clearDependency(taskId);
            }
        }

        // 添加依赖
        if (addBlockedBy != null && !addBlockedBy.isEmpty()) {
            Set<Integer> set = new HashSet<>(task.getList("blockedBy", Integer.class));
            set.addAll(addBlockedBy);
            task.put("blockedBy", new ArrayList<>(set));
        }

        // 添加阻塞（双向更新）
        if (addBlocks != null && !addBlocks.isEmpty()) {
            Set<Integer> blocks = new HashSet<>(task.getList("blocks", Integer.class));
            blocks.addAll(addBlocks);
            task.put("blocks", new ArrayList<>(blocks));

            for (Integer blockedId : addBlocks) {
                try {
                    JSONObject blocked = loadTask(blockedId);
                    List<Integer> blockedBy = blocked.getList("blockedBy", Integer.class);
                    if (!blockedBy.contains(taskId)) {
                        blockedBy.add(taskId);
                        blocked.put("blockedBy", blockedBy);
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
        try {
            Files.list(taskDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("task_"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            JSONObject task = JSON.parseObject(content);
                            List<Integer> blockedBy = task.getList("blockedBy", Integer.class);
                            if (blockedBy != null && blockedBy.contains(completedId)) {
                                blockedBy.remove(Integer.valueOf(completedId));
                                task.put("blockedBy", blockedBy);
                                saveTask(task);
                            }
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    // 列出所有任务（和 Python 格式完全一致）
    public String list() {
        List<JSONObject> tasks = new ArrayList<>();
        try {
            Files.list(taskDir)
                    .sorted()
                    .filter(p -> p.getFileName().toString().matches("task_\\d+\\.json"))
                    .forEach(p -> {
                        try {
                            tasks.add(JSON.parseObject(Files.readString(p)));
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception e) {
            return "No tasks.";
        }

        if (tasks.isEmpty()) {
            return "No tasks.";
        }

        List<String> lines = new ArrayList<>();
        for (JSONObject t : tasks) {
            String status = t.getString("status");
            String marker = switch (status) {
                case "pending" -> "[ ]";
                case "in_progress" -> "[>]";
                case "completed" -> "[x]";
                default -> "[?]";
            };

            List<Integer> blockedBy = t.getList("blockedBy", Integer.class);
            String blocked = (blockedBy != null && !blockedBy.isEmpty())
                    ? " (依赖: " + blockedBy + ")"
                    : "";

            lines.add(marker + " #" + t.getIntValue("id") + ": " + t.getString("subject") + blocked);
        }

        return String.join("\n", lines);
    }

    private boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
