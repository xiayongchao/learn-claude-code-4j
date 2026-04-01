# S06ContextCompact - 上下文压缩：上下文会满，你需要腾出空间

## 核心理念

**"上下文会满，你需要腾出空间" -- 三层压缩策略，实现无限会话。**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S06ContextCompact.java
- 原版：https://github.com/shareAI-lab/learn-claude-code
- 上篇：[S05SkillLoading - 技能加载](./S05SkillLoading.md)

## 上篇回顾

上篇文章我们实现了 SkillLoader，按需加载专业知识，避免上下文膨胀。

## 问题

Agent 工作越久，messages 数组越大。每次工具调用的结果都永久留在上下文里，直到超出模型的上下文窗口上限。

Claude Code 的解决方案：**三层压缩策略**，让无限会话成为可能。

## 解决方案：三层压缩

```
LLM 调用前
    |
    v
+---+ Layer 1: microCompact (每次调用前)
|   | 替换旧的 tool_result 为占位符
+---+
    | token 超过阈值?
    v
+---+ Layer 2: autoCompact (自动)
|   | 保存会话记录 + 总结 + 重建上下文
+---+
    | 模型调用 compact 工具?
    v
+---+ Layer 3: manual compact (手动)
|   | 用户或模型主动触发压缩
+---+
```

## Java 实现详解

### 1. 压缩触发条件

```java
private static final String SYSTEM = "你是运行在 " + Commons.CWD + " 工作目录下的编程智能体，请使用工具完成各项任务";
private static final int THRESHOLD = 3000;  // token 阈值
```

### 2. Token 估算：混合文本计算

```java
public static int estimateTokens(String text) {
    if (text == null || text.isBlank()) {
        return 0;
    }

    double tokenCount = 0;
    for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        // 中文：每个字符 1 token
        if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
            tokenCount++;
        } else {
            // 英文/数字/符号：每 4 个字符 ≈ 1 token
            tokenCount += 0.25;
        }
    }
    return (int) Math.ceil(tokenCount);
}
```

### 3. Layer 1：microCompact - 微观压缩

每次 LLM 调用前执行，将旧的 tool_result 替换为占位符：

```java
private static final int KEEP_RECENT = 3;  // 保留最近 3 条

public static void microCompact(List<ChatCompletionMessageParam> messages) {
    // 1. 收集所有 tool 消息
    List<ChatCompletionMessageParam> toolMessages = new ArrayList<>();
    for (ChatCompletionMessageParam message : messages) {
        if (message.isTool()) {
            toolMessages.add(message);
        }
    }
    if (toolMessages.size() <= KEEP_RECENT) {
        return;  // 不足 3 条，无需压缩
    }

    // 2. 建立 tool_call_id -> tool_name 映射
    Map<String, String> toolNameMap = new HashMap<>();
    for (ChatCompletionMessageParam message : messages) {
        if (!message.isAssistant()) continue;
        // ... 遍历 tool_calls 建立映射
    }

    // 3. 替换旧的 tool_result 为占位符
    List<ChatCompletionMessageParam> toClearMessages = Commons.getFirstN(toolMessages, KEEP_RECENT);
    for (ChatCompletionMessageParam message : toClearMessages) {
        // 保留最近 3 条，其余替换为：
        // "[上一步: 已使用 readFile]"
        String placeholder = String.format("[上一步: 已使用 %s]", toolName);
        // ... 构建新消息
    }
}
```

**效果：** `"file content of pom.xml... (5000 chars)"` → `"[上一步: 已使用 readFile]"`

### 4. Layer 2：autoCompact - 自动压缩

当 token 超过阈值时执行：

