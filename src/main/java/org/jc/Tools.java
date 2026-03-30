package org.jc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

public class Tools {
    private static final Set<String> dangerous = new HashSet<>(List.of("rm -rf /", "sudo", "shutdown", "reboot"));

    public static String runBash(String arguments) {
        if (dangerous.contains(arguments)) {
            return "错误：危险命令被阻止";
        }

        String command = JSON.parseObject(arguments).getString("command");
        Commands.CommandResult commandResult = Commands.execSync(command);
        return JSON.toJSONString(commandResult);
    }

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

    // ------------------------------
    // 写入文件
    // ------------------------------
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

    // ------------------------------
    // 编辑文件
    // ------------------------------
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
            String content = Files.readString(filePath);

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

    public static ChatCompletionTool bashTool() {
        // 第一步：创建空的 Map
        Map<String, JsonValue> paramMap = new HashMap<>();

        // 第二步：放入 JSON Schema 的每一个 key-value
        paramMap.put("type", JsonValue.from("object"));

        // properties
        Map<String, JsonValue> fields = new HashMap<>();
        fields.put("type", JsonValue.from("string"));
        fields.put("description", JsonValue.from("要执行的shell命令，如 ls -l、pwd"));

        Map<String, JsonValue> properties = new HashMap<>();
        properties.put("command", JsonValue.from(fields));

        paramMap.put("properties", JsonValue.from(properties));

        // required
        paramMap.put("required", JsonValue.from(List.of("command")));

        FunctionParameters parameters = FunctionParameters.builder()
                .putAllAdditionalProperties(paramMap)  // ✅ 入参完全匹配
                .build();

        // 最终函数
        FunctionDefinition functionDefinition = FunctionDefinition.builder()
                .name("bash")
                .description("在当前工作区中运行 shell 命令")
                .parameters(parameters)
                .build();

        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool
                .builder()
                .function(functionDefinition)
                .build());
    }

    public static ChatCompletionTool readFileTool() {
        // 第一步：创建空的 Map
        Map<String, JsonValue> paramMap = new HashMap<>();

        // 第二步：放入 JSON Schema 的每一个 key-value
        paramMap.put("type", JsonValue.from("object"));

        // properties
        Map<String, JsonValue> fields = new HashMap<>();
        fields.put("type", JsonValue.from("string"));
        fields.put("description", JsonValue.from("文件路径"));

        Map<String, JsonValue> properties = new HashMap<>();
        properties.put("path", JsonValue.from(fields));

        paramMap.put("properties", JsonValue.from(properties));

        // required
        paramMap.put("required", JsonValue.from(List.of("path")));

        FunctionParameters parameters = FunctionParameters.builder()
                .putAllAdditionalProperties(paramMap)  // ✅ 入参完全匹配
                .build();

        // 最终函数
        FunctionDefinition functionDefinition = FunctionDefinition.builder()
                .name("read_file")
                .description("读取文件内容")
                .parameters(parameters)
                .build();

        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool
                .builder()
                .function(functionDefinition)
                .build());
    }

    public static ChatCompletionTool writeFileTool() {
        // 第一步：创建空的 Map
        Map<String, JsonValue> paramMap = new HashMap<>();

        // 第二步：放入 JSON Schema 的每一个 key-value
        paramMap.put("type", JsonValue.from("object"));

        // properties
        Map<String, JsonValue> path = new HashMap<>();
        path.put("type", JsonValue.from("string"));
        path.put("description", JsonValue.from("文件路径"));

        Map<String, JsonValue> content = new HashMap<>();
        content.put("type", JsonValue.from("string"));
        content.put("description", JsonValue.from("写入内容"));

        Map<String, JsonValue> properties = new HashMap<>();
        properties.put("path", JsonValue.from(path));
        properties.put("content", JsonValue.from(content));

        paramMap.put("properties", JsonValue.from(properties));

        // required
        paramMap.put("required", JsonValue.from(List.of("path", "content")));

        FunctionParameters parameters = FunctionParameters.builder()
                .putAllAdditionalProperties(paramMap)  // ✅ 入参完全匹配
                .build();

        // 最终函数
        FunctionDefinition functionDefinition = FunctionDefinition.builder()
                .name("write_file")
                .description("写入内容到文件")
                .parameters(parameters)
                .build();

        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool
                .builder()
                .function(functionDefinition)
                .build());
    }

    public static ChatCompletionTool editFileTool() {
        // 第一步：创建空的 Map
        Map<String, JsonValue> paramMap = new HashMap<>();

        // 第二步：放入 JSON Schema 的每一个 key-value
        paramMap.put("type", JsonValue.from("object"));

        // properties
        Map<String, JsonValue> path = new HashMap<>();
        path.put("type", JsonValue.from("string"));
        path.put("description", JsonValue.from("文件路径"));

        Map<String, JsonValue> oldText = new HashMap<>();
        oldText.put("type", JsonValue.from("string"));
        oldText.put("description", JsonValue.from("旧文本"));

        Map<String, JsonValue> newText = new HashMap<>();
        newText.put("type", JsonValue.from("string"));
        newText.put("description", JsonValue.from("新文本"));

        Map<String, JsonValue> properties = new HashMap<>();
        properties.put("path", JsonValue.from(path));
        properties.put("oldText", JsonValue.from(oldText));
        properties.put("newText", JsonValue.from(newText));

        paramMap.put("properties", JsonValue.from(properties));

        // required
        paramMap.put("required", JsonValue.from(List.of("path", "oldText", "newText")));

        FunctionParameters parameters = FunctionParameters.builder()
                .putAllAdditionalProperties(paramMap)  // ✅ 入参完全匹配
                .build();

        // 最终函数
        FunctionDefinition functionDefinition = FunctionDefinition.builder()
                .name("edit_file")
                .description("替换文件中的精确文本")
                .parameters(parameters)
                .build();

        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool
                .builder()
                .function(functionDefinition)
                .build());
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
