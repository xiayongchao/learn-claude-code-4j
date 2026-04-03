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

## 代码演进

### 为什么重构？

早期版本用 `Map<String, Function<String, String>>` 管理工具：
- 代码散落在各 S0x 文件中
- agentLoop 逻辑重复
- 难以扩展团队、多 Agent 等复杂场景

### 新架构

```
Agent (基类)
├── readInbox()   - 读取收件箱
├── chat()        - 调用 LLM
├── toolCall()    - 执行工具
├── loop()        - 主循环
└── getLead()     - 获取 Lead Agent（供 Teammate 使用）

ToolHandlers (工具注册器)
├── LeadToolCall     - Lead Agent 专用
└── TeammateToolCall - TeammateAgent 专用

AgentConfig (配置)
├── readInbox
├── workDir
└── trackerLock

TeammateAgent (队友 Agent，继承 Agent)
```

### 关键设计

**1. 工具处理器分离**

Lead 和 Teammate 使用不同的接口，避免类型混乱：

```java
public interface LeadToolCall {
    String call(Agent agent, String arguments);
}

public interface TeammateToolCall {
    String call(TeammateAgent agent, String arguments);
}
```

**2. Teammate 通过 getLead() 访问 Lead**

```java
// 队友发送消息需要通过 Lead
.add("sendMessage", (agent, args) -> agent.getLead().sendMessage(args))
```

## Java 实现详解

### 1. 团队角色划分

| 角色 | 描述 |
|------|------|
| Team Lead（主 Agent） | 负责任务分配、协调、汇总 |
| Teammate（队友 Agent） | 负责执行具体任务，相互通信 |

### 2. 工具注册：ToolHandlers

```java
// 队友工具（不含 spawn，避免递归）
private static ToolHandlers teammateToolHandlers = ToolHandlers.of()
    .add("bash", (ToolHandlers.TeammateToolCall) (agent, args) -> Tools.runBash(args))
    .add("readFile", (ToolHandlers.TeammateToolCall) (agent, args) -> Tools.runReadFile(args))
    .add("writeFile", (ToolHandlers.TeammateToolCall) (agent, args) -> Tools.runWriteFile(args))
    .add("editFile", (ToolHandlers.TeammateToolCall) (agent, args) -> Tools.runEditFile(args))
    .add("sendMessage", (ToolHandlers.TeammateToolCall) (agent, args) -> agent.getLead().sendMessage(args))
    .add("readInbox", (ToolHandlers.TeammateToolCall) (agent, args) -> agent.getLead().readInbox(args));

// Lead 工具（包含团队管理）
private static ToolHandlers leadToolHandlers = ToolHandlers.of()
    .add("bash", (ToolHandlers.LeadToolCall) (agent, args) -> Tools.runBash(args))
    .add("readFile", (ToolHandlers.LeadToolCall) (agent, args) -> Tools.runReadFile(args))
    .add("writeFile", (ToolHandlers.LeadToolCall) (agent, args) -> Tools.runWriteFile(args))
    .add("editFile", (ToolHandlers.LeadToolCall) (agent, args) -> Tools.runEditFile(args))
    .add("spawnTeammate", (ToolHandlers.LeadToolCall) (agent, arguments)
            -> agent.spawnTeammate(arguments, teammateTools, teammateToolHandlers
            , baseAgent -> String
                    .format("你是: %s, 角色: %s, 工作目录: %s"
                            , baseAgent.getName(), baseAgent.getRole()
                            , baseAgent.getConfig().getWorkDir())))
    .add("listTeammates", (ToolHandlers.LeadToolCall) Agent::listTeammate)
    .add("sendMessage", (ToolHandlers.LeadToolCall) Agent::sendMessage)
    .add("readInbox", (ToolHandlers.LeadToolCall) Agent::readInbox)
    .add("broadcast", (ToolHandlers.LeadToolCall) Agent::broadcast);
```

### 3. Agent 配置与调用

```java
AgentConfig config = AgentConfig.of();
config.setReadInbox(true);
config.setWorkDir(Commons.CWD);

Agent agent = Agent.of();
agent.setName(LEAD);
agent.setModel(QWEN_3_5_PLUS);
agent.setPromptProvider(baseAgent -> "你是 " + Commons.CWD + " 工作目录下的团队负责人。创建团队成员，并通过收件箱进行通信协作");
agent.setBus(bus);
agent.setTeam(team);
agent.setTools(leadTools);
agent.setToolHandlers(leadToolHandlers);
agent.setConfig(config);

ChatCompletionMessageParam last = agent.loop(messages);
```

### 4. MessageBus：消息总线

消息写入 `inbox/{收件人}.jsonl` 文件，实现异步队列：

```java
public class MessageBus {
    public String send(String sender, String to, String content, String msgType, Map<String, Object> extra) {
        Message msg = new Message(msgType, sender, content, timestamp, extra);
        Path inboxPath = dirPath.resolve(to + ".jsonl");
        Files.write(inboxPath, line.getBytes(), APPEND);
        return "发送 " + msgType + " 给 " + to;
    }

    public List<Message> readInbox(String name, boolean json) {
        // 读取并清空 inbox
    }

    public String broadcast(String sender, List<String> teammates) {
        // 向所有队友广播
    }
}
```

### 5. 消息类型

| 类型 | 说明 |
|------|------|
| message | 点对点消息 |
| broadcast | 广播给所有人 |
| command | 命令（如 "执行测试"） |
| response | 回复（如 "测试完成，结果如下"） |

## 团队协作流程

```
1. Lead 调用 spawnTeammate("alice", "前端", "开发用户界面")
          |
          v
2. TeammateAgent 启动 alice 线程
          |
          v
3. alice 运行自己的 loop，读取收件箱
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

## 相对 s08 的变更

| 组件 | s08 | s09 |
|------|-----|-----|
| 架构 | 内联 agentLoop | Agent 框架 |
| 工具注册 | `Map<String, Function>` | `ToolHandlers` |
| 团队管理 | 无 | `Team` |
| 消息通信 | 无 | `MessageBus` |
| 配置文件 | 无 | `AgentConfig` |
| 并发 | 后台任务单线程 | 团队多线程 |
| 持久化 | tasks/*.json | config.json + inbox/*.jsonl |
| 新增工具 | backgroundRun / checkBackground | spawnTeammate / sendMessage / broadcast / readInbox |

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
