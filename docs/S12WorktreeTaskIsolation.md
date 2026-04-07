# S12WorktreeTaskIsolation - 工作树与任务隔离

## 核心理念

**"使用 Git Worktree 隔离任务工作空间，实现真正的并行开发"**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S12WorktreeTaskIsolation.java
- 上篇：[S11AutonomousAgents - 自主 Agent](./S11AutonomousAgents.md)

## 上篇回顾

上篇文章我们实现了自主 Agent，支持自动认领任务和空闲轮询。

## 问题

- 多任务并行开发时，工作目录相互影响
- 单分支开发无法真正并行
- 任务与工作空间无绑定关系

**我们需要：任务级别的隔离工作空间。**

## 解决方案

```
Worktree 架构

主仓库 (main)
      │
      ├── [wt/auth-refactor] → 任务 #1
      │         └── commit-1 → merge → delete
      │
      └── [wt/ui-login] → 任务 #2
                └── commit-2 → merge → keep
```

## 核心组件详解

### 1. Worktree 工作树

```java
public class Worktree {
    private String name;      // 名称 (auth-refactor)
    private String path;     // 路径 (.worktrees/auth-refactor)
    private String branch;    // 分支 (wt/auth-refactor)
    private Integer taskId; // 绑定的任务 ID
    private String status;   // active / kept / removed
    private double createdAt;
    private double removedAt;
    private double keptAt;
}
```

### 2. Worktrees 工作树管理

```java
public class Worktrees {
    private final Path repoRoot;
    private final Path worktreesDir;
    private final Path indexPath;
    private final Tasks tasks;
    private final EventBus events;

    public String create(String name, Integer taskId, String baseRef) {
        // 创建 Git Worktree 分支
        runGit("worktree", "add", "-b", branch, wtPath.toString(), finalBaseRef);

        // 写入索引
        Worktree entry = new Worktree();
        entry.setName(name);
        entry.setPath(wtPath.toString());
        entry.setBranch(branch);
        entry.setTaskId(taskId);
        entry.setStatus("active");
        
        // 绑定任务
        if (taskId != null) {
            tasks.bindWorktree(taskId, name);
        }
        
        // 发送事件
        events.emit("worktree.create.after", ...);
        return JSON.toJSONString(entry);
    }

    public String remove(String name, boolean force, boolean completeTask) {
        // 删除 Git Worktree
        runGit("worktree", "remove", force ? "--force" : "", wtPath);
        
        // 完成关联任务
        if (completeTask && taskId != null) {
            tasks.updateStatus(taskId, TaskStatus.COMPLETED.getValue());
        }
    }

    public String keep(String name) {
        IndexData idx = loadIndex();
        for (Worktree item : idx.getWorktrees()) {
            if (Objects.equals(item.getName(), name)) {
                item.setStatus("kept");
                item.setKeptAt(ts);
            }
        }
        saveIndex(idx);
    }

    public String run(String name, String command) {
        // 在指定 Worktree 中执行命令
        Commands.CommandResult r = Commands.execSync(command, 300000, false, path);
    }
}
```

### 3. 索引文件

```json
// .worktrees/index.json
{
    "worktrees": [
        {
            "name": "auth-refactor",
            "path": "/path/to/.worktrees/auth-refactor",
            "branch": "wt/auth-refactor",
            "taskId": 1,
            "status": "active",
            "createdAt": 1234567890.0
        },
        {
            "name": "ui-login",
            "path": "/path/to/.worktrees/ui-login",
            "branch": "wt/ui-login",
            "taskId": 2,
            "status": "kept",
            "createdAt": 1234567891.0,
            "keptAt": 1234567892.0
        }
    ]
}
```

### 4. 事件总线

