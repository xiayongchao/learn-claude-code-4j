package org.jc.agents;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.jc.Commons;
import org.jc.component.loop.*;
import org.jc.component.state.LeadState;
import org.jc.component.tool.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Command(
        name = "xia",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "Kimi 智能体 CLI 工具",
        subcommands = {S13Cli.StartCmd.class, S13Cli.StopCmd.class, S13Cli.StatusCmd.class, S13Cli.ChatCmd.class}
)
public class S13Cli extends AbstractModule {
    public static final File PID_FILE = new File("xia.pid");
    private static final int MAX_MESSAGES = 20;

    // =======================
    // 你原来的 Guice 配置
    // =======================
    @Override
    protected void configure() {
        Multibinder<LeadTool> leadToolBinder = Multibinder.newSetBinder(binder(), LeadTool.class);
        leadToolBinder.addBinding().to(BashTool.class);
        leadToolBinder.addBinding().to(ReadFileTool.class);
        leadToolBinder.addBinding().to(WriteFileTool.class);
        leadToolBinder.addBinding().to(EditFileTool.class);

        bind(OpenAIClient.class).toInstance(Commons.getQwenClient());
        bind(ReActs.class).to(ReActsImpl.class);
        bind(LeadReAct.class).in(Singleton.class);
        bind(TeammateReAct.class).to(DefaultTeammateReAct.class);
    }

    // =======================
    // 全局单例（服务启动后初始化一次）
    // =======================
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
                    .prompt("你当前工作目录为 " + Commons.CWD + "，作为编程智能体，使用 Bash 完成任务，直接执行、无需解释")
                    .workDir(Commons.CWD)
                    .messages(new ArrayList<>())
                    .build();
        }
    }

    // =======================
    // 主入口
    // =======================
    public static void main(String[] args) {
        int code = new CommandLine(new S13Cli()).execute(args);
        System.exit(code);
    }

    // ============================================================
    // 启动命令
    // ============================================================
    @Command(name = "start", description = "启动智能体服务")
    static class StartCmd implements Callable<Integer> {
        @Option(names = {"-d", "--daemon"}, defaultValue = "true")
        boolean daemon;

        @Override
        public Integer call() throws Exception {
            if (PID_FILE.exists()) {
                System.out.println("服务已启动，pid=" + readPid());
                return 1;
            }

            if (daemon) {
                String javaHome = System.getProperty("java.home");
                String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    javaBin += ".exe";
                }

                String classpath = System.getProperty("java.class.path");
                try {
                    String jarPath = ProcessBuilder.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI().getPath();
                    if (jarPath.endsWith(".jar")) {
                        classpath = jarPath;
                    }
                } catch (Exception ignored) {
                }

                ProcessBuilder pb = new ProcessBuilder(
                        javaBin,
                        "-cp", classpath,
                        S13Cli.class.getName(),
                        "start", "--daemon=false"
                );
                pb.redirectError(ProcessBuilder.Redirect.PIPE);
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                Process p = pb.start();

                try {
                    boolean started = false;
                    for (int i = 0; i < 10; i++) {
                        Thread.sleep(500);
                        if (PID_FILE.exists()) {
                            started = true;
                            break;
                        }
                    }

                    if (!started) {
                        String error = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                        if (!error.isBlank()) {
                            System.out.println("启动失败: " + error);
                        } else {
                            System.out.println("启动失败: 进程未能正常启动");
                        }
                        return 1;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return 1;
                }

                System.out.println("智能体服务已后台启动，pid=" + readPid());
            } else {
                runServer();
            }
            return 0;
        }

        // =======================
        // 你的业务真正运行在这里
        // =======================
        private void runServer() {
            try {
                long pid = ProcessHandle.current().pid();
                try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(PID_FILE), StandardCharsets.UTF_8))) {
                    w.println(pid);
                }

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    synchronized (this) {
                        this.notify();
                    }
                }));
                S13Cli.initAgent();
                System.out.println("智能体服务已启动成功，等待聊天请求...");

                // 阻塞，保持服务不退出
                synchronized (this) {
                    this.wait();
                }
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                System.out.println("写入PID文件失败：" + e.getMessage());
            } finally {
                PID_FILE.delete();
                System.out.println("服务已停止");
            }
        }
    }

    // ============================================================
    // 聊天命令（核心！）
    // ============================================================
    @Command(name = "chat", description = "和智能体聊天")
    static class ChatCmd implements Callable<Integer> {
        @Parameters(arity = "0..1")
        String message;

        @Override
        public Integer call() {
            if (!PID_FILE.exists()) {
                System.out.println("请先启动服务：xia start");
                return 1;
            }

            // 初始化智能体（确保只初始化一次）
            S13Cli.initAgent();

            // 单次提问
            if (message != null && !message.isBlank()) {
                System.out.println("你: " + message);
                String reply = chat(message);
                System.out.println("智能体: " + reply);
                return 0;
            }

            // 连续对话
            System.out.println("===== 进入智能体对话模式，输入 exit 退出 =====");
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("请输入> ");
                String input = scanner.nextLine().trim();

                if ("exit".equalsIgnoreCase(input)) {
                    System.out.println("对话结束");
                    break;
                }

                String resp = chat(input);
                System.out.println(">> " + resp);
            }
            scanner.close();
            return 0;
        }

        // =======================
        // 你原来的核心聊天逻辑
        // =======================
        private String chat(String query) {
            try {
                List<ChatCompletionMessageParam> messages = state.getMessages();
                messages.add(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder().content(query).build()
                ));

                if (messages.size() > MAX_MESSAGES) {
                    List<ChatCompletionMessageParam> trimmed = Commons.getLastN(messages, MAX_MESSAGES);
                    state.setMessages(new ArrayList<>(trimmed));
                } else {
                    state.setMessages(messages);
                }

                ChatCompletionMessageParam last = reActs.start(state);
                return Commons.getText(last);
            } catch (Exception e) {
                return "出错：" + e.getMessage();
            }
        }
    }

    // ============================================================
    // stop / status
    // ============================================================
    @Command(name = "stop", description = "停止服务")
    static class StopCmd implements Callable<Integer> {
        @Override
        public Integer call() {
            if (!PID_FILE.exists()) {
                System.out.println("服务未运行");
                return 0;
            }
            try {
                long pid = readPid();
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                Process kill = isWindows
                        ? new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid)).start()
                        : new ProcessBuilder("kill", String.valueOf(pid)).start();
                kill.waitFor();
                PID_FILE.delete();
                System.out.println("服务已停止");
            } catch (Exception e) {
                System.out.println("停止失败：" + e.getMessage());
                return 1;
            }
            return 0;
        }
    }

    @Command(name = "status", description = "查看状态")
    static class StatusCmd implements Callable<Integer> {
        @Override
        public Integer call() {
            if (!PID_FILE.exists()) {
                System.out.println("状态：未运行");
                return 0;
            }
            try {
                long pid = readPid();
                Process check = System.getProperty("os.name").toLowerCase().contains("win")
                        ? new ProcessBuilder("tasklist", "/FI", "PID eq " + pid).start()
                        : new ProcessBuilder("ps", "-p", String.valueOf(pid)).start();
                int code = check.waitFor();
                System.out.println(code == 0 ? "状态：运行中，pid=" + pid : "状态：已异常退出");
                if (code != 0) PID_FILE.delete();
            } catch (Exception e) {
                System.out.println("状态异常：" + e.getMessage());
            }
            return 0;
        }
    }

    // =======================
    // 工具方法
    // =======================
    private static long readPid() throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(PID_FILE))) {
            return Long.parseLong(br.readLine().trim());
        }
    }
}