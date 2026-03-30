# S01AgentLoop - Agent 循环：模型与真实世界的第一道连接

## 核心理念

**"模型即 Agent，代码即 Harness（马具）"**

这个项目源自 [shareAI-lab/learn-claude-code](https://github.com/shareAI-lab/learn-claude-code)，旨在帮助 Java 程序员理解 AI Agent 的核心机制。不同于 Python 原版，本文用 Java 实现相同的 Agent Loop，让习惯 Java 的开发者也能深入学习。

## 问题背景

语言模型能推理代码，但它无法直接"触碰"真实世界——不能读文件、不能运行测试、不能查看错误日志。

没有 Agent Loop，每次工具调用后你都需要手动把结果粘回去。**你自己就是那个循环。**

## 解决方案：一个循环控制整个流程

```
+--------+      +-------+      +---------+
|  User  | ---> |  LLM  | ---> |  Tool   |
| prompt |      |       |      | execute |
+--------+      +---+---+      +----+----+
                    ^                |
                    |   tool_result  |
                    +----------------+
                    (loop until 模型不再调用工具)
```

## Java 实现详解

### 1. 入口：main 方法

```java
public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);
    List<ChatCompletionMessageParam> messages = new ArrayList<>();
    
    while (true) {
        System.out.print("请输入> ");
        String query = scanner.nextLine();
        if (List.of("q", "exit", "").contains(query.strip())) {
            break;
        }
        
        // 添加用户消息
        messages.add(ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder()
                .content(query)
                .build()
        ));
        
        // 启动 Agent 循环
        agentLoop(messages);
        
        // 输出最终回复
        System.out.println(">> " + Commons.getText(messages.get(messages.size() - 1)));
    }
}
```

### 2. 核心：agentLoop 方法

```java
public static void agentLoop(List<ChatCompletionMessageParam> messages) {
    while (true) {
        // 1. 构建完整消息列表（系统提示 + 历史消息）
        List<ChatCompletionMessageParam> fullMessages = new ArrayList<>();
        fullMessages.add(ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder()
                .content("你当前工作目录为 " + Commons.getCwd() + 
                         "，作为代码执行代理，使用 Bash 完成任务")
                .build()
        ));
        fullMessages.addAll(messages);
        
        // 2. 调用 LLM API
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("qwen3.5-plus")
                .messages(fullMessages)
                .tools(List.of(Commons.bashTool()))
                .build();
        
        ChatCompletion chatCompletion = Commons.getClient()
            .chat().completions().create(params);
        ChatCompletionMessage message = chatCompletion.choices().get(0).message();
        
        // 3. 将模型回复加入历史
        messages.add(ChatCompletionMessageParam.ofAssistant(message.toParam()));
        
        // 4. 检查是否有工具调用
        Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = message.toolCalls();
        if (toolCallsOptional.isEmpty()) {
            return;  // 无工具调用，结束循环
        }
        
        // 5. 执行工具调用
        for (ChatCompletionMessageToolCall toolCall : toolCallsOptional.get()) {
            ChatCompletionMessageParam toolMessage = Tools.exe(toolCall);
            if (toolMessage != null) {
                messages.add(toolMessage);  // 将工具结果发回给模型
            }
        }
    }
}
```

### 3. 工具注册：Tools 类

```java
public class Tools {
    private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();
    
    static {
        TOOL_HANDLERS.put("bash", Commons::runBash);
    }
    
    public static ChatCompletionMessageParam exe(ChatCompletionMessageToolCall toolCall) {
        // ... 执行工具并返回结果
    }
}
```

### 4. Bash 工具定义

```java
public static ChatCompletionTool bashTool() {
    Map<String, JsonValue> paramMap = new HashMap<>();
    paramMap.put("type", JsonValue.from("object"));
    
    Map<String, JsonValue> commandProp = new HashMap<>();
    commandProp.put("type", JsonValue.from("string"));
    commandProp.put("description", JsonValue.from("要执行的shell命令"));
    
    paramMap.put("properties", JsonValue.from(Map.of("command", JsonValue.from(commandProp))));
    paramMap.put("required", JsonValue.from(List.of("command")));
    
    FunctionParameters parameters = FunctionParameters.builder()
            .putAllAdditionalProperties(paramMap)
            .build();
    
    return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
            .function(FunctionDefinition.builder()
                    .name("bash")
                    .description("在当前工作区中运行 shell 命令")
                    .parameters(parameters)
                    .build())
            .build());
}
```

## Python vs Java 对比

| 组件 | Python | Java |
|------|--------|------|
| API 客户端 | `anthropic.Anthropic` | `OpenAIOkHttpClient` |
| 消息类型 | `{"role": "user", ...}` | `ChatCompletionMessageParam.ofUser()` |
| 工具定义 | `{"name": "bash", ...}` | `ChatCompletionTool` + `FunctionDefinition` |
| 停止判断 | `response.stop_reason != "tool_use"` | `message.toolCalls().isEmpty()` |
| 工具执行 | `run_bash(command)` | `Commons.runBash(args)` |

## 项目依赖

```xml
<dependency>
    <groupId>com.openai</groupId>
    <artifactId>openai-java</artifactId>
    <version>4.29.1</version>
</dependency>
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>fastjson</artifactId>
    <version>2.0.32</version>
</dependency>
```

## 试试看

1. 创建名为 `hello.java` 的文件，使其输出打印 "Hello, World!"
2. 列出当前目录下所有 Java 文件
3. 当前 Git 分支是什么？
4. 创建名为 `test_output` 的文件夹，并在其中新建 3 个文件

## 核心要义

> **"One loop & Bash is all you need"**  
> 一个工具 + 一个循环 = 一个 Agent

循环本身不变，后续所有机制（s02-s12）都在此基础上叠加：
- s02: 添加更多工具
- s03: 任务规划（TodoWrite）
- s04: 子 Agent 隔离
- s05: 技能加载
- ...

**模型决定何时调用工具，代码负责执行工具并返回结果。这就是 Agent 的全部秘密。**
