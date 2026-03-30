# S02ToolUse - 工具使用：扩展模型能触达的边界

## 核心理念

**"加一个工具，只加一个 handler" -- 循环不用动，新工具注册进 dispatch map 就行。**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S02ToolUse.java
- 原版：https://github.com/shareAI-lab/learn-claude-code
- 上篇：[S01AgentLoop - Agent 循环](./S01AgentLoop.md)

## 上篇回顾

上篇文章我们实现了最简单的 Agent Loop：只有 `bash` 一个工具，通过 while 循环持续调用模型、执行命令、返回结果。

## 问题

只有 `bash` 时，所有操作都走 shell：
- `cat` 截断不可预测
- `sed` 遇到特殊字符就崩
- 每次 bash 调用都是不受约束的安全风险

我们需要**专用工具**（`read_file`, `write_file`），可以在工具层面做路径沙箱。

**关键洞察：加工具不需要改循环。**

## 解决方案

```
+--------+      +-------+      +------------------+
|  User  | ---> |  LLM  | ---> | Tool Dispatch    |
| prompt |      |       |      | {                |
+--------+      +---+---+      |   bash: runBash |
                    ^           |   read: runRead |
                    |           |   write: runWr  |
                    +-----------+   edit: runEdit |
                    tool_result | }                |
                                +------------------+
```

Dispatch Map 替代 if/elif 链，一个查找替换所有判断。

## Java 实现详解

### 1. 工具处理器注册

```java
private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();

static {
    TOOL_HANDLERS.put("bash", Tools::runBash);
    TOOL_HANDLERS.put("readFile", Tools::runReadFile);
    TOOL_HANDLERS.put("writeFile", Tools::runWriteFile);
    TOOL_HANDLERS.put("editFile", Tools::runEditFile);
}
```

### 2. agentLoop 方法（与 s01 完全一致）

```java
private static final String SYSTEM = "你是工作目录 " + Commons.CWD + " 下的编程智能体，使用工具完成任务，直接执行、无需解释";

public static void agentLoop(List<ChatCompletionMessageParam> messages) {
    while (true) {
        List<ChatCompletionMessageParam> fullMessages = new ArrayList<>();
        fullMessages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder()
                        .content(SYSTEM)
                        .build()
        ));
        fullMessages.addAll(messages);

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("qwen3.5-plus")
                .messages(fullMessages)
                .tools(List.of(
                        Tools.bashTool(),
                        Tools.readFileTool(),
                        Tools.writeFileTool(),
                        Tools.editFileTool()
                ))
                .build();

        ChatCompletion chatCompletion = Commons.getClient().chat().completions().create(params);
        ChatCompletionMessage message = chatCompletion.choices().get(0).message();

        messages.add(ChatCompletionMessageParam.ofAssistant(message.toParam()));

        Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = message.toolCalls();
        if (toolCallsOptional.isEmpty()) {
            return;
        }

        for (ChatCompletionMessageToolCall toolCall : toolCallsOptional.get()) {
            ChatCompletionMessageParam toolMessage = Tools.exe(TOOL_HANDLERS, toolCall);
            if (toolMessage != null) {
                messages.add(toolMessage);
            }
        }
    }
}
```

**循环体与 s01 完全一致！只是 TOOLS 列表变长了。**

### 3. 路径安全：isSafePath 沙箱

```java
public static boolean isSafePath(String workDirPath, String path) {
    Path workDir = Paths.get(workDirPath).normalize();
    Path targetPath = workDir.resolve(path).normalize();

    return targetPath.startsWith(workDir);
}
```

防止 `../` 路径穿越，确保操作限制在工作目录内。

### 4. 工具实现

