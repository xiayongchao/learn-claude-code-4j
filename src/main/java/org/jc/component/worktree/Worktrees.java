package org.jc.component.worktree;

import com.alibaba.fastjson2.JSON;
import com.google.inject.Inject;
import org.jc.Commands;
import org.jc.component.state.States;
import org.jc.component.task.Tasks;
import org.jc.component.util.FileUtils;
import org.jc.component.enums.TaskStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class Worktrees {
    private static final Set<String> DANGEROUS = new HashSet<>(Set.of("rm -rf /", "sudo", "shutdown", "reboot", "> /dev/"));
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9._-]{1,40}");

    private final Path repoRoot;
    private final Path worktreesDir;
    private final Path indexPath;
    private final Tasks tasks;
    private final EventBus events;
    private final boolean gitAvailable;

    @Inject
    public Worktrees(Tasks tasks, EventBus events) {
        this.repoRoot = detectRepoRoot();
        this.gitAvailable = isGitRepo();
        this.tasks = tasks;
        this.events = events;

        Path basePath = repoRoot != null ? repoRoot : Paths.get(States.get().getWorkDir());
        Path wd;
        try {
            wd = FileUtils.resolve(basePath, ".worktrees", false, true);
        } catch (Exception e) {
            wd = basePath.resolve(".worktrees");
        }
        this.worktreesDir = wd;
        this.indexPath = worktreesDir.resolve("index.json");

        initIndex();
    }

    private Path detectRepoRoot() {
        try {
            Commands.CommandResult r = Commands.execSync("git rev-parse --show-toplevel", 10000);
            if (r.getExitCode() == 0) {
                String root = r.getOutput().trim();
                Path p = Paths.get(root);
                if (Files.exists(p)) {
                    return p;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isGitRepo() {
        try {
            Commands.CommandResult r = Commands.execSync("git rev-parse --is-inside-work-tree", 10000);
            return r.getExitCode() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void initIndex() {
        try {
            if (!Files.exists(indexPath)) {
                Files.createDirectories(worktreesDir);
                Files.writeString(indexPath, JSON.toJSONString(Map.of("worktrees", new ArrayList<>())));
            }
        } catch (Exception ignored) {
        }
    }

    private IndexData loadIndex() {
        try {
            String content = Files.readString(indexPath);
            return JSON.parseObject(content, IndexData.class);
        } catch (Exception ignored) {
            return new IndexData();
        }
    }

    private void saveIndex(IndexData index) {
        try {
            Files.writeString(indexPath, JSON.toJSONString(index));
        } catch (Exception ignored) {
        }
    }

    private Worktree find(String name) {
        IndexData idx = loadIndex();
        for (Worktree wt : idx.getWorktrees()) {
            if (Objects.equals(wt.getName(), name)) {
                return wt;
            }
        }
        return null;
    }

    private void validateName(String name) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid worktree name. Use 1-40 chars: letters, numbers, ., _, -");
        }
    }

    public String create(String name, Integer taskId, String baseRef) {
        // ====================== 第一步：统一处理默认值
        String finalBaseRef = (baseRef == null || baseRef.isBlank()) ? "HEAD" : baseRef;

        validateName(name);
        if (find(name) != null) {
            throw new IllegalArgumentException("Worktree '" + name + "' already exists in index");
        }
        if (taskId != null && !tasks.exists(taskId)) {
            throw new IllegalArgumentException("Task " + taskId + " not found");
        }

        Map<String, Object> taskMap = new HashMap<>();
        taskMap.put("id", taskId);

        events.emit("worktree.create.before", taskMap, Map.of("name", name, "base_ref", finalBaseRef));

        try {
            Path wtPath = worktreesDir.resolve(name);
            String branch = "wt/" + name;

            // ====================== 终极修复：最简、最兼容的 git 命令 ======================
            runGit("worktree", "add", "-b", branch, wtPath.toString(), finalBaseRef);

            Worktree entry = new Worktree();
            entry.setName(name);
            entry.setPath(wtPath.toString());
            entry.setBranch(branch);
            entry.setTaskId(taskId);
            entry.setStatus("active");
            entry.setCreatedAt(System.currentTimeMillis() / 1000.0);

            IndexData idx = loadIndex();
            idx.getWorktrees().add(entry);
            saveIndex(idx);

            if (taskId != null) {
                tasks.bindWorktree(taskId, name);
            }

            events.emit("worktree.create.after", taskMap, Map.of(
                    "name", name,
                    "path", wtPath.toString(),
                    "branch", branch,
                    "status", "active"));

            return JSON.toJSONString(entry);
        } catch (Exception e) {
            Map<String, Object> worktree = new HashMap<>();
            worktree.put("name", name);
            worktree.put("base_ref", finalBaseRef);
            events.emit("worktree.create.failed", taskMap, worktree, e.getMessage());
            throw new RuntimeException("Create worktree failed: " + e.getMessage(), e);
        }
    }

    public String list() {
        IndexData idx = loadIndex();
        List<Worktree> wts = idx.getWorktrees();
        if (wts == null || wts.isEmpty()) {
            return "No worktrees in index.";
        }

        List<String> lines = new ArrayList<>();
        for (Worktree wt : wts) {
            String suffix = wt.getTaskId() != null ? " task=" + wt.getTaskId() : "";
            String status = wt.getStatus() != null ? wt.getStatus() : "unknown";
            lines.add("[" + status + "] " + wt.getName() + " -> " + wt.getPath() + " (" + wt.getBranch() + ")" + suffix);
        }
        return String.join("\n", lines);
    }

    public String status(String name) {
        Worktree wt = find(name);
        if (wt == null) {
            return "Error: Unknown worktree '" + name + "'";
        }

        Path path = Paths.get(wt.getPath());
        if (!Files.exists(path)) {
            return "Error: Worktree path missing: " + path;
        }

        Commands.CommandResult r = Commands.execSync("git status --short --branch", 60000, false, path);
        String text = r.getOutput().trim();
        return text.isEmpty() ? "Clean worktree" : text;
    }

    public String run(String name, String command) {
        if (DANGEROUS.stream().anyMatch(command::contains)) {
            return "Error: Dangerous command blocked";
        }

        Worktree wt = find(name);
        if (wt == null) {
            return "Error: Unknown worktree '" + name + "'";
        }

        Path path = Paths.get(wt.getPath());
        if (!Files.exists(path)) {
            return "Error: Worktree path missing: " + path;
        }

        try {
            Commands.CommandResult r = Commands.execSync(command, 300000, false, path);
            String out = r.getOutput().trim();
            return out.isEmpty() ? "(no output)" : (out.length() > 50000 ? out.substring(0, 50000) : out);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public String remove(String name, boolean force, boolean completeTask) {
        Worktree wt = find(name);
        if (wt == null) {
            return "Error: Unknown worktree '" + name + "'";
        }

        // ====================== 关键修复：校验路径不能为空 ======================
        String wtPath = wt.getPath();
        if (wtPath == null || wtPath.isBlank()) {
            String msg = "Error: Worktree '" + name + "' has no path in index";
            events.emit("worktree.remove.failed",
                    wt.getTaskId() != null ? Map.of("id", wt.getTaskId()) : new HashMap<>(),
                    Map.of("name", name),
                    msg);
            throw new RuntimeException(msg);
        }

        Map<String, Object> taskMap = wt.getTaskId() != null ? Map.of("id", wt.getTaskId()) : new HashMap<>();
        events.emit("worktree.remove.before", taskMap, Map.of("name", name, "path", wtPath));

        try {
            List<String> args = new ArrayList<>(List.of("worktree", "remove"));
            if (force) {
                args.add("--force");
            }
            args.add(wtPath); // 现在绝对安全
            runGit(args);

            if (completeTask && wt.getTaskId() != null) {
                int taskId = wt.getTaskId();
                tasks.updateStatus(taskId, TaskStatus.COMPLETED.getValue());
                tasks.unbindWorktree(taskId);
                events.emit("task.completed", Map.of("id", taskId, "status", "completed"), Map.of("name", name));
            }

            IndexData idx = loadIndex();
            for (Worktree item : idx.getWorktrees()) {
                if (Objects.equals(item.getName(), name)) {
                    item.setStatus("removed");
                    item.setRemovedAt(System.currentTimeMillis() / 1000.0);
                    break;
                }
            }
            saveIndex(idx);

            events.emit("worktree.remove.after", taskMap, Map.of("name", name, "path", wtPath, "status", "removed"));
            return "Removed worktree '" + name + "'";
        } catch (Exception e) {
            events.emit("worktree.remove.failed", taskMap, Map.of("name", name, "path", wtPath), e.getMessage());
            throw new RuntimeException("Remove failed: " + e.getMessage(), e);
        }
    }

    public String keep(String name) {
        Worktree wt = find(name);
        if (wt == null) {
            return "Error: Unknown worktree '" + name + "'";
        }

        IndexData idx = loadIndex();
        Worktree kept = null;
        for (Worktree item : idx.getWorktrees()) {
            if (Objects.equals(item.getName(), name)) {
                item.setStatus("kept");
                item.setKeptAt(System.currentTimeMillis() / 1000.0);
                kept = item;
            }
        }
        saveIndex(idx);

        events.emit("worktree.keep", Map.of("id", wt.getTaskId()), Map.of(
                "name", name,
                "path", wt.getPath(),
                "status", "kept"));

        return kept != null ? JSON.toJSONString(kept) : "Error: Unknown worktree '" + name + "'";
    }

    public boolean isGitAvailable() {
        return gitAvailable;
    }

    public EventBus events() {
        return events;
    }

    public Tasks tasks() {
        return tasks;
    }

    // 可变参数，绝对不会拼错
    public String runGit(String... args) {
        if (!gitAvailable) {
            throw new RuntimeException("Not in a git repository");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(Arrays.asList(args));

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true);

            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            int exitCode = p.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException(output);
            }
            return output;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private void runGit(List<String> args, Path cwd) {
        Commands.CommandResult r = Commands.execSync("git " + String.join(" ", args), 120000, false, cwd);
        if (r.getExitCode() != 0) {
            throw new RuntimeException(r.getOutput().trim());
        }
    }

    // 最终 100% 能处理空格路径的 runGit
    private String runGit(List<String> args) {
        if (!gitAvailable) {
            throw new RuntimeException("Not in a git repository");
        }

        // 构建完整命令：git + 你传入的参数
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);

        try {
            // ✅ 关键：ProcessBuilder 接收 List<String> 会自动处理空格路径！
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException(output);
            }
            return output;
        } catch (Exception e) {
            throw new RuntimeException("Git command failed: " + e.getMessage(), e);
        }
    }

    public static class IndexData {
        private List<Worktree> worktrees = new ArrayList<>();

        public List<Worktree> getWorktrees() {
            return worktrees;
        }

        public void setWorktrees(List<Worktree> worktrees) {
            this.worktrees = worktrees;
        }
    }
}