```java
public class EventBus {
    public Path eventLogPath() {
        return FileUtils.resolve(workDir, ".worktrees/events.jsonl", true, true);
    }

    public void emit(String event, Map<String, Object> task, 
                  Map<String, Object> worktree, String error) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("ts", System.currentTimeMillis() / 1000.0);
        payload.put("task", task);
        payload.put("worktree", worktree);
        if (error != null) {
            payload.put("error", error);
        }
        
        String line = JSON.toJSONString(payload);
        Files.writeString(this.eventLogPath(), line + "\n", APPEND);
    }

    public List<WorktreeEvent> listRecent(int limit) {
        // 读取最近的事件
    }
}
```

### 5. Worktree 创建工具

```java
public class WorktreeCreateTool extends BaseTool<WorktreeCreateToolArgs> {
    public String doCall(WorktreeCreateToolArgs arguments) {
        return worktrees.create(
                arguments.getName(),
                arguments.getTaskId(),
                arguments.getBaseRef());
    }
}
```

工具定义：
```json
{
    "type": "function",
    "function": {
        "name": "worktreeCreate",
        "description": "创建一个 Git 工作树，并可选择将其绑定到一个任务",
        "parameters": {
            "type": "object",
            "properties": {
                "name": { "type": "string" },
                "taskId": { "type": "integer" },
                "baseRef": { "type": "string" }
            },
            "required": ["name"]
        }
    }
}
```

### 6. 其他 Worktree 工具

| 工具 | 功能 |
|------|------|
| worktreeList | 列出所有工作树 |
| worktreeStatus | 查看工作树状态 |
| worktreeRun | 在工作树中执行命令 |
| worktreeKeep | 保留工作树 |
| worktreeRemove | 删除工作树 |
| worktreeEvents | 查看工作树事件 |
| taskBindWorktree | 绑定任务到工作树 |

### 7. TaskBindWorktreeTool - 任务绑定工作树

```java
public class TaskBindWorktreeTool extends BaseTool<TaskBindWorktreeToolArgs> {
    public String doCall(TaskBindWorktreeToolArgs arguments) {
        int taskId = arguments.getTaskId();
        String worktreeName = arguments.getWorktreeName();
        
        Worktree wt = worktrees.find(worktreeName);
        if (wt == null) {
            return "Error: Worktree '" + worktreeName + "' not found";
        }
        
        wt.setTaskId(taskId);
        IndexData idx = worktrees.loadIndex();
        // 更新索引...
        
        tasks.bindWorktree(taskId, worktreeName);
        return JSON.toJSONString(wt);
    }
}
```

## 执行流程图

```
Lead 创建并管理任务工作树:

1. taskCreate("实现后端认证") → taskId=1
         │
         ▼
2. taskCreate("实现前端登录") → taskId=2
         │
         ▼
3. worktreeCreate("auth-refactor", taskId=1)
         │ 创建 worktree，绑定 task #1
         ▼
4. worktreeCreate("ui-login", taskId=2)
         │ 创建 worktree，绑定 task #2
         ▼
5. worktreeRun("auth-refactor", "git status --short")
         │ 在独立目录执行命令
         ▼
6. worktreeRun("ui-login", "npm run dev")
         │ 并行开发
         ▼
7. worktreeRemove("auth-refactor", completeTask=true)
         │ 删除并完成任务
         ▼
8. worktreeKeep("ui-login")
         │ 保留，等待合并
```

## Guice 配置

