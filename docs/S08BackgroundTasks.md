# S08BackgroundTasks - 后台任务：慢操作放后台，Agent 继续思考

## 核心理念

**"慢操作放后台，Agent 继续思考" -- 后台线程执行命令，完成后通知模型。**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S08BackgroundTasks.java
- 原版：https://github.com/shareAI-lab/learn-claude-code
- 上篇：[S07TaskSystem - 任务系统](./S07TaskSystem.md)

## 上篇回顾

上篇文章我们实现了持久化的 TaskManager，支持任务依赖管理和文件存储。

## 问题

同步执行命令时，Agent 必须等待命令完成才能继续：
- `sleep 10` 会阻塞 10 秒
- 大文件编译、测试套件可能耗时几分钟
- Agent 无法并行处理其他任务

**解决方案：后台线程执行，结果完成后通知。**

## 解决方案

```
Agent Loop                      BackgroundManager
    |                                   |
    |  backgroundRun("sleep 10")        |
    |---------------------------------->|
    |  return "taskId: xxx"            |
    |                                   |
    |  (继续处理其他任务)               |  [异步执行中...]
    |                                   |
    |  drainNotifications()             |
    |<----------------------------------|
    |  "[bg:xxx] completed: done"       |
```

## Java 实现详解

### 1. BackgroundManager：后台任务管理器

```java
public class BackgroundManager {
    private final ConcurrentHashMap<String, TaskInfo> tasks = new ConcurrentHashMap<>();
    private final Queue<TaskNotification> notificationQueue = new LinkedList<>();
    private final ReentrantLock lock = new ReentrantLock();
}
```

### 2. 启动后台任务

```java
public String run(String args) {
    String command = JSON.parseObject(args).getString("command");
    String taskId = UUID.randomUUID().toString().substring(0, 8);

    TaskInfo task = new TaskInfo("running", null, command);
    tasks.put(taskId, task);

    Commands.execAsync(command, 300_000, false, new CommandCallback() {
        @Override
        public void onSuccess(CommandResult result) {
            updateTask(taskId, "completed", result.getOutput());
        }

        @Override
        public void onFail(CommandResult result) {
            String status = result.isTimeout() ? "timeout" : "error";
            updateTask(taskId, status, result.getOutput());
        }
    });

    return "后台任务 " + taskId + " 开始: " + truncate(command, 80);
}
```

### 3. 任务状态更新

```java
private void updateTask(String taskId, String status, String output) {
    TaskInfo task = tasks.get(taskId);
    if (task == null) return;

    output = truncate(output == null ? "" : output.trim(), 50000);
    if (output.isEmpty()) output = "(无输出)";

    task.setStatus(status);
    task.setResult(output);

    // 加入通知队列
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
```

### 4. 查询任务状态

```java
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

    // 列出所有任务
    List<String> lines = new ArrayList<>();
    for (Map.Entry<String, TaskInfo> entry : tasks.entrySet()) {
        String tid = entry.getKey();
        TaskInfo task = entry.getValue();
        lines.add(tid + ": [" + task.getStatus() + "] " + truncate(task.getCommand(), 60));
    }
    return lines.isEmpty() ? "没有后台任务" : String.join("\n", lines);
}
```

### 5. 清空通知队列

```java
public String drainNotifications() {
    lock.lock();
    try {
        List<TaskNotification> list = new ArrayList<>(notificationQueue);
        notificationQueue.clear();
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
```

### 6. agentLoop 集成后台通知

```java
private static final String SYSTEM = "你是运行在 " + Commons.CWD 
    + " 工作目录下的编码智能体，长时间运行的命令请使用 backgroundRun 执行";

public static void agentLoop(List<ChatCompletionMessageParam> messages) {
    while (true) {
        // 每次调用前清空通知队列，注入后台结果
        String notifs = BG.drainNotifications();
        if (notifs != null && !notifs.isBlank()) {
            messages.add(ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam.builder()
                            .content(String.format("<background-results>\n%s\n</background-results>", notifs))
                            .build()));
            messages.add(ChatCompletionMessageParam.ofAssistant(
                    ChatCompletionAssistantMessageParam.builder()
                            .content("已记录后台执行结果")
                            .build()));
        }

        // 正常 LLM 调用...
        List<ChatCompletionMessageParam> fullMessages = new ArrayList<>();
        fullMessages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(SYSTEM).build()));
        fullMessages.addAll(messages);

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("qwen3.5-plus")
                .messages(fullMessages)
                .tools(tools)
                .build();

        ChatCompletion chatCompletion = Commons.getClient().chat().completions().create(params);
        // ...
    }
}
```

### 7. 工具注册

```java
private static final BackgroundManager BG = new BackgroundManager();

private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();

static {
    TOOL_HANDLERS.put("bash", Tools::runBash);
    TOOL_HANDLERS.put("readFile", Tools::runReadFile);
    TOOL_HANDLERS.put("writeFile", Tools::runWriteFile);
    TOOL_HANDLERS.put("editFile", Tools::runEditFile);
    TOOL_HANDLERS.put("backgroundRun", BG::run);
    TOOL_HANDLERS.put("checkBackground", BG::check);
}

private static final List<ChatCompletionTool> tools = List.of(
    Tools.bashTool(),
    Tools.readFileTool(),
    Tools.writeFileTool(),
    Tools.editFileTool(),
    Tools.backgroundRunTool(),
    Tools.checkBackgroundTool()
);
```

## 执行流程图

```
1. Agent 调用 backgroundRun("sleep 10")
          |
          v
2. BackgroundManager 启动异步任务，立即返回 taskId
          |
          v
3. Agent 继续处理其他任务（不阻塞）
          |
          v
4. 后台任务完成，通知写入队列
          |
          v
5. 下次 LLM 调用前，drainNotifications() 注入结果
          |
          v
6. Agent 收到 <background-results> 标签处理结果
```

## 任务状态

| 状态 | 说明 |
|------|------|
| running | 运行中 |
| completed | 成功完成 |
| timeout | 超时（默认 5 分钟） |
| error | 执行失败 |

## 相对 s07 的变更

| 组件 | s07 | s08 |
|------|-----|-----|
| 任务管理 | 持久化任务文件 | 持久化 + 后台执行 |
| 执行方式 | 同步阻塞 | 异步非阻塞 |
| 通知机制 | 无 | drainNotifications 注入 |
| 新增工具 | taskCreate/Update/List/Get | backgroundRun / checkBackground |

## 试试看

1. `在后台运行 sleep 5 && echo done，并在其执行期间创建一个文件`
2. `启动 3 个后台任务：sleep 2s、sleep 4s、sleep 6s，并查看它们的状态`

## 核心要义

> **"Run slow operations in the background; the agent keeps thinking"**  
> 慢操作不阻塞，Agent 并行工作

**设计原则：**
- 异步执行：命令立即返回，任务后台运行
- 通知注入：下次 LLM 调用前注入结果
- 线程安全：ConcurrentHashMap + ReentrantLock

下篇预告：[S09AgentTeams - Agent 团队：任务太大，一个 Agent 忙不过来](./S09AgentTeams.md)
