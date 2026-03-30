package org.jc.todo;

import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.List;

public class TodoManager {
    public static final String PENDING = "pending";
    public static final String IN_PROGRESS = "in_progress";
    public static final String COMPLETED = "completed";
    private List<TodoItem> items = new ArrayList<>();

    public String update(String json) {
        return this.update(JSON.parseArray(JSON.parseObject(json).getString("items"), TodoItem.class));
    }

    public String update(List<TodoItem> items) {
        // 最多20条
        if (items.size() > 20) {
            throw new IllegalArgumentException("最多允许20条待办事项");
        }

        List<TodoItem> validated = new ArrayList<>();
        int inProgressCount = 0;

        for (int i = 0; i < items.size(); i++) {
            TodoItem item = items.get(i);

            // 读取并清洗数据
            String text = item.getText() == null ? "" : item.getText().trim();
            String status = item.getStatus() == null ? PENDING : item.getStatus().toLowerCase();
            String itemId = (item.getId() == null || item.getId().isBlank())
                    ? String.valueOf(i + 1)
                    : item.getId();

            // 文本不能为空
            if (text.isBlank()) {
                throw new IllegalArgumentException("待办事项 " + itemId + ": 文本不能为空");
            }

            // 状态校验
            if (!List.of(PENDING, IN_PROGRESS, COMPLETED).contains(status)) {
                throw new IllegalArgumentException("待办事项 " + itemId + ": 状态无效: " + status);
            }

            // 统计进行中
            if (status.equals(IN_PROGRESS)) {
                inProgressCount++;
            }

            validated.add(new TodoItem(itemId, text, status));
        }

        // 只能有一个进行中
        if (inProgressCount > 1) {
            throw new IllegalArgumentException("同一时间仅允许一个任务处于进行中状态");
        }

        this.items = validated;
        return render();
    }

    public String render() {
        if (items.isEmpty()) {
            return "暂无待办事项";
        }

        List<String> lines = new ArrayList<>();
        for (TodoItem item : items) {
            String marker = switch (item.getStatus()) {
                case "pending" -> "[ ]";
                case "in_progress" -> "[>]";
                case "completed" -> "[x]";
                default -> "[ ]";
            };

            lines.add(marker + " #" + item.getId() + ": " + item.getText());
        }

        long doneCount = items.stream()
                .filter(item -> COMPLETED.equals(item.getStatus()))
                .count();

        lines.add("\n(" + doneCount + "/" + items.size() + " 已完成)");
        return String.join("\n", lines);
    }

    public List<TodoItem> getItems() {
        return items;
    }

    public static void main(String[] args) {
        TodoManager manager = new TodoManager();

        List<TodoItem> items = new ArrayList<>();
        items.add(new TodoItem("1", "写Java代码", IN_PROGRESS));
        items.add(new TodoItem("2", "测试功能", PENDING));

        String result = manager.update(items);
        System.out.println(result);
    }
}