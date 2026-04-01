# S07TaskSystem - 任务系统：大目标拆成小任务，持久化到磁盘

## 核心理念

**"大目标拆成小任务，持久化到磁盘" -- 任务图 + 依赖管理 + 文件持久化。**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S07TaskSystem.java
- 原版：https://github.com/shareAI-lab/learn-claude-code
- 上篇：[S06ContextCompact - 上下文压缩](./S06ContextCompact.md)

## 上篇回顾

上篇文章我们实现了三层压缩策略，让无限会话成为可能。

## 问题

s03 的 TodoManager 保存在内存中：
- 会话结束，任务丢失
- 重启后无法恢复进度
- 无法在多轮对话间保持状态

我们需要：**持久化的任务图**。

## 解决方案

```
TaskManager (内存)
    │
    │  save / load
    ▼
tasks/ (文件系统)
    ├── task_1.json
    ├── task_2.json
    └── task_3.json
```

每个任务文件：
```json
{
  "id": 1,
  "subject": "搭建项目",
  "description": "初始化 Maven 项目结构",
  "status": "completed",
  "blockedBy": [],
  "blocks": [2, 3],
  "owner": ""
}
```

## Java 实现详解

### 1. 任务实体结构

```json
{
  "id": 1,
  "subject": "搭建项目",
  "description": "初始化 Maven 项目结构",
  "status": "pending | in_progress | completed",
  "blockedBy": [1, 2],
  "blocks": [3, 4],
  "owner": ""
}
```

| 字段 | 说明 |
|------|------|
| id | 唯一标识，自增 |
| subject | 任务主题 |
| description | 任务描述 |
| status | 状态：pending / in_progress / completed |
| blockedBy | 依赖列表（哪些任务完成后我才开始） |
| blocks | 我阻塞的任务列表 |
| owner | 任务负责人 |

### 2. TaskManager：任务管理器

```java
public class TaskManager {
    private final Path taskDir;
    private int nextId;

    public TaskManager(String tasksDir) {
        this.taskDir = Paths.get(tasksDir);
        Files.createDirectories(this.taskDir);
        this.nextId = findMaxId() + 1;
    }
}
```

### 3. 文件持久化

```java
private void saveTask(JSONObject task) {
    int id = task.getIntValue("id");
    Path path = taskDir.resolve("task_" + id + ".json");
    String json = JSON.toJSONString(task);
    Files.writeString(path, json);
}

private JSONObject loadTask(int taskId) {
    Path path = taskDir.resolve("task_" + taskId + ".json");
    String content = Files.readString(path);
    return JSON.parseObject(content);
}
```

### 4. 创建任务

```java
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
```

### 5. 依赖管理

```java
public String update(int taskId, String status,
                     List<Integer> addBlockedBy,
                     List<Integer> addBlocks) {
    JSONObject task = loadTask(taskId);

    if (status != null) {
        task.put("status", status);
        if ("completed".equals(status)) {
            clearDependency(taskId);  // 完成后清理依赖
        }
    }

    if (addBlockedBy != null) {
        Set<Integer> set = new HashSet<>(task.getList("blockedBy", Integer.class));
        set.addAll(addBlockedBy);
        task.put("blockedBy", new ArrayList<>(set));
    }

    if (addBlocks != null) {
        // 双向更新：更新自己的 blocks，也更新被阻塞任务的 blockedBy
        for (Integer blockedId : addBlocks) {
            JSONObject blocked = loadTask(blockedId);
            List<Integer> blockedBy = blocked.getList("blockedBy", Integer.class);
            if (!blockedBy.contains(taskId)) {
                blockedBy.add(taskId);
                blocked.put("blockedBy", blockedBy);
                saveTask(blocked);
            }
        }
    }

    saveTask(task);
    return JSON.toJSONString(task);
}
```

### 6. 清理依赖

任务完成后，自动从所有任务的 `blockedBy` 中移除自己：

