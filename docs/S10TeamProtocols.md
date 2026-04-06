# S10TeamProtocols - 团队协议：优雅停止 + 方案审批

## 核心理念

**"优雅停止 + 方案审批 -- 团队协作的规范化流程。"**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S10TeamProtocols.java
- 上篇：[S09AgentTeams - Agent 团队](./S09AgentTeams.md)

## 上篇回顾

上篇文章我们实现了多 Agent 团队架构，支持 Lead 创建 Teammate 并通过收件箱通信。

## 问题

团队协作需要规范流程：
1. **停止协议**：不能强制终止队员，需要优雅关闭
2. **审批协议**：重要工作需要 Lead 审批方案

**我们需要：团队协议机制。**

## 解决方案

```
停止协议流程：
┌──────────┐    shutdownRequest    ┌──────────┐
│   Lead   │ ─────────────────────>│ Teammate │
└──────────┘                       └────┬─────┘
     ▲                                    │
     │                         shutdownResponse
     │  approve=true/false                │
     └────────────────────────────────────┘

方案审批流程：
┌──────────┐    planApproval      ┌──────────┐
│ Teammate │ ─────────────────────>│   Lead   │
└──────────┘                       └────┬─────┘
     ▲                                    │
     │                         planReview
     │  approve=true/false + feedback     │
     └────────────────────────────────────┘
```

## 协议详解

### 1. 停止协议 (Shutdown Protocol)

#### ShutdownRequests 管理器

```java
public class ShutdownRequests {
    private Map<String, ShutdownRequest> requests = Maps.newConcurrentMap();
    
    public void put(String requestId, ShutdownRequest request) {
        this.requests.put(requestId, request);
    }
    
    public ShutdownRequest get(String requestId) {
        return this.requests.get(requestId);
    }
}
```

#### ShutdownRequestTool - 发起停止请求

```java
public class ShutdownRequestTool extends BaseTool<ShutdownRequestToolArgs> {
    public String doCall(ShutdownRequestToolArgs arguments) {
        String teammate = arguments.getTeammate();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        
        // 1. 创建停止请求
        this.shutdownRequests.put(requestId, new ShutdownRequest(teammate,
                ShutdownRequestStatus.PENDING.getValue()));
        
        // 2. 发送停止消息
        InBoxMessage message = new InBoxMessage();
        message.setType(InBoxMsgType.SHUTDOWN_REQUEST.getValue());
        message.setFrom(States.get().getName());
        message.setExtra(Map.of("requestId", requestId));
        message.setContent("请优雅地停止你当前的工作");
        
        this.bus.send(teammate, message);
        return String.format("已向 '%s' 发送停止请求: %s", teammate, requestId);
    }
}
```

#### ShutdownResponseTool - 响应停止请求

```java
public class ShutdownResponseTool extends BaseTool<ShutdownResponseToolArgs> {
    public String doCall(ShutdownResponseToolArgs arguments) {
        String requestId = arguments.getRequestId();
        Boolean approve = arguments.getApprove();
        
        // 1. 更新请求状态
        ShutdownRequest shutdownRequest = this.shutdownRequests.get(requestId);
        if (shutdownRequest != null) {
            shutdownRequest.setStatus(approve ? "approved" : "rejected");
        }
        
        // 2. 发送响应消息
        InBoxMessage message = new InBoxMessage();
        message.setType(InBoxMsgType.SHUTDOWN_RESPONSE.getValue());
        message.setFrom(States.get().getName());
        message.setExtra(Map.of("requestId", requestId, "approve", approve));
        
        // 3. 批准则设置停止标志
        if (Boolean.TRUE.equals(approve)) {
            States.teammate().setShutdown(true);
        }
        
        return String.format("停止请求(requestId=%s)已%s",
                requestId, approve ? "批准" : "拒绝");
    }
}
```

#### ShutdownCheckTool - 检查停止状态

```java
public class ShutdownCheckTool extends BaseTool<ShutdownCheckToolArgs> {
    public String doCall(ShutdownCheckToolArgs arguments) {
        String requestId = arguments.getRequestId();
        return JSON.toJSONString(this.shutdownRequests.get(requestId));
    }
}
```

