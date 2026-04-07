package org.jc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 跨平台命令执行工具
 * 1. 自动适配 Windows cmd / Linux Mac bash
 * 2. 超时控制 + 强制销毁进程
 * 3. Linux sudo 提权支持
 * 4. 线程池异步回调
 * 5. 移除：日志脱敏逻辑
 * 6. 统一UTF-8、合并错误流防卡死
 */
public class Commands {

    private static final boolean IS_WINDOWS;
    private static final ExecutorService ASYNC_POOL;

    static {
        String os = System.getProperty("os.name").toLowerCase();
        IS_WINDOWS = os.contains("win");

        ASYNC_POOL = new ThreadPoolExecutor(
                2, 8, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(128),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("cmd-async-thread-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    // 同步默认超时
    public static CommandResult execSync(String command) {
        return execSync(command, 120 * 1000, false);
    }

    // 同步自定义超时
    public static CommandResult execSync(String command, long timeoutMs) {
        return execSync(command, timeoutMs, false);
    }

    /**
     * 同步执行核心
     *
     * @param command   原始命令
     * @param timeoutMs 超时毫秒
     * @param needSudo  是否sudo提权(Linux生效)
     */
    public static CommandResult execSync(String command, long timeoutMs, boolean needSudo) {
        return execSync(command, timeoutMs, needSudo, null);
    }

    public static CommandResult execSync(String command, long timeoutMs, boolean needSudo, java.nio.file.Path cwd) {
        List<String> cmdList = buildCommand(command, needSudo);
        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.redirectErrorStream(true);
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }

        Process process = null;
        StringBuilder outSb = new StringBuilder();
        int exitCode = -1;
        boolean timeoutFlag = false;

        try {
            process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                // 直接原样输出，无任何脱敏处理
                while ((line = reader.readLine()) != null) {
                    outSb.append(line).append(System.lineSeparator());
                }
            }

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (finished) {
                exitCode = process.exitValue();
            } else {
                timeoutFlag = true;
                process.destroyForcibly();
            }
        } catch (IOException | InterruptedException e) {
            outSb.append("执行异常: ").append(e.getMessage()).append(System.lineSeparator());
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
        }
        return new CommandResult(exitCode, outSb.toString(), timeoutFlag, IS_WINDOWS);
    }

    // 异步回调执行
    public static void execAsync(String command, long timeoutMs, boolean needSudo, CommandCallback callback) {
        ASYNC_POOL.submit(() -> {
            CommandResult result = execSync(command, timeoutMs, needSudo);
            if (result.getExitCode() == 0 && !result.isTimeout()) {
                callback.onSuccess(result);
            } else {
                callback.onFail(result);
            }
        });
    }

    public static void execAsync(String command, CommandCallback callback) {
        execAsync(command, 300_000, false, callback);
    }

    // 构建底层命令数组
    private static List<String> buildCommand(String rawCmd, boolean needSudo) {
        List<String> args = new ArrayList<>();
        if (IS_WINDOWS) {
            args.add("cmd");
            args.add("/c");
            args.add(rawCmd);
        } else {
            args.add("bash");
            args.add("-c");
            String finalCmd = needSudo ? "sudo " + rawCmd : rawCmd;
            args.add(finalCmd);
        }
        return args;
    }

    // 回调接口
    public interface CommandCallback {
        void onSuccess(CommandResult result);

        void onFail(CommandResult result);
    }

    // 返回结果实体
    public static class CommandResult {
        private final int exitCode;
        private final String output;
        private final boolean timeout;
        private final boolean windowsEnv;

        public CommandResult(int exitCode, String output, boolean timeout, boolean windowsEnv) {
            this.exitCode = exitCode;
            this.output = output;
            this.timeout = timeout;
            this.windowsEnv = windowsEnv;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getOutput() {
            return output;
        }

        public boolean isTimeout() {
            return timeout;
        }

        public boolean isWindowsEnv() {
            return windowsEnv;
        }
    }

    // 测试
    public static void main(String[] args) {
        String testCmd = IS_WINDOWS ? "ipconfig" : "df -h";
        CommandResult res = execSync(testCmd, 10000);
        System.out.println("退出码：" + res.getExitCode());
        System.out.println("原始输出：\n" + res.getOutput());
    }
}