```java
private void clearDependency(int completedId) {
    Files.list(taskDir)
            .filter(p -> p.getFileName().toString().startsWith("task_"))
            .forEach(p -> {
                JSONObject task = JSON.parseObject(Files.readString(p));
                List<Integer> blockedBy = task.getList("blockedBy", Integer.class);
                if (blockedBy != null && blockedBy.contains(completedId)) {
                    blockedBy.remove(Integer.valueOf(completedId));
                    task.put("blockedBy", blockedBy);
                    saveTask(task);
                }
            });
}
```

### 7. 列出任务

```java
public String list() {
    List<JSONObject> tasks = new ArrayList<>();
    Files.list(taskDir)
            .filter(p -> p.getFileName().toString().matches("task_\\d+\\.json"))
            .forEach(p -> tasks.add(JSON.parseObject(Files.readString(p))));

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

        lines.add(marker + " #" + t.getIntValue("id") + ": " 
                + t.getString("subject") + blocked);
    }
    return String.join("\n", lines);
}
```

输出示例：
```
[ ] #1: 搭建项目 (依赖: [])
[x] #2: 编写代码 (依赖: [1])
[x] #3: 编写测试 (依赖: [1])
[ ] #4: 代码审查 (依赖: [2, 3])
```

## 任务工具注册

```java
private static final TaskManager TASKS = new TaskManager(Commons.TASK_DIR);

private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();

static {
    TOOL_HANDLERS.put("bash", Tools::runBash);
    TOOL_HANDLERS.put("readFile", Tools::runReadFile);
    TOOL_HANDLERS.put("writeFile", Tools::runWriteFile);
    TOOL_HANDLERS.put("editFile", Tools::runEditFile);
    TOOL_HANDLERS.put("taskCreate", TASKS::create);
    TOOL_HANDLERS.put("taskUpdate", TASKS::update);
    TOOL_HANDLERS.put("taskList", args -> TASKS.list());
    TOOL_HANDLERS.put("taskGet", TASKS::get);
}

private static final List<ChatCompletionTool> tools = List.of(
    Tools.bashTool(),
    Tools.readFileTool(),
    Tools.writeFileTool(),
    Tools.editFileTool(),
    Tools.taskCreateTool(),
    Tools.taskUpdateTool(),
    Tools.taskListTool(),
    Tools.taskGetTool()
);
```

## 任务图示意

```
代码重构任务看板

   [解析]
      │
      ├──→ [转换] ──┐
      │             ├──→ [测试]
      └──→ [生成] ──┘
                     │
              依赖关系: 转换和生成都依赖解析
              转换和生成可以并行执行
```

## 相对 s06 的变更

| 组件 | s06 | s07 |
|------|-----|-----|
| 任务管理 | 内存 TodoManager | 持久化 TaskManager |
| 依赖管理 | 无 | blockedBy / blocks 双向关联 |
| 存储 | 内存 | tasks/*.json 文件 |
| 工具 | 4 + compact | 4 + taskCreate/taskUpdate/taskList/taskGet |

## 试试看

1. `创建 3 个任务："搭建项目"、"编写代码"、"编写测试"，并按顺序设置依赖关系`
2. `列出所有任务并展示依赖关系图`
3. `完成任务 1，然后列出任务，查看任务 2 是否解除阻塞`
4. `创建一个用于代码重构的任务看板：解析 → 转换 → 生成 → 测试`

## 核心要义

> **"Break big goals into small tasks, order them, persist to disk"**  
> 任务持久化，进度不丢失

**设计原则：**
- 文件即状态：每个任务一个 JSON 文件
- 依赖双向维护：blockedBy + blocks 同步更新
- 完成即清理：完成任务时自动解除阻塞

下篇预告：[S08BackgroundTask - 后台任务：慢操作放后台，Agent 继续思考](./S08BackgroundTask.md)
