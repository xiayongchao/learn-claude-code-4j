# S04Subagent - 子 Agent：每个子任务需要干净的上下文

## 核心理念

**"大任务拆小，每个小任务用干净上下文" -- Subagent 用独立 messages[]，不污染主对话。**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S04Subagent.java
- 原版：https://github.com/shareAI-lab/learn-claude-code
- 上篇：[S03TodoWrite - 任务规划](./S03TodoWrite.md)

## 上篇回顾

上篇文章我们实现了 TodoManager 和 Nag Reminder 机制，让模型能够追踪多步骤任务的进度。

## 问题

Agent 工作越久，messages 数组越胖。每次读文件、跑命令的输出都永久留在上下文里。

"这个项目用什么测试框架？" 可能要读 5 个文件，但父 Agent 只需要一个词："JUnit"。

子任务的中间过程对父任务来说是噪音。

## 解决方案

```
Parent agent                     Subagent
+------------------+             +------------------+
| messages=[...]   |             | messages=[]      | <-- fresh
|                  |  dispatch   |                  |
| tool: task       | ----------> | while tool_use:  |
|   prompt="..."   |             |   call tools     |
|                  |  summary    |   append results |
|   result = "..." | <---------- | return last text |
+------------------+             +------------------+

Parent context stays clean. Subagent context is discarded.
```

**核心思想：子 Agent 的消息历史在完成后丢弃，只返回摘要。**

## Java 实现详解

### 1. 工具配置：区分父子工具

```java
// 父 Agent 工具：包含 task 工具
private static final List<ChatCompletionTool> tools = List.of(
    Tools.bashTool(),
    Tools.readFileTool(),
    Tools.writeFileTool(),
    Tools.editFileTool(),
    Tools.taskTool()  // 子任务分发
);

// 子 Agent 工具：不包含 task 工具（禁止递归）
private static final List<ChatCompletionTool> childTools = List.of(
    Tools.bashTool(),
    Tools.readFileTool(),
    Tools.writeFileTool(),
    Tools.editFileTool()
);
```

**关键约束：子 Agent 不能有 task 工具，防止递归生成子 Agent。**

### 2. 系统提示：区分父子身份

```java
private static final String SYSTEM = 
    "你是工作目录 " + Commons.CWD + " 下的代码智能体，" +
    "请使用任务工具来分派代码调研工作或各类子任务";

private static final String CHILD_SYSTEM = 
    "你是运行在 " + Commons.CWD + " 工作目录下的编码子智能体。" +
    "完成指定任务后，请汇总你的调研结果与结论";
```

### 3. 工具注册

```java
private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();

static {
    TOOL_HANDLERS.put("bash", Tools::runBash);
    TOOL_HANDLERS.put("readFile", Tools::runReadFile);
    TOOL_HANDLERS.put("writeFile", Tools::runWriteFile);
    TOOL_HANDLERS.put("editFile", Tools::runEditFile);
    TOOL_HANDLERS.put("task", S04Subagent::runSubAgent);  // 子任务处理器
}
```

### 4. 子 Agent 执行器：runSubAgent

```java
public static String runSubAgent(String args) {
    String prompt = JSON.parseObject(args).getString("prompt");

    // 关键：子 Agent 用全新的消息列表
    List<ChatCompletionMessageParam> messages = new ArrayList<>();
    messages.add(ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder()
                    .content(prompt)
                    .build()
    ));

    ChatCompletionMessageParam lastMessage = null;
    for (int i = 0; i < 50; i++) {  // 安全限制：最多50轮
        List<ChatCompletionMessageParam> fullMessages = new ArrayList<>();
        fullMessages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder()
                        .content(CHILD_SYSTEM)
                        .build()
        ));
        fullMessages.addAll(messages);

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("qwen3.5-plus")
                .messages(fullMessages)
                .tools(childTools)  // 使用子 Agent 工具列表
                .build();

        ChatCompletion chatCompletion = Commons.getClient().chat().completions().create(params);
        ChatCompletionMessage message = chatCompletion.choices().get(0).message();

        lastMessage = ChatCompletionMessageParam.ofAssistant(message.toParam());
        messages.add(lastMessage);

        Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = message.toolCalls();
        if (toolCallsOptional.isEmpty()) {
            break;  // 无工具调用，子 Agent 结束
        }

        for (ChatCompletionMessageToolCall toolCall : toolCallsOptional.get()) {
            ChatCompletionMessageParam toolMessage = Tools.exe(TOOL_HANDLERS, toolCall);
            if (toolMessage != null) {
                messages.add(toolMessage);
            }
        }
    }

    // 只返回最终文本作为摘要
    String text = Commons.getText(lastMessage);
    return (text == null || text.isBlank()) ? "没有结果" : text;
}
```

### 5. 执行流程图

```
1. 父 Agent 调用 task 工具，传入子任务 prompt
          |
          v
2. runSubAgent 创建全新 messages 列表
          |
          v
3. 子 Agent 循环（最多50轮）：
   - 调用基础工具（bash, readFile, writeFile, editFile）
   - 不调用 task 工具（防止递归）
          |
          v
4. 子 Agent 完成后，返回最终文本作为摘要
          |
          v
5. 父 Agent 收到摘要，消息历史被丢弃（不污染父上下文）
```

## 关键设计

| 设计点 | 说明 |
|--------|------|
| 独立消息列表 | 子 Agent 创建全新的 `messages`，与父 Agent 完全隔离 |
| 安全限制 | 最多 50 轮循环，防止死循环 |
| 无 task 工具 | 子 Agent 工具列表不包含 task，禁止递归 |
| 仅返回摘要 | `messages` 历史被丢弃，只返回最终文本 |
| 不同系统提示 | 父子有不同的 SYSTEM，身份明确 |

## 相对 s03 的变更

| 组件 | s03 | s04 |
|------|-----|-----|
| Tools | 5 | 5 (基础) + task |
| 上下文 | 单一共享 | 父 + 子隔离 |
| Subagent | 无 | `runSubAgent()` 函数 |
| 返回值 | 不适用 | 仅摘要文本 |

## 试试看

1. `使用子任务查找该项目使用的测试框架`
2. `委派任务：读取所有 .java 文件并总结每个文件的作用`

## 核心要义

> **"Break big tasks down; each subtask gets a clean context"**  
> 子任务的中间过程是噪音，不是资产

**设计原则：**
- 隔离即清晰：子 Agent 的消息历史是子 Agent 的，不属于父 Agent
- 摘要即价值：父 Agent 只需要结果，不需要过程
- 禁止递归：子 Agent 不能生成子 Agent

下篇预告：[S05SkillLoading - 技能加载：用时再加载，不用不加载](./S05SkillLoading.md)
