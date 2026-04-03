# S10TeamProtocols - 团队协议：队友需要共同的通信规则

## 核心理念

**"队友需要共同的通信规则" -- Shutdown 协议 + Plan 协议，规范团队协作。**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S10TeamProtocols.java
- 原版：https://github.com/shareAI-lab/learn-claude-code
- 上篇：[S09AgentTeams - Agent 团队](./S09AgentTeams.md)

## 上篇回顾

上篇文章我们实现了团队 Agent 和消息队列，Agent 之间可以相互通信协作。

## 问题

团队协作需要规范：
- Agent 任务完成后如何优雅退出？
- 高风险操作（如重构）需要审批吗？
- 没有规则的团队会陷入混乱

**解决方案：引入团队协议，规范通信行为。**

## 解决方案

```
┌─────────────────────────────────────────────────────┐
│                   Team Protocols                      │
├─────────────────────────────────────────────────────┤
│  Shutdown Protocol  │  Plan Protocol                 │
│  ┌───────────────┐  │  ┌─────────────────┐           │
│  │shutdownRequest│──┼─>│   planSubmit    │           │
│  │  (Lead)      │  │  │   (Teammate)   │           │
│  └───────┬───────┘  │  └────────┬────────┘           │
│          │          │           │                     │
│          v          │           v                     │
│  ┌───────────────┐  │  ┌─────────────────┐           │
│  │shutdownResponse│<─┼──│  planReview    │           │
│  │  (Teammate)   │  │  │  (Lead)        │           │
│  └───────────────┘  │  └─────────────────┘           │
│                      │        │                       │
│                      │        v                       │
│                      │  ┌─────────────────┐          │
│                      │  │  planApproval   │          │
│                      │  │  (Teammate)    │          │
│                      │  └─────────────────┘          │
└─────────────────────────────────────────────────────┘
```

## Java 实现详解

### 1. 新增协议工具

```java
// Teammate 工具（新增 shutdownResponse, planApproval）
private static List<ChatCompletionTool> teammateTools = List.of(
    Tools.bashTool(),
    Tools.readFileTool(),
    Tools.writeFileTool(),
    Tools.editFileTool(),
    Tools.sendMessageTool(),
    Tools.readInboxTool(),
    Tools.teammateShutdownResponseTool(),  // 新增
    Tools.planApprovalTool()               // 新增
);

// Lead 工具（新增 shutdownRequest, shutdownResponse, planReview）
private static List<ChatCompletionTool> leadTools = List.of(
    Tools.bashTool(),
    Tools.readFileTool(),
    Tools.writeFileTool(),
    Tools.editFileTool(),
    Tools.spawnTeammateTool(),
    Tools.listTeammatesTool(),
    Tools.sendMessageTool(),
    Tools.readInboxTool(),
    Tools.broadcastTool(),
    Tools.shutdownRequestTool(),    // 新增
    Tools.leadShutdownResponseTool(), // 新增
    Tools.planReviewTool()          // 新增
);
```

### 2. 工具处理器注册

```java
private static ToolHandlers teammateToolHandlers = ToolHandlers.of()
    .add("bash", ...)
    .add("readFile", ...)
    .add("writeFile", ...)
    .add("editFile", ...)
    .add("sendMessage", ...)
    .add("readInbox", ...)
    .add("shutdownResponse", (ToolHandlers.TeammateToolCall) TeammateAgent::shutdownResponse)  // 新增
    .add("planApproval", (ToolHandlers.TeammateToolCall) TeammateAgent::planApproval);          // 新增

private static ToolHandlers leadToolHandlers = ToolHandlers.of()
    .add("bash", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runBash(arguments))
    .add("readFile", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runReadFile(arguments))
    .add("writeFile", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runWriteFile(arguments))
    .add("editFile", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runEditFile(arguments))
    .add("spawnTeammate", (ToolHandlers.LeadToolCall) (agent, arguments)
            -> agent.spawnTeammate(arguments, teammateTools, teammateToolHandlers
            , baseAgent -> String
                    .format("你是: %s, 角色: %s, 工作目录: %s"
                            , baseAgent.getName(), baseAgent.getRole()
                            , baseAgent.getConfig().getWorkDir())))
    .add("listTeammates", (ToolHandlers.LeadToolCall) (agent, arguments) -> agent.getTeam().listTeammate())
    .add("sendMessage", (ToolHandlers.LeadToolCall) Agent::sendMessage)
    .add("readInbox", (ToolHandlers.LeadToolCall) Agent::readInbox)
    .add("broadcast", (ToolHandlers.LeadToolCall) Agent::broadcast)
    .add("shutdownRequest", (ToolHandlers.LeadToolCall) Agent::shutdownRequest)
    .add("shutdownResponse", (ToolHandlers.LeadToolCall) Agent::shutdownResponse)
    .add("planReview", (ToolHandlers.LeadToolCall) Agent::planReview);
```

### 3. 协议说明

#### Shutdown Protocol（关闭协议）

| 工具 | 角色 | 说明 |
|------|------|------|
| `shutdownRequest` | Lead | 请求队友关闭 |
| `shutdownResponse` | Teammate | 同意/拒绝关闭 |
| `leadShutdownResponse` | Lead | 响应关闭确认 |

**流程：**
```
Lead                    Teammate
  |                         |
  |--- shutdownRequest ---->|
  |                         |
  |<-- shutdownResponse ----|
  |   (ready / busy)       |
```

#### Plan Protocol（计划协议）

| 工具 | 角色 | 说明 |
|------|------|------|
| `planReview` | Lead | 审核队友提交的计划 |
| `planApproval` | Teammate | 确认计划已批准 |

**流程：**
```
Teammate                Lead
  |                       |
  |--- 提出计划 ------------->|
  |                       |
  |<-- planReview -------|  (审核结果)
  |   (approve / reject) |
  |                       |
  |--- planApproval ----->|
  |   (确认执行)          |
```

### 4. 配置

```java
AgentConfig config = AgentConfig.of();
config.setReadInbox(true);
config.setWorkDir(Commons.CWD);
```

## 相对 s09 的变更

| 组件 | s09 | s10 |
|------|-----|-----|
| 关闭协议 | 无 | shutdownRequest / shutdownResponse |
| 计划协议 | 无 | planReview / planApproval |
| Teammate 工具 | 6 个 | 8 个 |
| Lead 工具 | 9 个 | 12 个 |

## 试试看

1. `先生成程序员 alice，然后你再发送关闭请求给她`
2. `列出队员，查看 alice 在关闭获批后的状态`
3. `生成 bob 并分配一项高风险重构任务。审核并拒绝他的计划`
4. `生成 charlie，让他提交一项计划，然后批准该计划`

## 核心要义

> **"Teammates need shared rules for communication"**  
> 协议即规则，规范即效率

**设计原则：**
- Shutdown 协议：优雅退出，状态确认
- Plan 协议：高风险操作，Lead 审批
- 同步锁：防止协议冲突

下篇预告：[S11TeamGuardrails - 安全护栏：防止 Agent 做傻事](./S11TeamGuardrails.md)