```java
public class S12WorktreeTaskIsolation extends AbstractModule {
    @Override
    protected void configure() {
        // Lead 工具
        Multibinder<LeadTool> leadToolBinder = Multibinder.newSetBinder(binder(), LeadTool.class);
        leadToolBinder.addBinding().to(BashTool.class);
        leadToolBinder.addBinding().to(ReadFileTool.class);
        leadToolBinder.addBinding().to(WriteFileTool.class);
        leadToolBinder.addBinding().to(EditFileTool.class);
        
        // 任务工具
        leadToolBinder.addBinding().to(TaskCreateTool.class);
        leadToolBinder.addBinding().to(TaskListTool.class);
        leadToolBinder.addBinding().to(TaskGetTool.class);
        leadToolBinder.addBinding().to(TaskUpdateTool.class);
        leadToolBinder.addBinding().to(TaskBindWorktreeTool.class);
        
        // Worktree 工具
        leadToolBinder.addBinding().to(WorktreeCreateTool.class);
        leadToolBinder.addBinding().to(WorktreeListTool.class);
        leadToolBinder.addBinding().to(WorktreeStatusTool.class);
        leadToolBinder.addBinding().to(WorktreeRunTool.class);
        leadToolBinder.addBinding().to(WorktreeKeepTool.class);
        leadToolBinder.addBinding().to(WorktreeRemoveTool.class);
        leadToolBinder.addBinding().to(WorktreeEventsTool.class);
        
        // Teammate 工具（类似）
        
        // 核心服务
        bind(OpenAIClient.class).toInstance(Commons.getClient());
        bind(TeammateReAct.class).to(S12TeammateReAct.class);
        bind(LeadReAct.class).in(Singleton.class);
        bind(EventBus.class).in(Singleton.class);
        bind(ReActs.class).to(ReActsImpl.class);
    }
}
```

## 工作树状态流转

```
              ┌─────────────────────────────────────────┐
              │                                         │
              ▼                                         │
         ┌────────┐                                     │
         │ active │──→ worktreeRun() ──→ 开发中         │
         └────────┘                                     │
              │                                         │
              ├────────────┐                            │
              │            │                            │
              ▼            ▼                            │
        ┌──────────┐  ┌──────────┐                     │
        │ keep()   │  │remove() │                     │
        └──────────┘  └──────────┘                     │
              │            │                            │
              ▼            ▼                            │
        ┌──────────┐  ┌──────────┐                   │
        │  kept    │  │removed  │─────────────────────┘
        └──────────┘  └──────────┘
```

## 事件日志

```jsonl
// .worktrees/events.jsonl
{"event": "worktree.create.after", "ts": 1234567890.0, "task": {"id": 1}, "worktree": {"name": "auth-refactor", "status": "active"}}
{"event": "worktree.create.after", "ts": 1234567891.0, "task": {"id": 2}, "worktree": {"name": "ui-login", "status": "active"}}
{"event": "worktree.keep", "ts": 1234567895.0, "task": {"id": 2}, "worktree": {"name": "ui-login", "status": "kept"}}
{"event": "task.completed", "ts": 1234567896.0, "task": {"id": 1, "status": "completed"}, "worktree": {"name": "auth-refactor"}}
{"event": "worktree.remove.after", "ts": 1234567897.0, "task": {"id": 1}, "worktree": {"name": "auth-refactor", "status": "removed"}}
```

## 相对 s11 的变更

| 组件 | s11 | s12 |
|------|-----|-----|
| 工作空间 | 无 | Git Worktree |
| 任务隔离 | 无 | 独立目录 |
| 并行开发 | 无 | 多分支并行 |
| 事件追踪 | 无 | EventBus |
| 工作树管理 | 无 | 完整 CRUD |
| 任务绑定 | 无 | Task ↔ Worktree |

## 试试看

1. `创建两个任务，然后用 worktreeCreate 为每个任务创建独立工作树`
2. `在不同的 worktree 中并行开发，观察不会相互影响`
3. `使用 worktreeRun 在工作树中执行 git status --short`
4. `使用 worktreeKeep 保留一个工作树，然后用 worktreeRemove 删除另一个`
5. `使用 worktreeEvents 查看所有操作事件日志`

## 核心要义

> **"One task per worktree, parallel development, clean merge"**  
> 一任务一工作树，并行开发，干净合并

**设计原则：**
- 任务绑定：每个任务绑定独立 Worktree
- 隔离开发：不同任务在不同分支
- 生命周期：创建 → 开发 → keep/remove → 合并
- 事件追踪：记录所有操作到事件日志

**设计亮点：**
- `Git Worktree`：使用原生 Git 机制，零冲突
- `任务绑定`：Task ↔ Worktree 双向关联
- `状态管理`：active / kept / removed
- `EventBus`：完整生命周期可见性
- `自动完成`：remove 时可选完成任务