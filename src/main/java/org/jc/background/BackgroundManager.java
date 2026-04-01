package org.jc.background;

import com.alibaba.fastjson2.JSON;
import org.jc.Commands;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 后台任务管理器
 */
public class BackgroundManager {

    // 任务存储：taskId -> TaskInfo
    private final ConcurrentHashMap<String, TaskInfo> tasks = new ConcurrentHashMap<>();

    // 通知队列（对象）
    private final Queue<TaskNotification> notificationQueue = new LinkedList<>();
    private final ReentrantLock lock = new ReentrantLock();

    // ===================== 启动后台任务 =====================
    public String run(String args) {
        String command = JSON.parseObject(args).getString("command");
        String taskId = UUID.randomUUID().toString().substring(0, 8);

        // ✅ 使用对象，不再用 Map
        TaskInfo task = new TaskInfo("running", null, command);
        tasks.put(taskId, task);

        // 异步执行命令
        Commands.execAsync(command, 300_000, false, new Commands.CommandCallback() {
            @Override
            public void onSuccess(Commands.CommandResult result) {
                updateTask(taskId, "completed", result.getOutput());
            }

            @Override
            public void onFail(Commands.CommandResult result) {
                String status = result.isTimeout() ? "timeout" : "error";
                updateTask(taskId, status, result.getOutput());
            }
        });

        return "后台任务 " + taskId + " 开始: " + truncate(command, 80);
    }

    // ===================== 更新任务结果 =====================
    private void updateTask(String taskId, String status, String output) {
        TaskInfo task = tasks.get(taskId);
        if (task == null) return;

        output = truncate(output == null ? "" : output.trim(), 50000);
        if (output.isEmpty()) output = "(无输出)";

        // ✅ 对象赋值
        task.setStatus(status);
        task.setResult(output);

        // ✅ 加入通知队列（对象）
        lock.lock();
        try {
            TaskNotification notif = new TaskNotification(
                    taskId,
                    status,
                    truncate(task.getCommand(), 80),
                    truncate(output, 500)
            );
            notificationQueue.offer(notif);
        } finally {
            lock.unlock();
        }
    }

    // ===================== 查询任务 =====================
    public String check(String args) {
        String taskId = JSON.parseObject(args).getString("taskId");
        if (taskId != null && !taskId.isBlank()) {
            TaskInfo task = tasks.get(taskId);
            if (task == null) {
                return "错误: 未知任务 " + taskId;
            }
            return "[" + task.getStatus() + "] " + truncate(task.getCommand(), 60)
                    + "\n" + (task.getResult() == null ? "(运行中)" : task.getResult());
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, TaskInfo> entry : tasks.entrySet()) {
            String tid = entry.getKey();
            TaskInfo task = entry.getValue();
            lines.add(tid + ": [" + task.getStatus() + "] " + truncate(task.getCommand(), 60));
        }
        return lines.isEmpty() ? "没有后台任务" : String.join("\n", lines);
    }

    // ===================== 清空通知 =====================
    public String drainNotifications() {
        lock.lock();
        try {
            List<TaskNotification> list = new ArrayList<>(notificationQueue);
            notificationQueue.clear();
            // 拼接成字符串：[bg:taskId] status: result
            return list.stream()
                    .map(n -> String.format("[bg:%s] %s: %s",
                            n.getTaskId(),
                            n.getStatus(),
                            n.getResult()))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        } finally {
            lock.unlock();
        }
    }

    // ===================== 工具 =====================
    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}