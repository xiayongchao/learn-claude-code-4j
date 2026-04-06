# S11AutonomousAgents - 自主 Agent：自动认领任务 + 空闲轮询

## 核心理念

**"Agent 主动寻找任务，无需等待指令 -- 真正的自主工作能力。"**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S11AutonomousAgents.java
- 上篇：[S10TeamProtocols - 团队协议](./S10TeamProtocols.md)

## 上篇回顾

上篇文章我们实现了团队协议，支持优雅停止和方案审批。

## 问题

之前的 Teammate 需要 Lead 分配任务：
- 等待 Lead 发消息才工作
- 无消息时循环结束进入 idle
- 无法主动寻找任务

**我们需要：Agent 自主工作能力。**

## 解决方案

```
Agent 工作模式

普通模式 (Working):
┌──────────────────────────────────────┐
│  readInbox → chat → callTools       │
│       ↓                              │
│  有消息或空闲? → 退出普通模式        │
└──────────────────────────────────────┘

空闲模式 (Idle Poll):
┌──────────────────────────────────────┐
│  setIdle → poll inbox + tasks        │
│       ↓                              │
│  有新消息? → 退出空闲，继续工作       │
│  有未认领任务? → claimTask，继续工作  │
│  超时? → shutdown                    │
└──────────────────────────────────────┘
```

## 核心组件详解

### 1. Tasks 任务看板

```java
public class Tasks {
    public void writeTask(Task task) {
        Path taskPath = FileUtils.resolve(workDir,
                String.format("tasks/task_%s.json", taskId), true, true);
        FileUtils.write(taskPath, task);
    }
    
    public Task readTask(int taskId) {
        Path taskPath = FileUtils.resolve(workDir,
                String.format("tasks/task_%s.json", taskId), true, true);
        return FileUtils.read(taskPath, Task.class);
    }
    
    public List<Task> list() {
        Path tasksDir = FileUtils.resolve(workDir, "tasks", false, true);  // false=目录
        return Files.list(tasksDir)
                .filter(p -> p.matches("task_\\d+\\.json"))
                .map(p -> JSON.parseObject(readString(p), Task.class))
                .toList();
    }
    
    public Task getUnclaimedTask() {
        List<Task> unclaimed = scanUnclaimedTasks();
        return unclaimed.isEmpty() ? null : unclaimed.get(0);
    }
    
    public List<Task> scanUnclaimedTasks() {
        return list().stream()
                .filter(t -> TaskStatus.PENDING.is(t.getStatus()))
                .filter(t -> t.getOwner() == null || t.getOwner().isBlank())
                .filter(t -> t.getBlockedBy() == null || t.getBlockedBy().isEmpty())
                .toList();
    }
    
    public boolean claimTask(int taskId, String owner) {
        Task task = readTask(taskId);
        if (task.getOwner() != null) return false;
        if (!TaskStatus.PENDING.is(task.getStatus())) return false;
        if (task.getBlockedBy() != null && !task.getBlockedBy().isEmpty()) return false;
        
        task.setOwner(owner);
        task.setStatus(TaskStatus.IN_PROGRESS.getValue());
        writeTask(task);
        return true;
    }
    
    public void clearDependency(int completedId) {
        list().forEach(task -> {
            List<Integer> blockedBy = task.getBlockedBy();
            if (blockedBy != null && blockedBy.contains(completedId)) {
                blockedBy.remove(Integer.valueOf(completedId));
                writeTask(task);
            }
        });
    }
}
```

### 2. Task 任务实体

```java
public class Task {
    private int taskId;
    private String subject;        // 任务主题
    private String description;    // 任务描述
    private String status;         // pending / in_progress / completed
    private List<Integer> blockedBy;  // 依赖的任务 ID 列表
    private List<Integer> blocks;     // 被我阻塞的任务 ID 列表
    private String owner;          // 负责人
}
```

### 3. Task 工具集

#### TaskCreateTool - 创建任务

```java
public class TaskCreateTool extends BaseTool<TaskCreateToolArgs> {
    public String doCall(TaskCreateToolArgs arguments) {
        Task task = new Task();
        task.setTaskId(this.tasks.nextTaskId());
        task.setSubject(arguments.getSubject());
        task.setDescription(arguments.getDescription());
        task.setStatus(TaskStatus.PENDING.getValue());  // 使用枚举
        task.setBlockedBy(new ArrayList<>());
        task.setBlocks(new ArrayList<>());
        task.setOwner("");
        
        this.tasks.writeTask(task);
        return JSON.toJSONString(task);
    }
}
```

#### TaskUpdateTool - 更新任务