```java
public static void autoCompact(List<ChatCompletionMessageParam> messages) {
    try {
        // 1. 保存完整会话记录到磁盘
        Path transcriptDirPath = Paths.get(Commons.TRANSCRIPT_DIR);
        Files.createDirectories(transcriptDirPath);
        
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        Path transcriptPath = transcriptDirPath.resolve("transcript_" + timestamp + ".jsonl");
        
        List<String> lines = new ArrayList<>();
        for (ChatCompletionMessageParam msg : messages) {
            lines.add(Messages.toStandardJson(msg));
        }
        Files.write(transcriptPath, lines);
        System.out.println("[会话记录已保存: " + transcriptPath + "]");

        // 2. 让 LLM 总结对话
        String prompt = "对本次对话进行连贯总结，需包含："
                + "1）已完成事项；2）当前所处状态；3）达成的关键决策。"
                + "请保持总结简洁，保留关键细节。\n" + conversationText;

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("qwen3.5-plus")
                .messages(List.of(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder().content(prompt).build())))
                .maxCompletionTokens(2000)
                .build();

        ChatCompletion chatCompletion = Commons.getClient().chat().completions().create(params);
        String summary = Commons.getAssistantText(chatCompletion.choices().get(0).message().toParam());

        // 3. 重建上下文：只保留 2 条消息
        messages.clear();
        messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content("[对话已压缩。会话记录：" + transcriptPath + "]\n\n" + summary)
                        .build()));
        messages.add(ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .content("我已获取总结中的上下文，继续执行")
                        .build()));
    } catch (Exception e) {
        // 异常处理
    }
}
```

### 5. agentLoop 集成三层压缩

```java
public static void agentLoop(List<ChatCompletionMessageParam> messages) {
    while (true) {
        // Layer 1: 每次调用前微观压缩
        Compacts.microCompact(messages);
        
        // Layer 2: token 超过阈值则自动压缩
        if (Tokens.countDialogTokens(messages) > THRESHOLD) {
            System.out.println("[已触发自动压缩]");
            Compacts.autoCompact(messages);
        }
        
        // 正常 LLM 调用...
        ChatCompletion chatCompletion = Commons.getClient().chat().completions().create(params);
        messages.add(ChatCompletionMessageParam.ofAssistant(message.toParam()));
        
        // ... 工具执行 ...
        
        // Layer 3: 手动压缩（通过 compact 工具触发）
        if (manualCompact) {
            System.out.println("[手动压缩]");
            Compacts.autoCompact(messages);
        }
    }
}
```

### 6. compact 工具

```java
TOOL_HANDLERS.put("compact", args -> "已请求手动压缩");

// 模型调用 compact 工具时，设置标记
for (ChatCompletionMessageToolCall toolCall : toolCalls) {
    if (Tools.isCompactTool(toolCall)) {
        manualCompact = true;
        continue;  // 不执行，只标记
    }
    // ... 其他工具
}
```

## 三层压缩对比

| 层级 | 触发条件 | 作用 | 粒度 |
|------|----------|------|------|
| Layer 1: microCompact | 每次调用前 | 替换旧结果为占位符 | 消息级 |
| Layer 2: autoCompact | token > 3000 | 保存记录 + 总结 | 会话级 |
| Layer 3: manual | 模型调用 compact 工具 | 同 autoCompact | 手动触发 |

## 压缩效果示意

```
压缩前 (假设 500 条消息):
User: 读取所有 Java 文件...
Assistant: 我来读取...
Tool: readFile pom.xml 返回 2000 字符...
Tool: readFile App.java 返回 3000 字符...
Tool: readFile Service.java 返回 2500 字符...
... (更多工具调用)

压缩后 (2 条消息):
User: [对话已压缩。会话记录: transcript_123456.jsonl]
       总结：已完成读取 pom.xml(App 配置)、App.java(主类)、Service.java(业务逻辑)...
Assistant: 我已获取总结中的上下文，继续执行
```

## 相对 s05 的变更

| 组件 | s05 | s06 |
|------|-----|-----|
| 压缩 | 无 | 三层压缩策略 |
| Token 估算 | 无 | `Tokens.estimateTokens()` |
| 会话持久化 | 无 | `transcript_*.jsonl` |
| compact 工具 | 无 | 手动触发压缩 |

## 试试看

1. `逐一读取 agents/ 目录下的每个 java 文件`
2. `持续读取文件，直到压缩功能自动触发`
3. `使用 compact 工具手动压缩对话`

## 核心要义

> **"Context will fill up; you need a way to make room"**  
> 上下文有限，但会话可以无限

**设计原则：**
- 微观压缩：保留关键信息，去除冗余细节
- 自动压缩：阈值触发，无感知清理
- 手动压缩：用户/模型主动控制
- 会话持久化：压缩前保存，保留完整历史

下篇预告：[S07TaskSystem - 任务系统：大目标拆成小任务，持久化到磁盘](./S07TaskSystem.md)
