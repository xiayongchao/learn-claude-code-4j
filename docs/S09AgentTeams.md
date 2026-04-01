# S09AgentTeams - Agent 团队：任务太大，一个 Agent 忙不过来

## 核心理念

**"任务太大，一个 Agent 忙不过来" -- 团队协作 + 异步消息队列。**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S09AgentTeams.java
- 原版：https://github.com/shareAI-lab/learn-claude-code
- 上篇：[S08BackgroundTasks - 后台任务](./S08BackgroundTasks.md)

## 上篇回顾

上篇文章我们实现了后台任务执行，Agent 不再被慢操作阻塞。

## 问题

一个 Agent 能力有限：
- 复杂任务需要多角色协作（前端、后端、测试）
- 串行执行效率低，有些任务可以并行
- Agent 之间需要通信协调

**解决方案：团队 Agent + 消息队列。**

## 解决方案

```
                    Team Lead
                       |
        +--------------+---------------+
        |              |               |
   spawnTeammate   sendMessage    broadcast
        |              |               |
        v              v               v
   +--------+    +--------+      +--------+
   | alice  |    |  Bob   |      |  All   |
   | (前端) |    | (后端) |      |Members |
   +--------+    +--------+      +--------+
        |              |
        +-----> MessageBus <-------+
                     |
              inbox/bob.jsonl
```

## Java 实现详解

### 1. 团队角色划分

| 角色 | 描述 |
|------|------|
| Team Lead（主 Agent） | 负责任务分配、协调、汇总 |
| Teammate（队友 Agent） | 负责执行具体任务，相互通信 |

### 2. 工具注册

```java
// 主 Agent 工具（包含团队管理）
private static final List<ChatCompletionTool> tools = List.of(
    Tools.bashTool(),
    Tools.readFileTool(),
    Tools.writeFileTool(),
    Tools.editFileTool(),
    Tools.spawnTeammateTool(),   // 创建队友
    Tools.listTeammatesTool(),    // 列出队友
    Tools.sendMessageTool(),      // 发送消息
    Tools.readInboxTool(),        // 读取收件箱
    Tools.broadcastTool()          // 广播
);

// 队友 Agent 工具（不含 spawn，避免递归）
private static final List<ChatCompletionTool> teammateTools = List.of(
    Tools.bashTool(),
    Tools.readFileTool(),
    Tools.writeFileTool(),
    Tools.editFileTool(),
    Tools.sendMessageTool(),      // 可以发消息
    Tools.readInboxTool()         // 可以收消息
);
```

### 3. MessageBus：消息总线

```java
public class MessageBus {
    private static final Set<String> VALID_MSG_TYPES = new HashSet<>(
        Arrays.asList("message", "broadcast", "command", "response")
    );
    
    public String send(String sender, String to, String content,
                       String msgType, Map<String, Object> extra) {
        // 消息写入 inbox/{to}.jsonl
        double timestamp = System.currentTimeMillis() / 1000.0;
        Message msg = new Message(msgType, sender, content, timestamp, extra);
        
        Path inboxPath = dirPath.resolve(to + ".jsonl");
        String line = JSON.toJSONString(msg) + "\n";
        Files.write(inboxPath, line.getBytes(), 
            Files.exists(inboxPath) ? APPEND : CREATE);
        
        return "发送 " + msgType + " 给 " + to;
    }
    
    public List<Message> readInbox(String name, boolean json) {
        Path inboxPath = dirPath.resolve(name + ".jsonl");
        // 读取并清空
    }
    
    public String broadcast(String sender, List<String> teammates) {
        // 向所有队友广播
    }
}
```

### 4. TeammateManager：队友管理器