```java
public class TaskUpdateTool extends BaseTool<TaskUpdateToolArgs> {
    public String doCall(TaskUpdateToolArgs arguments) {
        int taskId = arguments.getTaskId();
        String status = arguments.getStatus();
        List<Integer> addBlockedBy = arguments.getAddBlockedBy();
        List<Integer> addBlocks = arguments.getAddBlocks();
        
        Task task = this.tasks.readTask(taskId);
        
        // 更新状态
        if (status != null) {
            task.setStatus(status);
            if (TaskStatus.COMPLETED.is(status)) {
                this.tasks.clearDependency(taskId);  // 完成时清理依赖
            }
        }
        
        // 添加依赖
        if (addBlockedBy != null) {
            Set<Integer> set = new HashSet<>(task.getBlockedBy());
            set.addAll(addBlockedBy);
            task.setBlockedBy(new ArrayList<>(set));
        }
        
        // 添加阻塞关系（双向更新）
        if (addBlocks != null) {
            for (Integer blockedId : addBlocks) {
                Task blocked = this.tasks.readTask(blockedId);
                if (!blocked.getBlockedBy().contains(taskId)) {
                    blocked.getBlockedBy().add(taskId);
                    this.tasks.writeTask(blocked);
                }
            }
        }
        
        this.tasks.writeTask(task);
        return JSON.toJSONString(task);
    }
}
```

#### TaskListTool - 列出任务

```java
public class TaskListTool extends BaseTool<Void> {
    public String doCall(Void arguments) {
        List<Task> tasks = this.tasks.list();
        List<String> lines = new ArrayList<>();
        
        for (Task t : tasks) {
            String marker = switch (t.getStatus()) {
                case "pending" -> "[ ]";
                case "in_progress" -> "[>]";
                case "completed" -> "[x]";
                default -> "[?]";
            };
            
            String blocked = (t.getBlockedBy() != null && !t.getBlockedBy().isEmpty())
                    ? " (依赖: " + t.getBlockedBy() + ")" : "";
            
            lines.add(marker + " " + t.getTaskId() + ": " + t.getSubject() + blocked);
        }
        
        return String.join("\n", lines);
    }
}
```

#### ClaimTaskTool - 认领任务（带锁）

```java
public class ClaimTaskTool extends BaseTool<ClaimTaskToolArgs> {
    public String doCall(ClaimTaskToolArgs arguments) {
        int taskId = arguments.getTaskId();
        String owner = States.get().getName();
        
        // 使用 claimTaskLock 防止并发认领
        States.get().getClaimTaskLock().lock();
        try {
            // 检查是否有未完成的任务
            List<Task> taskList = this.tasks.list();
            for (Task task : taskList) {
                if (Objects.equals(name(), task.getOwner()) 
                        && TaskStatus.IN_PROGRESS.is(task.getStatus())) {
                    return String.format("错误：任务 %s 还没有处理完成，无法认领新任务", task.getTaskId());
                }
            }
            
            Task task = this.tasks.readTask(taskId);
            if (task == null) {
                return String.format("错误：任务 %s 不存在", taskId);
            }
            
            // 检查已被认领
            if (task.getOwner() != null && !task.getOwner().isBlank()) {
                return String.format("错误：任务 %s 已被 %s 认领", taskId, task.getOwner());
            }
            
            // 检查状态
            if (!TaskStatus.PENDING.is(task.getStatus())) {
                return String.format("错误：任务 %s 无法认领，状态为 %s", taskId, task.getStatus());
            }
            
            // 检查依赖
            if (task.getBlockedBy() != null && !task.getBlockedBy().isEmpty()) {
                return String.format("错误：任务 %s 被其他任务阻塞", taskId);
            }
            
            task.setOwner(owner);
            task.setStatus(TaskStatus.IN_PROGRESS.getValue());
            this.tasks.writeTask(task);
            
            return String.format("已为 %s 认领任务 %s", owner, task.getTaskId());
        } catch (Exception e) {
            return String.format("错误：认领任务失败 - %s", e.getMessage());
        } finally {
            States.get().getClaimTaskLock().unlock();
        }
    }
}
```

### 4. IdleTeammateTool - 空闲工具

```java
public class IdleTeammateTool extends BaseTool<Void> {
    public String doCall(Void arguments) {
        States.teammate().setIdle(true);
        return "进入空闲阶段，将轮询等待新任务";
    }
}
```

### 5. S11TeammateReAct 自主循环

