# S09AgentTeams - Agent 团队：一个 Lead + 多个 Teammate，收件箱通信

## 核心理念

**"一个 Lead + 多个 Teammate，通过收件箱异步通信协作" -- 多 Agent 协作的基础架构。**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S09AgentTeams.java
- 重构源码：https://github.com/xiayongchao/learn-claude-code-4j/tree/main/src/main/java/org/jc/component
- 上篇：[S08BackgroundTasks - 后台任务](./S08BackgroundTasks.md)

## 上篇回顾

上篇文章我们实现了后台任务系统，让慢操作不阻塞 Agent 继续工作。

## 问题

之前所有 Agent 都是独立工作的，单一 Agent 无法：
- 并行处理多个任务
- 分工协作完成复杂目标
- 维护团队成员状态

**我们需要：多 Agent 协作架构。**

## 解决方案

```
                    ┌─────────────────┐
                    │      Lead       │  (领导：协调者)
                    │  (Agent Loop)   │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ Teammate │  │ Teammate │  │ Teammate │
        │   Alice  │  │    Bob   │  │  Charlie │
        └────┬─────┘  └────┬─────┘  └────┬─────┘
             │              │              │
             └──────────────┴──────────────┘
                             │
                    ┌────────▼────────┐
                    │   MessageBus    │  (消息总线)
                    │  (收件箱系统)   │
                    └─────────────────┘
```

## 重构概览

从第9课开始，项目进行了全面的组件化重构：

```
src/main/java/org/jc/
├── agents/              # Agent 入口类
├── component/           # 核心组件
│   ├── loop/           # ReAct 循环
│   ├── tool/           # 工具集
│   ├── team/           # 团队管理
│   ├── state/          # 状态管理
│   ├── inbox/          # 消息收件箱
│   └── util/           # 工具类
└── Commons.java        # 公共常量
```

### 依赖注入：Google Guice

使用 Guice 进行依赖管理：

```java
public class S09AgentTeams extends AbstractModule {
    @Override
    protected void configure() {
        // 领导工具
        Multibinder<LeadTool> leadToolBinder = Multibinder.newSetBinder(binder(), LeadTool.class);
        leadToolBinder.addBinding().to(BashTool.class);
        leadToolBinder.addBinding().to(ReadFileTool.class);
        // ...
        
        // 队员工具
        Multibinder<TeammateTool> teammateToolBinder = Multibinder.newSetBinder(binder(), TeammateTool.class);
        // ...
        
        // 核心服务
        bind(OpenAIClient.class).toInstance(Commons.getClient());
        bind(MessageBus.class).in(Singleton.class);
        bind(Team.class).in(Singleton.class);
        bind(ReActs.class).to(ReActsImpl.class);
    }
}
```

## 核心组件详解

### 1. State 状态管理

```java
public interface State {
    String getName();
    String getRole();
    String getModel();
    String getPrompt();
    String getWorkDir();
    List<ChatCompletionMessageParam> getMessages();
}
```

LeadState vs TeammateState：

| 属性 | LeadState | TeammateState |
|------|-----------|---------------|
| messages | ✓ | ✓ |
| shutdown | - | ✓ |
| idle | - | ✓ |
| maxLoopTimes | - | ✓ |

### 2. States ThreadLocal 状态传递

```java
public class States {
    private static final ThreadLocal<State> CTX = new ThreadLocal<>();
    
    public static void set(State state) { CTX.set(state); }
    public static State get() { return CTX.get(); }
    public static LeadState lead() { return (LeadState) get(); }
    public static TeammateState teammate() { return (TeammateState) get(); }
    public static void clear() { CTX.remove(); }
}
```

### 3. ReActs 接口

```java
public interface ReActs {
    ChatCompletionMessageParam start(LeadState state);  // 领导同步执行
    void start(TeammateState state);                     // 队员异步执行
}
```

### 4. ReActsImpl 实现

```java
public class ReActsImpl implements ReActs {
    private final ThreadPoolExecutor theadPools = new ThreadPoolExecutor(0, 5,
            300, TimeUnit.SECONDS, new ArrayBlockingQueue<>(50),
            AiThreadFactory.create("loop", true));
    
    private final LeadReAct leadReAct;
    private final TeammateReAct teammateReAct;
    
    public ChatCompletionMessageParam start(LeadState state) {
        try {
            States.set(state);
            leadReAct.loop();
            return States.lead().getLastMessage();
        } finally {
            States.clear();
        }
    }
    
    public void start(TeammateState state) {
        theadPools.submit(() -> {
            try {
                States.set(state);
                teammateReAct.loop();
            } finally {
                States.clear();
            }
        });
    }
}
```

### 5. MessageBus 消息总线

