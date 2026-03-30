# S03TodoWrite - 任务规划：没有计划的 Agent 会迷失方向

## 核心理念

**"没有计划的 Agent 走哪算哪" -- 先列步骤再动手，完成率翻倍。**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S03TodoWrite.java
- 原版：https://github.com/shareAI-lab/learn-claude-code
- 上篇：[S02ToolUse - 工具使用](./S02ToolUse.md)

## 上篇回顾

上篇文章我们实现了 4 个工具：`bash`、`readFile`、`writeFile`、`editFile`，并通过 Dispatch Map 实现工具分发。

## 问题

多步任务中，模型会丢失进度：
- 重复做过的事
- 跳步
- 跑偏

对话越长越严重：工具结果不断填满上下文，系统提示的影响力逐渐被稀释。一个 10 步重构可能做完 1-3 步就开始即兴发挥，因为 4-10 步已经被挤出注意力了。

## 解决方案

```
+--------+      +-------+      +---------+
|  User  | ---> |  LLM  | ---> | Tools   |
| prompt |      |       |      | + todo  |
+--------+      +---+---+      +----+----+
                    ^                |
                    |   tool_result  |
                    +----------------+
                          |
              +-----------+-----------+
              | TodoManager state     |
              | [ ] task A            |
              | [>] task B  <- doing |
              | [x] task C            |
              +-----------------------+
                          |
              if roundsSinceTodo >= 3:
                inject <reminder>
```

**两个关键机制：**
1. **TodoManager** - 模型自己维护任务状态
2. **Nag Reminder** - 3 轮不更新待办就提醒

## Java 实现详解

### 1. TodoManager：带状态的任务管理器

```java
public class TodoManager {
    public static final String PENDING = "pending";
    public static final String IN_PROGRESS = "in_progress";
    public static final String COMPLETED = "completed";
    
    private List<TodoItem> items = new ArrayList<>();

    public String update(String json) {
        return this.update(JSON.parseArray(
            JSON.parseObject(json).getString("items"), 
            TodoItem.class));
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
            String text = item.getText() == null ? "" : item.getText().trim();
            String status = item.getStatus() == null ? PENDING : item.getStatus().toLowerCase();
            String itemId = (item.getId() == null || item.getId().isBlank())
                    ? String.valueOf(i + 1) : item.getId();

            if (text.isBlank()) {
                throw new IllegalArgumentException("待办事项 " + itemId + ": 文本不能为空");
            }
            if (!List.of(PENDING, IN_PROGRESS, COMPLETED).contains(status)) {
                throw new IllegalArgumentException("状态无效: " + status);
            }
            if (status.equals(IN_PROGRESS)) {
                inProgressCount++;
            }
            validated.add(new TodoItem(itemId, text, status));
        }

        // 关键约束：只能有一个进行中
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
}
```

### 2. TodoItem：待办事项实体

```java
public class TodoItem {
    private String id;
    private String text;
    private String status;  // pending | in_progress | completed
}
```

### 3. 工具注册

```java
private static final TodoManager TODO = new TodoManager();

private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();

static {
    TOOL_HANDLERS.put("bash", Tools::runBash);
    TOOL_HANDLERS.put("readFile", Tools::runReadFile);
    TOOL_HANDLERS.put("writeFile", Tools::runWriteFile);
    TOOL_HANDLERS.put("editFile", Tools::runEditFile);
    TOOL_HANDLERS.put("todo", TODO::update);  // 新增
}

private static final List<ChatCompletionTool> tools = List.of(
    Tools.bashTool(),
    Tools.readFileTool(),
    Tools.writeFileTool(),
    Tools.editFileTool(),
    Tools.todoTool()  // 新增
);
```

### 4. SYSTEM 提示词

```java
private static final String SYSTEM = 
    "你是工作目录 " + Commons.CWD + " 下的编程智能体，" +
    "使用待办工具规划多步骤任务。" +
    "开始前标记为进行中，完成后标记为已完成，" +
    "优先使用工具操作，而非文字说明";
```

### 5. agentLoop 方法：Nag Reminder 机制

```java
public static void agentLoop(List<ChatCompletionMessageParam> messages) {
    int roundsSinceTodo = 0;  // 追踪轮次
    while (true) {
        List<ChatCompletionMessageParam> fullMessages = new ArrayList<>();
        fullMessages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder()
                        .content(SYSTEM)
                        .build()
        ));
        fullMessages.addAll(messages);

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("qwen3.5-plus")
                .messages(fullMessages)
                .tools(tools)
                .build();

        ChatCompletion chatCompletion = Commons.getClient().chat().completions().create(params);
        ChatCompletionMessage message = chatCompletion.choices().get(0).message();

        messages.add(ChatCompletionMessageParam.ofAssistant(message.toParam()));

        Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = message.toolCalls();
        if (toolCallsOptional.isEmpty()) {
            return;
        }

        boolean usedTodo = false;
        for (ChatCompletionMessageToolCall toolCall : toolCallsOptional.get()) {
            ChatCompletionMessageParam toolMessage = Tools.exe(TOOL_HANDLERS, toolCall);
            if (toolMessage != null) {
                messages.add(toolMessage);
            }
            if (Tools.isTodoTool(toolCall)) {
                usedTodo = true;
            }
        }

        // Nag Reminder：连续3轮不调用todo则提醒
        if (usedTodo) {
            roundsSinceTodo = 0;
        } else {
            roundsSinceTodo++;
        }
        if (roundsSinceTodo >= 3) {
            messages.add(ChatCompletionMessageParam
                    .ofUser(ChatCompletionUserMessageParam.builder()
                            .content("<reminder>更新你的待办事项</reminder>")
                            .build()));
        }
    }
}
```

## 关键约束

| 约束 | 说明 |
|------|------|
| 最多 20 条 | 防止上下文溢出 |
| 只能有一个 in_progress | 强制顺序聚焦 |
| 3 轮 nag reminder | 制造问责压力 |

## 相对 s02 的变更

| 组件 | s02 | s03 |
|------|-----|-----|
| Tools | 4 | 5 (+todo) |
| 规划 | 无 | 带状态的 TodoManager |
| Nag 注入 | 无 | 3 轮后注入 reminder |
| Agent loop | 简单分发 | + roundsSinceTodo 计数器 |

## 试试看

1. `重构 hello.java 文件：添加类型注解、文档字符串，并补充程序入口守卫判断`
2. `创建一个 java 包，包含 utils.java 以及测试文件 tests/test_utils.java`
3. `检查所有 java 代码文件，并修复所有代码风格问题`

## 核心要义

> **"An agent without a plan drifts"**  
> 模型自己追踪进度，Harness 负责在它忘记时提醒

**设计原则：**
- 约束即智慧：`in_progress` 只能一个
- 提醒即压力：不更新就追问
- 工具即行动：优先操作而非文字

下篇预告：[S04Subagent - 子 Agent：每个子任务需要干净上下文](./S04Subagent.md)