```java
public class S11TeammateReAct implements TeammateReAct {
    public void loop() {
        List<ChatCompletionMessageParam> messages = States.teammate().getMessages();
        messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(States.teammate().getUserPrompt())
                        .build()));
        
        Integer maxLoopTimes = States.teammate().getMaxLoopTimes();
        boolean idlePoll = true;
        
        while (idlePoll) {
            // 普通工作循环
            for (int i = 0; i < maxLoopTimes; i++) {
                this.readInbox(messages);
                if (States.teammate().isShutdown()) break;
                
                ChatCompletionAssistantMessageParam assistantMessage = this.chat(messages);
                Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = assistantMessage.toolCalls();
                if (toolCallsOptional.isEmpty()) break;
                
                this.callTools(toolCallsOptional.get(), messages);
                if (States.teammate().isIdle()) break;
            }
            
            // 空闲轮询阶段
            this.team.setTeammateIdle();
            idlePoll = this.idlePoll(messages);
        }
    }
    
    private boolean idlePoll(List<ChatCompletionMessageParam> messages) {
        boolean resume = false;
        
        long idleTimeout = States.teammate().getIdleTimeout();    // 默认 5 分钟
        long pollInterval = States.teammate().getPollInterval();  // 默认 5 秒
        long polls = idleTimeout / Math.max(pollInterval, 1);
        
        for (int i = 0; i < polls; i++) {
            System.out.printf(">>>%s准备认领任务%n", States.get().getName());
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
            // 检查收件箱
            int readSize = this.readInbox(messages);
            if (States.teammate().isShutdown()) {
                this.team.setTeammateShutdown();
                return false;
            }
            if (readSize > 0) {
                this.team.setTeammateWorking();
                return true;  // 有消息，继续工作
            }
            
            // 自动认领任务
            Task unclaimedTask = this.tasks.getUnclaimedTask();
            if (unclaimedTask != null) {
                if (this.tasks.claimTask(unclaimedTask.getTaskId(), States.get().getName())) {
                    if (messages.size() <= 3) {
                        this.identityReInjection(messages);  // 重新注入身份
                    }
                    messages.add(ChatCompletionMessageParam.ofUser(
                            ChatCompletionUserMessageParam.builder()
                                    .content(String.format("<auto-claimed>任务 %s: %s\n%s</auto-claimed>",
                                            unclaimedTask.getTaskId(), unclaimedTask.getSubject(),
                                            unclaimedTask.getDescription()))
                                    .build()));
                    this.team.setTeammateWorking();
                    return true;  // 认领到任务，继续工作
                }
            }
        }
        
        // 超时，关闭
        this.team.setTeammateShutdown();
        return false;
    }
    
    private void identityReInjection(List<ChatCompletionMessageParam> messages) {
        messages.clear();
        messages.add(this.makeIdentityBlock());
        messages.add(ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .content(String.format("我是 %s，继续执行", States.get().getName()))
                        .build()));
    }
    
    public ChatCompletionMessageParam makeIdentityBlock() {
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(String.format("<identity>你是：%s，角色：%s，团队：%s。继续执行你的工作</identity>",
                                States.get().getName(), States.get().getRole(), this.team.getTeamName()))
                        .build());
    }
}
```

### 6. TeammateState 扩展

```java
public class TeammateState extends BaseState implements State {
    private String userPrompt;
    private Integer maxLoopTimes;
    private String lead;
    private boolean shutdown;
    private boolean idle;
    private long idleTimeout = 1000 * 60 * 5;   // 5 分钟
    private long pollInterval = 1000 * 5;       // 5 秒
    // 锁从 BaseState 继承
}
```

### 7. S11SpawnTeammateTool - 创建自主 Agent

```java
public class S11SpawnTeammateTool extends BaseTool<SpawnTeammateToolArgs> {
    public String doCall(SpawnTeammateToolArgs arguments) {
        TeammateState state = new TeammateState();
        state.setName(name);
        state.setModel(States.get().getModel());
        state.setRole(role);
        state.setPrompt(String.format("你是: %s, 角色: %s, 所属团队: %s, 工作目录: %s。" +
                "若无待办工作，请使用闲置工具，系统将自动为你认领新任务",
                name, role, teamName, workDir));
        state.setUserPrompt(prompt);
        state.setMaxLoopTimes(50);
        state.setIdleTimeout(1000 * 60 * 5);   // 5 分钟
        state.setPollInterval(1000 * 5);       // 5 秒
        state.setWorkDir(workDir);
        state.setLead(States.get().getName());
        state.setMessages(new ArrayList<>());
        
        // 共享 Lead 的锁，保证线程安全
        state.setShutdownLock(States.get().getShutdownLock());
        state.setPlanLock(States.get().getPlanLock());
        state.setClaimTaskLock(States.get().getClaimTaskLock());
        
        this.teammate(name, role);
        this.reActs.start(state);
        return String.format("创建 '%s' (角色: %s)", name, role);
    }
}
```

