package org.jc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.*;
import org.jc.component.util.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

public class Tools {
    private static final Set<String> dangerous = new HashSet<>(List.of("rm -rf /", "sudo", "shutdown", "reboot"));
    public static final String TODO = "todo";
    public static final String COMPACT = "compact";

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static String runBash(String arguments) {
        if (dangerous.contains(arguments)) {
            return "错误：危险命令被阻止";
        }

        String command = JSON.parseObject(arguments).getString("command");
        Commands.CommandResult commandResult = Commands.execSync(command);
        if (commandResult.isTimeout()) {
            return "错误：命令执行超时 (120s)";
        }
        return JSON.toJSONString(commandResult);
    }


    public static String runReadFile(String arguments) {
        JSONObject argument = JSON.parseObject(arguments);
        String path = argument.getString("path");
        Integer limit = argument.getInteger("limit");
        return Tools.runReadFile(path, limit);
    }

    public static String runReadFile(String path, Integer limit) {
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

    // ------------------------------
    // 写入文件
    // ------------------------------
    public static String runWriteFile(String arguments) {
        JSONObject argument = JSON.parseObject(arguments);
        String path = argument.getString("path");
        String content = argument.getString("content");
        return Tools.runWriteFile(path, content);
    }

    public static String runWriteFile(String path, String content) {
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

    // ------------------------------
    // 编辑文件
    // ------------------------------
    public static String runEditFile(String arguments) {
        JSONObject argument = JSON.parseObject(arguments);
        String path = argument.getString("path");
        String oldText = argument.getString("oldText");
        String newText = argument.getString("newText");
        return Tools.runEditFile(path, oldText, newText);
    }

    public static String runEditFile(String path, String oldText, String newText) {
        if (!Commons.isSafePath(Commons.CWD, path)) {
            return "错误：路径超出工作区：" + path;
        }

        try {
            Path filePath = Paths.get(Commons.CWD).resolve(path);
            String content = FileUtils.read(filePath);

            if (!content.contains(oldText)) {
                return "错误：在 " + path + " 中未找到文本";
            }

            String updated = content.replaceFirst(oldText, newText);
            Files.writeString(filePath, updated);
            return "已编辑 " + path;
        } catch (Exception e) {
            return "错误：" + e.getMessage();
        }
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static ChatCompletionTool backgroundRunTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("backgroundRun")
                        .description("在后台线程运行命令，立即返回任务ID。")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "command", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("要在后台执行的命令")
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("command"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build());
    }

    public static ChatCompletionTool checkBackgroundTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("checkBackground")
                        .description("检查后台任务状态。省略任务ID则列出全部任务。")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "taskId", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("后台任务ID，可选")
                                                ))
                                        ))
                                ))
                                .build()
                        )
                        .build()
                )
                .build());
    }

    public static ChatCompletionTool taskCreateTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("taskCreate")
                        .description("创建一个新的任务")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "subject", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("任务主题")
                                                )),
                                                "description", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("任务描述")
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("subject"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build());
    }

    public static ChatCompletionTool taskUpdateTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("taskUpdate")
                        .description("更新任务的状态或依赖关系")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "taskId", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("integer"),
                                                        "description", JsonValue.from("任务ID")
                                                )),
                                                "status", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "enum", JsonValue.from(List.of("pending", "in_progress", "completed")),
                                                        "description", JsonValue.from("任务状态")
                                                )),
                                                "addBlockedBy", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("array"),
                                                        "items", JsonValue.from(Map.of(
                                                                "type", JsonValue.from("integer")
                                                        )),
                                                        "description", JsonValue.from("添加依赖的任务ID列表")
                                                )),
                                                "addBlocks", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("array"),
                                                        "items", JsonValue.from(Map.of(
                                                                "type", JsonValue.from("integer")
                                                        )),
                                                        "description", JsonValue.from("添加阻塞的任务ID列表")
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("task_id"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build());
    }

    public static ChatCompletionTool taskListTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("taskList")
                        .description("列出所有任务的状态摘要")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of())
                                ))
                                .build()
                        )
                        .build()
                )
                .build());
    }

    public static ChatCompletionTool taskGetTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("taskGet")
                        .description("根据任务 ID获取任务的详细信息")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "taskId", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("integer"),
                                                        "description", JsonValue.from("任务ID")
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("task_id"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build());
    }

    public static ChatCompletionTool compactTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("compact")
                        .description("触发手动会话压缩")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "focus", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("摘要中需要保留的内容")
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("focus"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build()
        );
    }

    public static ChatCompletionTool loadSkillTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("loadSkill")
                        .description("按名称加载专属专业知识。")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "name", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("要加载的技能名称")
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("name"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build()
        );
    }

    public static ChatCompletionTool taskTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("task")
                        .description("生成一个携带全新上下文的子智能体。它共享文件系统，但不共享对话历史")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "prompt", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("任务简要说明")
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("prompt"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build()
        );
    }

    public static ChatCompletionTool todoTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("todo")
                        .description("更新任务列表，追踪多步骤任务的执行进度。")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "items", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("array"),
                                                        "items", JsonValue.from(Map.of(
                                                                "type", JsonValue.from("object"),
                                                                "properties", JsonValue.from(Map.of(
                                                                        "id", JsonValue.from(Map.of(
                                                                                "type", JsonValue.from("string")
                                                                        )),
                                                                        "text", JsonValue.from(Map.of(
                                                                                "type", JsonValue.from("string")
                                                                        )),
                                                                        "status", JsonValue.from(Map.of(
                                                                                "type", JsonValue.from("string"),
                                                                                "enum", JsonValue.from(List.of("pending", "in_progress", "completed"))
                                                                        ))
                                                                )),
                                                                "required", JsonValue.from(List.of("id", "text", "status"))
                                                        ))
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("items"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build()
        );
    }

    public static ChatCompletionTool bashTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("bash")
                        .description("在当前工作区中运行 shell 命令")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "command", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("要执行的shell命令，如 ls -l、pwd")
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("command"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build()
        );
    }

    public static ChatCompletionTool readFileTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("readFile")
                        .description("读取文件内容")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "path", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("文件路径")
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("path"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build()
        );
    }

    public static ChatCompletionTool writeFileTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("writeFile")
                        .description("写入内容到文件")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "path", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("文件路径")
                                                )),
                                                "content", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("写入内容")
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("path", "content"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build()
        );
    }

    public static ChatCompletionTool editFileTool() {
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name("editFile")
                        .description("替换文件中的精确文本")
                        .parameters(FunctionParameters.builder()
                                .putAllAdditionalProperties(Map.of(
                                        "type", JsonValue.from("object"),
                                        "properties", JsonValue.from(Map.of(
                                                "path", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("文件路径")
                                                )),
                                                "oldText", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("旧文本")
                                                )),
                                                "newText", JsonValue.from(Map.of(
                                                        "type", JsonValue.from("string"),
                                                        "description", JsonValue.from("新文本")
                                                ))
                                        )),
                                        "required", JsonValue.from(List.of("path", "oldText", "newText"))
                                ))
                                .build()
                        )
                        .build()
                )
                .build()
        );
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean isTodoTool(ChatCompletionMessageToolCall toolCall) {
        return isTool(toolCall, TODO);
    }

    public static boolean isCompactTool(ChatCompletionMessageToolCall toolCall) {
        return isTool(toolCall, COMPACT);
    }

    public static boolean isTool(ChatCompletionMessageToolCall toolCall, String tooName) {
        if (toolCall == null || !toolCall.isFunction()) {
            return false;
        }
        ChatCompletionMessageFunctionToolCall functionCall = toolCall.asFunction();
        ChatCompletionMessageFunctionToolCall.Function function = functionCall.function();
        String functionName = function.name();
        return Objects.equals(tooName, functionName);
    }

    public static ChatCompletionMessageParam exe(Map<String, Function<String, String>> TOOL_HANDLERS, ChatCompletionMessageToolCall toolCall) {
        if (toolCall == null || !toolCall.isFunction()) {
            return null;
        }

        ChatCompletionMessageFunctionToolCall functionCall = toolCall.asFunction();
        ChatCompletionMessageFunctionToolCall.Function function = functionCall.function();
        String functionName = function.name();
        String arguments = function.arguments();

        Function<String, String> toolHandler = TOOL_HANDLERS.get(functionName);
        if (toolHandler == null) {
            return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam
                    .builder()
                    .content(String.format("未知的工具：%s", functionName))
                    .toolCallId(functionCall.id()) // 必须对应 toolCall 的 ID
                    .build());
        }
        // 将工具执行结果以 "tool" 角色发回给模型
        return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam
                .builder()
                .content(toolHandler.apply(arguments))
                .toolCallId(functionCall.id()) // 必须对应 toolCall 的 ID
                .build());
    }


}
