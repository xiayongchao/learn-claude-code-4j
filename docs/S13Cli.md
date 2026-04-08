# S13Cli - 智能体 CLI 工具

## 核心理念

**"将智能体封装为命令行工具，随时随地与之对话"**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S13Cli.java
- 上篇：[S12WorktreeTaskIsolation - 工作树与任务隔离](./S12WorktreeTaskIsolation.md)

## 上篇回顾

上篇文章我们实现了 Git Worktree 任务隔离，支持多任务并行开发。

## 问题

- 智能体需要通过 API 调用使用，不够便捷
- 每次对话需要初始化，不够高效
- 缺少统一入口，难以与现有工作流集成

**我们需要：一个随时可用的智能体命令行工具。**

## 解决方案

```
S13Cli 架构

┌─────────────────────────────────────┐
│           xia CLI 工具              │
│                                     │
│  xia start   - 启动服务（守护进程）  │
│  xia chat    - 对话                 │
│  xia status  - 查看状态             │
│  xia stop    - 停止服务             │
└─────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────┐
│        智能体服务（后台运行）        │
│                                     │
│  • ReAct 循环                       │
│  • 工具集（Bash/Read/Write/Edit）   │
│  • 消息历史管理                    │
└─────────────────────────────────────┘
```

## 核心组件详解

### 1. CLI 命令结构

使用 picocli 框架，支持子命令：

```java
@Command(
        name = "xia",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "Kimi 智能体 CLI 工具",
        subcommands = {
            S13Cli.StartCmd.class,
            S13Cli.StopCmd.class,
            S13Cli.StatusCmd.class,
            S13Cli.ChatCmd.class
        }
)
public class S13Cli extends AbstractModule {
    // ...
}
```

### 2. 服务启动命令

```java
@Command(name = "start", description = "启动智能体服务")
static class StartCmd implements Callable<Integer> {
    @Option(names = {"-d", "--daemon"}, defaultValue = "true")
    boolean daemon;

    @Override
    public Integer call() throws Exception {
        if (daemon) {
            // 使用 ProcessBuilder 启动 Java 进程
            ProcessBuilder pb = new ProcessBuilder(
                    javaBin,
                    "-cp", classpath,
                    S13Cli.class.getName(),
                    "start", "--daemon=false"
            );
            Process p = pb.start();
            // 等待服务启动
        } else {
            runServer();
        }
    }
}
```

使用方式：
```bash
xia start           # 后台守护进程模式
xia start --daemon=false  # 前台运行
```

### 3. 聊天命令（核心！）

```java
@Command(name = "chat", description = "和智能体聊天")
static class ChatCmd implements Callable<Integer> {
    @Parameters(arity = "0..1")
    String message;

    @Override
    public Integer call() {
        // 单次提问
        if (message != null && !message.isBlank()) {
            System.out.println("你: " + message);
            String reply = chat(message);
            System.out.println("智能体: " + reply);
            return 0;
        }

        // 连续对话模式
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("请输入> ");
            String input = scanner.nextLine().trim();
            if ("exit".equalsIgnoreCase(input)) break;
            String resp = chat(input);
            System.out.println(">> " + resp);
        }
    }
}
```

使用方式：
```bash
xia chat "你好"                    # 单次对话
xia chat                           # 进入连续对话模式
```

### 4. 消息历史管理

智能体服务启动后，会保存对话历史，支持上下文理解：

```java
private static final int MAX_MESSAGES = 20;

private String chat(String query) {
    // 添加用户消息
    List<ChatCompletionMessageParam> messages = state.getMessages();
    messages.add(ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder().content(query).build()
    ));

    // 超过阈值则裁剪，保持最近 N 条
    if (messages.size() > MAX_MESSAGES) {
        List<ChatCompletionMessageParam> trimmed = Commons.getLastN(messages, MAX_MESSAGES);
        state.setMessages(new ArrayList<>(trimmed));
    }

    // 执行 ReAct 循环
    ChatCompletionMessageParam last = reActs.start(state);
    return Commons.getText(last);
}
```

### 5. 服务状态管理

PID 文件记录服务进程 ID：

```java
public static final File PID_FILE = new File("xia.pid");

// 停止服务
@Command(name = "stop", description = "停止服务")
static class StopCmd implements Callable<Integer> {
    @Override
    public Integer call() {
        long pid = readPid();
        Process kill = isWindows
                ? new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid)).start()
                : new ProcessBuilder("kill", String.valueOf(pid)).start();
        kill.waitFor();
        PID_FILE.delete();
    }
}
```

### 6. Guice 依赖注入配置

```java
@Override
protected void configure() {
    // Lead 工具集
    Multibinder<LeadTool> leadToolBinder = Multibinder.newSetBinder(binder(), LeadTool.class);
    leadToolBinder.addBinding().to(BashTool.class);
    leadToolBinder.addBinding().to(ReadFileTool.class);
    leadToolBinder.addBinding().to(WriteFileTool.class);
    leadToolBinder.addBinding().to(EditFileTool.class);

    // 核心服务
    bind(OpenAIClient.class).toInstance(Commons.getQwenClient());
    bind(ReActs.class).to(ReActsImpl.class);
    bind(LeadReAct.class).in(Singleton.class);
    bind(TeammateReAct.class).to(DefaultTeammateReAct.class);
}
```

## 使用流程

```
完整使用流程：

1. 启动服务
   $ xia start
   智能体服务已后台启动，pid=12345

2. 单次对话
   $ xia chat "列出当前目录文件"
   智能体: [执行 ls 命令]

3. 连续对话
   $ xia chat
   请输入> 帮我创建一个文件 hello.txt
   >> 已创建...

4. 查看状态
   $ xia status
   状态：运行中，pid=12345

5. 停止服务
   $ xia stop
   服务已停止
```

## 服务初始化

全局单例模式确保只初始化一次：

```java
public static volatile Injector injector;
public static volatile ReActs reActs;
public static volatile LeadState state;

public static void initAgent() {
    if (injector == null) {
        injector = Guice.createInjector(new S13Cli());
        reActs = injector.getInstance(ReActs.class);

        state = LeadState.builder()
                .name("lead")
                .role("lead")
                .model("qwen3.5-plus")
                .prompt("你当前工作目录为 " + Commons.CWD + 
                        "，作为编程智能体，使用 Bash 完成任务，直接执行、无需解释")
                .workDir(Commons.CWD)
                .messages(new ArrayList<>())
                .build();
    }
}
```

## 相对 S12 的变更

| 组件 | S12 | S13 |
|------|-----|-----|
| 交互方式 | API 调用 | CLI 命令 |
| 服务模式 | 需手动管理 | 守护进程 |
| 状态管理 | 无 | PID 文件 |
| 消息历史 | 无 | 保留最近20条 |
| 连续对话 | 无 | 支持交互模式 |
| 入口统一 | 多个类 | 单一 CLI |

## 试试看

1. `xia start` 启动智能体服务
2. `xia chat "你好"` 测试单次对话
3. `xia chat` 进入连续对话模式，输入多条指令
4. `xia status` 查看服务状态
5. `xia stop` 停止服务

## 核心要义

> **"One CLI, instant access to your coding agent"**  
> 一个命令，随时访问你的编程智能体

**设计原则：**
- 守护进程：服务后台运行，随叫随到
- 子命令清晰：start/stop/status/chat
- 消息历史：保留上下文，理解连续对话
- PID 管理：正确识别运行状态

**设计亮点：**
- `picocli`：简洁的 CLI 命令定义
- `ProcessBuilder`：跨平台进程管理
- `Guice 单例`：服务只初始化一次
- `消息裁剪`：防止上下文过长