## 执行流程图

```
Lead 创建任务:
1. Lead 调用 taskCreate("实现登录功能")
           │
           ▼
2. Tasks 写入 tasks/task_1.json
           │
           ▼
3. 返回 {"taskId": 1, "status": "pending", ...}

Agent 自主工作:
1. alice 调用 idle() 表示无工作
           │
           ▼
2. S11TeammateReAct 进入 idlePoll 循环
           │
           ▼
3. 轮询检查收件箱和任务板
           │
           ▼
4. 发现未认领任务 #1
           │
           ▼
5. claimTaskLock.lock() → 认领任务 → lock.unlock()
           │
           ▼
6. 自动开始执行任务
```

## 新增工具清单

| 工具 | 所有者 | 功能 |
|------|--------|------|
| taskCreate | Lead | 创建任务 |
| taskUpdate | Lead | 更新任务状态/依赖 |
| taskList | Both | 列出所有任务 |
| taskGet | Both | 获取任务详情 |
| claimTask | Both | 认领任务（带锁） |
| idle | Teammate | 进入空闲轮询 |

## Guice 配置

```java
public class S11AutonomousAgents extends AbstractModule {
    @Override
    protected void configure() {
        // Lead 工具
        Multibinder<LeadTool> leadToolBinder = Multibinder.newSetBinder(binder(), LeadTool.class);
        // ... 基础工具
        leadToolBinder.addBinding().to(IdleLeadTool.class);
        leadToolBinder.addBinding().to(TaskCreateTool.class);
        leadToolBinder.addBinding().to(TaskUpdateTool.class);
        leadToolBinder.addBinding().to(TaskGetTool.class);
        leadToolBinder.addBinding().to(TaskListTool.class);
        leadToolBinder.addBinding().to(ClaimTaskTool.class);
        
        // Teammate 工具
        Multibinder<TeammateTool> teammateToolBinder = Multibinder.newSetBinder(binder(), TeammateTool.class);
        // ... 基础工具
        teammateToolBinder.addBinding().to(IdleTeammateTool.class);
        teammateToolBinder.addBinding().to(ClaimTaskTool.class);
        
        // 核心服务
        bind(Tasks.class).in(Singleton.class);
        
        bind(TeammateReAct.class).to(S11TeammateReAct.class);
    }
}
```

## 任务依赖管理

```
任务依赖示例:

[task_1] 搭建项目 (已完成)
    │
    ├──→ [task_2] 编写代码 (依赖 #1)
    │         │
    │         └──→ [task_4] 代码审查 (依赖 #2)
    │
    └──→ [task_3] 编写测试 (依赖 #1)
              │
              └──→ [task_5] 集成测试 (依赖 #3, #4)

特点:
- task_2 和 task_3 可并行执行（都只依赖 #1）
- task_4 需等 #2 完成
- task_5 需等 #3 和 #4 都完成
```

## 相对 s10 的变更

| 组件 | s10 | s11 |
|------|-----|-----|
| 任务系统 | 无 | Tasks + Task |
| 自主工作 | 无 | idlePoll 自动认领 |
| 轮询机制 | 无 | 空闲轮询 + 超时关闭 |
| 身份注入 | 无 | 压缩后重新注入 |
| Agent 状态 | 基本 | 完整状态机 |
| 并发控制 | 无 | claimTaskLock |
| 轮询参数 | - | idleTimeout=5min, pollInterval=5s |

## 试试看

1. `在任务面板上创建 3 个任务，然后生成成员 Alice 和 Bob，观察他们自动认领任务`
2. `生成一名程序员队友，让其从任务面板中自行寻找并执行任务`
3. `创建带有依赖关系的任务，观察团队成员遵循阻塞顺序执行任务`

## 核心要义

> **"Auto-claim tasks, idle poll, graceful shutdown"**  
> 主动认领，轮询等待，优雅关闭

**设计原则：**
- 任务驱动：所有工作围绕任务展开
- 主动出击：不等待消息，主动寻找任务
- 状态轮询：空闲时定期检查新任务
- 超时关闭：无工作可做时自动退出

**设计亮点：**
- `claimTaskLock`：使用 ReentrantLock 防止并发认领同一任务
- `identityReInjection`：上下文压缩后重新注入身份信息
- `clearDependency`：任务完成时自动解除依赖
- `双向依赖维护`：更新 blockedBy 时同步更新 blocks
- `共享锁机制`：Teammate 继承 Lead 的锁，保证线程安全