```java
public class TeammateManager {
    private final ConcurrentHashMap<String, Thread> threads = new ConcurrentHashMap<>();
    private final TeamConfig config;
    
    public String spawn(String name, String role, String prompt) {
        // 创建队友线程
        Thread thread = new Thread(() -> teammateLoop(name, role, prompt));
        thread.setDaemon(true);
        threads.put(name, thread);
        thread.start();
        return "创建 '" + name + "' (角色: " + role + ")";
    }
    
    private void teammateLoop(String name, String role, String prompt) {
        String sysPrompt = "你是 '" + name + "', 角色: " + role + ", 工作目录 " + workDir;
        List<ChatCompletionMessageParam> messages = new ArrayList<>();
        
        for (int i = 0; i < 50; i++) {
            // 1. 读取收件箱
            List<Message> inboxMessages = bus.readInbox(name, false);
            for (Message msg : inboxMessages) {
                messages.add(ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam.builder()
                        .content(JSON.toJSONString(msg)).build()));
            }
            
            // 2. 调用 LLM
            ChatCompletion chatCompletion = Commons.getClient()
                .chat().completions().create(params);
            
            // 3. 执行工具...
        }
        
        // 结束后设为 idle
        teammate.setStatus("idle");
    }
    
    public String list() {
        // 列出所有队友状态
    }
}
```

### 5. Message：消息实体

```java
public class Message {
    private String type;        // message, broadcast, command, response
    private String from;       // 发送者
    private String content;     // 内容
    private double timestamp;    // 时间戳
    private Map<String, Object> extra;  // 扩展字段
}
```

### 6. 团队配置持久化

```java
// config.json
{
  "teamName": "default",
  "teammates": [
    {"name": "alice", "role": "程序员", "status": "idle"},
    {"name": "bob", "role": "测试", "status": "working"}
  ]
}
```

### 7. agentLoop 集成

```java
private static final String SYSTEM = 
    "你是 " + Commons.CWD + " 工作目录下的团队负责人。" +
    "创建团队成员，并通过收件箱进行通信协作";

private static final MessageBus BUS = new MessageBus(Commons.INBOX_DIR);
private static final TeammateManager TEAM = new TeammateManager(
    teammateTools, TOOL_HANDLERS, Commons.TEAM_DIR, BUS, Commons.CWD
);

static {
    TOOL_HANDLERS.put("spawnTeammate", TEAM::spawn);
    TOOL_HANDLERS.put("listTeammates", args -> TEAM.list());
    TOOL_HANDLERS.put("sendMessage", BUS::send);
    TOOL_HANDLERS.put("readInbox", args -> JSON.toJSONString(BUS.readInbox(args, true)));
    TOOL_HANDLERS.put("broadcast", args -> BUS.broadcast(args, "lead", TEAM.memberNames()));
}
```

## 团队协作流程

```
1. Lead 调用 spawnTeammate("alice", "前端", "开发用户界面")
          |
          v
2. TeammateManager 启动 alice 线程
          |
          v
3. alice 运行自己的 agentLoop，读取收件箱
          |
          v
4. Lead 或其他队友调用 sendMessage(to="alice", content="任务详情")
          |
          v
5. alice 收到消息，处理并可能回复
          |
          v
6. Lead 读取 inbox 查看结果
```

## 消息类型

| 类型 | 说明 |
|------|------|
| message | 点对点消息 |
| broadcast | 广播给所有人 |
| command | 命令（如 "执行测试"） |
| response | 回复（如 "测试完成，结果如下"） |

## 相对 s08 的变更

| 组件 | s08 | s09 |
|------|-----|-----|
| 并发 | 后台任务单线程 | 团队多线程 |
| 通信 | 无 | MessageBus 异步队列 |
| 工具 | backgroundRun / checkBackground | spawnTeammate / sendMessage / broadcast |
| 持久化 | tasks/*.json | config.json + inbox/*.jsonl |

## 试试看

1. `生成 alice（程序员）和 bob（测试人员）。让 alice 给 bob 发送一条消息`
2. `向所有队友广播 "状态更新：第一阶段已完成"`
3. `查看负责人收件箱中的所有消息`

## 核心要义

> **"When the task is too big for one, delegate to teammates"**  
> 团队协作，异步通信

**设计原则：**
- 领导-成员模式：Lead 协调，Teammate 执行
- 消息总线：JSONL 文件实现异步队列
- 线程隔离：每个队友独立运行
- 持久化：团队配置和消息持久化

下篇预告：[S10TeamProtocols - 团队协议：队友需要共同的通信规则](./S10TeamProtocols.md)