```java
// 读取文件
public static String runReadFile(String arguments) {
    JSONObject argument = JSON.parseObject(arguments);
    String path = argument.getString("path");
    Integer limit = argument.getInteger("limit");

    if (!Commons.isSafePath(Commons.CWD, path)) {
        return "路径超出工作区：" + path;
    }
    try {
        Path filePath = Paths.get(Commons.CWD).resolve(path);
        List<String> lines = Files.readAllLines(filePath);

        if (limit != null && limit < lines.size()) {
            List<String> showLines = lines.subList(0, limit);
            showLines.add("... (还有 " + (lines.size() - limit) + " 行)");
            lines = showLines;
        }

        String content = String.join("\n", lines);
        return content.length() > 50000 ? content.substring(0, 50000) : content;
    } catch (Exception e) {
        return "错误：" + e.getMessage();
    }
}

// 写入文件
public static String runWriteFile(String arguments) {
    JSONObject argument = JSON.parseObject(arguments);
    String path = argument.getString("path");
    String content = argument.getString("content");
    
    if (!Commons.isSafePath(Commons.CWD, path)) {
        return "错误：路径超出工作区：" + path;
    }

    try {
        Path filePath = Paths.get(Commons.CWD).resolve(path);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
        return "已写入 " + content.length() + " 字符";
    } catch (Exception e) {
        return "错误：" + e.getMessage();
    }
}

// 编辑文件
public static String runEditFile(String arguments) {
    JSONObject argument = JSON.parseObject(arguments);
    String path = argument.getString("path");
    String oldText = argument.getString("oldText");
    String newText = argument.getString("newText");
    
    if (!Commons.isSafePath(Commons.CWD, path)) {
        return "错误：路径超出工作区：" + path;
    }

    try {
        Path filePath = Paths.get(Commons.CWD).resolve(path);
        String fileContent = Files.readString(filePath);

        if (!fileContent.contains(oldText)) {
            return "错误：在 " + path + " 中未找到文本";
        }

        String updated = fileContent.replaceFirst(oldText, newText);
        Files.writeString(filePath, updated);
        return "已编辑 " + path;
    } catch (Exception e) {
        return "错误：" + e.getMessage();
    }
}
```

### 5. 工具定义示例

```java
public static ChatCompletionTool readFileTool() {
    Map<String, JsonValue> paramMap = new HashMap<>();
    paramMap.put("type", JsonValue.from("object"));

    Map<String, JsonValue> pathProp = new HashMap<>();
    pathProp.put("type", JsonValue.from("string"));
    pathProp.put("description", JsonValue.from("文件路径"));

    Map<String, JsonValue> properties = new HashMap<>();
    properties.put("path", JsonValue.from(pathProp));

    paramMap.put("properties", JsonValue.from(properties));
    paramMap.put("required", JsonValue.from(List.of("path")));

    FunctionParameters parameters = FunctionParameters.builder()
            .putAllAdditionalProperties(paramMap)
            .build();

    return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
            .function(FunctionDefinition.builder()
                    .name("readFile")
                    .description("读取文件内容")
                    .parameters(parameters)
                    .build())
            .build());
}
```

## 相对 s01 的变更

| 组件 | s01 | s02 |
|------|-----|-----|
| Tools | 1 (仅 bash) | 4 (bash, read, write, edit) |
| Dispatch | 硬编码调用 | `TOOL_HANDLERS` 字典 |
| 路径安全 | 无 | `isSafePath()` 沙箱 |
| Agent loop | - | **不变** |

## Python vs Java 对比

| 组件 | Python | Java |
|------|--------|------|
| 工具注册 | `{"bash": run_bash, ...}` | `TOOL_HANDLERS.put("bash", Tools::runBash)` |
| 工具列表 | `TOOLS = [{...}, {...}]` | `List.of(Tools.bashTool(), ...)` |
| 路径安全 | `path.is_relative_to(WORKDIR)` | `Commons.isSafePath(workDir, path)` |
| 循环体 | 不变 | 不变 |

## 试试看

1. `读取 pom.xml 文件`
2. `创建名为 greet.java 的文件，包含 greet(String name) 方法`
3. `编辑 greet.java，为该方法添加 Javadoc 文档字符串`
4. `读取 greet.java，验证修改是否生效`

## 核心要义

> **"Adding a tool means adding one handler"**  
> 工具即插即用，循环永远不变

```
加工具 = 加 handler + 加 schema + 加注册
      循环本身永远不变
```

下篇预告：[S03TodoWrite - 任务规划：没有计划的 Agent 会迷失方向](./S03TodoWrite.md)
