package org.jc.component.worktree;

import com.alibaba.fastjson2.JSON;
import org.jc.component.state.States;
import org.jc.component.util.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventBus {
    public Path eventLogPath() {
        try {
            return FileUtils.resolve(
                    States.get().getWorkDir(),
                    ".worktrees/events.jsonl",
                    true, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize EventBus", e);
        }
    }

    public void emit(String event, Map<String, Object> task, Map<String, Object> worktree, String error) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", event);
            payload.put("ts", System.currentTimeMillis() / 1000.0);
            payload.put("task", task != null ? task : new HashMap<>());
            payload.put("worktree", worktree != null ? worktree : new HashMap<>());
            if (error != null && !error.isBlank()) {
                payload.put("error", error);
            }

            String line = JSON.toJSONString(payload);
            Files.writeString(this.eventLogPath(), line + "\n", java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    public void emit(String event, Map<String, Object> task, Map<String, Object> worktree) {
        emit(event, task, worktree, null);
    }

    public void emit(String event) {
        emit(event, new HashMap<>(), new HashMap<>());
    }

    public List<WorktreeEvent> listRecent(int limit) {
        List<WorktreeEvent> events = new ArrayList<>();
        try {
            Path eventLogPath = this.eventLogPath();
            if (!Files.exists(eventLogPath)) {
                return events;
            }

            String content = Files.readString(eventLogPath);
            String[] lines = content.split("\n");
            int n = Math.max(1, Math.min(limit, 200));
            int start = Math.max(0, lines.length - n);

            for (int i = start; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    WorktreeEvent e = JSON.parseObject(line, WorktreeEvent.class);
                    events.add(e);
                } catch (Exception ignored) {
                    WorktreeEvent parseError = new WorktreeEvent();
                    parseError.setEvent("parse_error");
                    events.add(parseError);
                }
            }
        } catch (Exception ignored) {
        }
        return events;
    }

    public String listRecentJson(int limit) {
        return JSON.toJSONString(listRecent(limit));
    }
}