### 2. 方案审批协议 (Plan Approval Protocol)

#### PlanRequests 管理器

```java
public class PlanRequests {
    private Map<String, PlanRequest> requests = Maps.newConcurrentMap();
    
    public void put(String requestId, PlanRequest request) {
        this.requests.put(requestId, request);
    }
    
    public PlanRequest get(String requestId) {
        return this.requests.get(requestId);
    }
}
```

#### PlanApprovalTool - 提交方案

```java
public class PlanApprovalTool extends BaseTool<PlanApprovalToolArgs> {
    public String doCall(PlanApprovalToolArgs arguments) {
        String plan = arguments.getPlan();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        
        // 1. 创建方案请求
        this.planRequests.put(requestId, new PlanRequest(
                States.get().getName(), plan, "pending"));
        
        // 2. 发送方案消息
        InBoxMessage message = new InBoxMessage();
        message.setType(InBoxMsgType.PLAN_APPROVAL_RESPONSE.getValue());
        message.setFrom(States.get().getName());
        message.setExtra(Map.of("requestId", requestId, "plan", plan));
        message.setContent(plan);
        
        this.bus.send(States.teammate().getLead(), message);
        return String.format("计划已提交（request_id=%s），正在等待负责人审批", requestId);
    }
}
```

#### PlanReviewTool - 审核方案

```java
public class PlanReviewTool extends BaseTool<PlanReviewToolArgs> {
    public String doCall(PlanReviewToolArgs arguments) {
        String requestId = arguments.getRequestId();
        Boolean approve = arguments.getApprove();
        String feedback = arguments.getFeedback();
        
        // 1. 更新方案状态
        PlanRequest planRequest = this.planRequests.get(requestId);
        planRequest.setStatus(approve ? "approved" : "rejected");
        
        // 2. 发送审核结果
        InBoxMessage message = new InBoxMessage();
        message.setType(InBoxMsgType.PLAN_APPROVAL_RESPONSE.getValue());
        message.setFrom(States.get().getName());
        message.setExtra(Map.of("requestId", requestId, "approve", approve, "feedback", feedback));
        message.setContent(feedback);
        
        this.bus.send(planRequest.getFrom(), message);
        return String.format("已经把 %s 的计划状态设置为：%s", planRequest.getFrom(), planRequest.getStatus());
    }
}
```

### 3. 消息类型扩展

```java
public enum InBoxMsgType {
    MESSAGE("message"),
    BROADCAST("broadcast"),
    SHUTDOWN_REQUEST("shutdown_request"),      // 新增
    SHUTDOWN_RESPONSE("shutdown_response"),    // 新增
    PLAN_APPROVAL_RESPONSE("plan_approval_response");  // 新增
}
```

### 4. S10TeammateReAct 循环

```java
public class S10TeammateReAct implements TeammateReAct {
    public void loop() {
        List<ChatCompletionMessageParam> messages = States.teammate().getMessages();
        messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(States.teammate().getUserPrompt())
                        .build()));
        
        Integer maxLoopTimes = States.teammate().getMaxLoopTimes();
        for (int i = 0; i < maxLoopTimes; i++) {
            this.readInbox(messages);
            
            // 检查停止标志
            if (States.teammate().isShutdown()) {
                break;
            }
            
            ChatCompletionAssistantMessageParam assistantMessage = this.chat(messages);
            Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = assistantMessage.toolCalls();
            if (toolCallsOptional.isEmpty()) break;
            
            this.callTools(toolCallsOptional.get(), messages);
        }
        
        // 重置员工状态
        this.team.resetTeammateStatusAfterTurn();
    }
}
```

### 5. Team 状态重置