```java
public class MessageBus {
    private Path getInboxPath(String to) {
        return FileUtils.resolve(States.get().getWorkDir(),
                String.format("inbox/%s.jsonl", to), true);
    }
    
    public void send(String to, InBoxMessage message) throws IOException {
        Path inboxPath = this.getInboxPath(to);
        FileUtils.write(inboxPath, JSON.toJSONString(message) + "\n");
    }
    
    public List<InBoxMessage> readInbox(String name, boolean clear) throws IOException {
        List<InBoxMessage> messages = FileUtils.readList(inboxPath, InBoxMessage.class);
        if (clear) {
            FileUtils.clear(inboxPath);  // 读取后清空
        }
        return messages;
    }
}
```

### 6. Team 团队管理

```java
public class Team {
    public void setTeammateIdle() { /* 更新状态为 idle */ }
    public void setTeammateShutdown() { /* 更新状态为 shutdown */ }
    public void setTeammateWorking() { /* 更新状态为 working */ }
    public String render() { /* 列出团队成员 */ }
    public List<String> getTeammateNames() { /* 获取成员名称列表 */ }
}
```

配置文件 `team/config.json`：

```json
{
  "teamName": "default",
  "teammates": [
    {"name": "alice", "role": "programmer", "status": "working"},
    {"name": "bob", "role": "tester", "status": "idle"}
  ]
}
```

### 7. Tool 工具体系

```java
public interface Tool extends LeadTool, TeammateTool {
    String name();
    ChatCompletionTool definition();
    String call(String arguments);
}

public abstract class BaseTool<T> implements Tool {
    private final String name;
    private final Class<T> tClass;
    private final ChatCompletionTool definition;
    
    @Override
    public String call(String arguments) {
        return this.doCall(JSON.parseObject(arguments, tClass));
    }
    
    public abstract String doCall(T arguments);
}
```

### 8. LeadReAct 领导循环

```java
public class LeadReAct {
    public void loop() {
        List<ChatCompletionMessageParam> messages = States.get().getMessages();
        while (true) {
            this.readInbox(messages);           // 读取收件箱
            ChatCompletionAssistantMessageParam assistantMessage = this.chat(messages);
            Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = assistantMessage.toolCalls();
            if (toolCallsOptional.isEmpty()) break;
            this.callTools(toolCallsOptional.get(), messages);
        }
    }
}
```

### 9. S09SpawnTeammateTool 创建队员

```java
public class S09SpawnTeammateTool extends BaseTool<SpawnTeammateToolArgs> {
    public String doCall(SpawnTeammateToolArgs arguments) {
        TeammateState state = new TeammateState();
        state.setName(name);
        state.setRole(role);
        state.setPrompt(String.format("你是 '%s', 角色: %s, 工作目录 %s...",
                name, role, workDir));
        state.setMaxLoopTimes(50);
        
        this.teammate(name, role);     // 注册到团队
        this.reActs.start(state);       // 异步启动
        return String.format("创建 '%s' (角色: %s)", name, role);
    }
}
```

## 执行流程图

```
1. Lead 调用 spawnTeammate("alice", "programmer", "编写代码")
           │
           ▼
2. 创建 TeammateState，设置 name/role/prompt
           │
           ▼
3. Team 注册 alice 到 team/config.json
           │
           ▼
4. ReActs.start() 提交到线程池异步执行
           │
           ▼
5. S09TeammateReAct.loop() 开始工作
           │
           ▼
6. Alice 可通过 sendMessage 与 Lead 通信
```

## 新增工具清单

| 工具 | 所有者 | 功能 |
|------|--------|------|
| spawnTeammate | Lead | 创建团队成员 |
| sendMessage | Both | 发送消息给队友 |
| readInbox | Both | 读取收件箱 |
| broadcast | Lead | 广播消息给所有队友 |
| listTeammates | Lead | 列出团队成员 |

## 相对 s08 的变更

| 组件 | s08 | s09 |
|------|-----|-----|
| Agent 模式 | 单 Agent | Lead + 多 Teammate |
| 通信方式 | - | MessageBus 收件箱 |
| 依赖注入 | 无 | Guice |
| 代码组织 | 单文件 | 组件化 |
| 执行方式 | 同步 | 同步 + 异步线程池 |

## 试试看

1. `生成 alice（程序员）和 bob（测试人员）`
2. `让 alice 给 bob 发送一条消息 "你好 Bob"`
3. `向所有队友广播 "状态更新：第一阶段已完成"`
4. `查看收件箱中的所有消息`

## 核心要义

> **"One Lead + Multiple Teammates, communicating via async inbox"**  
> 领导协调，队员协作，收件箱异步通信

**设计原则：**
- 组件化：职责分离，易于扩展
- ThreadLocal：跨线程传递状态
- 线程池：队员异步并行工作
- 文件存储：团队配置持久化

下篇预告：[S10TeamProtocols - 团队协议：优雅停止与方案审批](./S10TeamProtocols.md)