```java
public class Team {
    public void writeTeamConfig(TeamConfig teamConfig) {
        Path teamConfigPath = FileUtils.resolve(workDir, "team/config.json", true, true);
        FileUtils.write(teamConfigPath, teamConfig);
    }
    
    public TeamConfig readTeamConfig() {
        Path teamConfigPath = FileUtils.resolve(workDir, "team/config.json", true, true);
        return FileUtils.read(teamConfigPath, TeamConfig.class);
    }
    
    public void resetTeammateStatusAfterTurn() {
        Teammate teammate = findTeammate(States.get().getName());
        
        if (TeammateStatus.SHUTDOWN.is(teammate.getStatus())) {
            return;  // 已停止，不更新
        }
        
        if (States.teammate().isShutdown()) {
            // 需要停止 → 设置为 shutdown
            teammate.setStatus(TeammateStatus.SHUTDOWN.getValue());
        } else {
            // 正常结束 → 设置为 idle
            teammate.setStatus(TeammateStatus.IDLE.getValue());
        }
        
        this.writeTeamConfig(teamConfig);
    }
}
```

## 执行流程图

### 停止协议时序

```
1. Lead 调用 shutdownRequest("alice")
           │
           ▼
2. ShutdownRequests 创建待处理请求
           │
           ▼
3. MessageBus 发送 SHUTDOWN_REQUEST 给 alice
           │
           ▼
4. alice 收件箱收到消息，调用 shutdownResponse
           │
           ▼
5. Lead 调用 shutdownCheck 查看状态
```

### 方案审批时序

```
1. alice 调用 planApproval("重构订单模块...")
           │
           ▼
2. PlanRequests 创建待审批方案
           │
           ▼
3. MessageBus 发送方案给 Lead
           │
           ▼
4. Lead 调用 planReview 审核
           │
           ▼
5. alice 收到批准/拒绝反馈，继续或修改方案
```

## 新增工具清单

| 工具 | 所有者 | 功能 |
|------|--------|------|
| shutdownRequest | Lead | 发起停止请求 |
| shutdownResponse | Teammate | 响应停止请求 |
| shutdownCheck | Lead | 检查停止状态 |
| planApproval | Teammate | 提交方案 |
| planReview | Lead | 审核方案 |

## Guice 配置

```java
public class S10TeamProtocols extends AbstractModule {
    @Override
    protected void configure() {
        // Lead 工具
        Multibinder<LeadTool> leadToolBinder = Multibinder.newSetBinder(binder(), LeadTool.class);
        // ... 基础工具
        leadToolBinder.addBinding().to(ShutdownRequestTool.class);
        leadToolBinder.addBinding().to(ShutdownCheckTool.class);
        leadToolBinder.addBinding().to(PlanReviewTool.class);
        
        // Teammate 工具
        Multibinder<TeammateTool> teammateToolBinder = Multibinder.newSetBinder(binder(), TeammateTool.class);
        // ... 基础工具
        teammateToolBinder.addBinding().to(ShutdownResponseTool.class);
        teammateToolBinder.addBinding().to(PlanApprovalTool.class);
        
        // 核心服务
        bind(ShutdownRequests.class).in(Singleton.class);
        bind(PlanRequests.class).in(Singleton.class);
        
        bind(TeammateReAct.class).to(S10TeammateReAct.class);
    }
}
```

## 相对 s09 的变更

| 组件 | s09 | s10 |
|------|-----|-----|
| 停止机制 | 无 | shutdownRequest/Response |
| 方案审批 | 无 | planApproval/Review |
| 状态管理 | 基本 | 请求管理器 |
| Teammate 循环 | 简单循环 | 支持停止检测 |
| 团队状态 | 基本 | 完整状态机 |

## 试试看

1. `生成程序员 alice，然后发送关闭请求给她`
2. `列出队员，查看 alice 在关闭获批后的状态`
3. `生成 bob 并分配一项高风险重构任务`
4. `审核并拒绝他的计划，然后生成 charlie 让他提交方案再批准`

## 核心要义

> **"Graceful shutdown + Plan approval -- Standardized team protocols"**  
> 优雅停止，方案审批，团队协作规范化

**设计原则：**
- 双向确认：停止和审批都需要双方确认
- 状态追踪：请求管理器维护协议状态
- 消息扩展：InBoxMsgType 支持多种消息类型
- 循环退出：shutdown 标志控制循环退出

下篇预告：[S11AutonomousAgents - 自主 Agent：自动寻找任务，主动工作](./S11AutonomousAgents.md)
