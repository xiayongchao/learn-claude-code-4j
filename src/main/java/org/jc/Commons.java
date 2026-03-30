package org.jc;

// 该代码 OpenAI SDK 版本为 2.6.0

import com.alibaba.fastjson.JSON;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Commons {
    public static void main(String[] args) {


    }

    public static String getCwd() {
        Path currentPath = Paths.get("");
        return currentPath.toAbsolutePath().toString();
    }


    private static final OpenAIClient openAIClient = OpenAIOkHttpClient.builder()
            .apiKey(System.getenv("DASHSCOPE_API_KEY"))
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
            .build();

    public static OpenAIClient getClient() {
        return openAIClient;

    }

    public static String runBash(String arguments) {
        String command = JSON.parseObject(arguments).getString("command");
        Commands.CommandResult commandResult = Commands.execSync(command);
        return JSON.toJSONString(commandResult);
    }

    public static ChatCompletionTool bashTool() {
        // 第一步：创建空的 Map
        Map<String, JsonValue> paramMap = new HashMap<>();

        // 第二步：放入 JSON Schema 的每一个 key-value
        paramMap.put("type", JsonValue.from("object"));

        // properties
        Map<String, JsonValue> commandProp = new HashMap<>();
        commandProp.put("type", JsonValue.from("string"));
        commandProp.put("description", JsonValue.from("要执行的shell命令，如 ls -l、pwd"));

        Map<String, JsonValue> properties = new HashMap<>();
        properties.put("command", JsonValue.from(commandProp));

        paramMap.put("properties", JsonValue.from(properties));

        // required
        paramMap.put("required", JsonValue.from(List.of("command")));

        // 第三步：构建 FunctionParameters（这行绝对不报错！）
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

    public static String getText(ChatCompletionMessageParam param) {
        if (param == null) {
            return null;
        }
        if (param.isAssistant()) {
            return getAssistantText(param.assistant());
        }
        if (param.isUser()) {
            return getUserText(param.user());
        }
        if (param.isSystem()) {
            return getSystemText(param.system());
        }
        if (param.isTool()) {
            return getToolText(param.tool());
        }
        if (param.isDeveloper()) {
            return getDeveloperText(param.developer());
        }
        if (param.isFunction()) {
            return getFunctionText(param.function());
        }
        if (param.isValid()) {
            ChatCompletionMessageParam validate = param.validate();
            return getText(validate);
        }

        return null;
    }

    public static String getAssistantText(Optional<ChatCompletionAssistantMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        Optional<ChatCompletionAssistantMessageParam.Content> content = optional.get().content();
        return content.map(ChatCompletionAssistantMessageParam.Content::asText).orElse(null);
    }

    public static String getUserText(Optional<ChatCompletionUserMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        ChatCompletionUserMessageParam.Content content = optional.get().content();
        return content.asText();
    }

    public static String getSystemText(Optional<ChatCompletionSystemMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        ChatCompletionSystemMessageParam.Content content = optional.get().content();
        return content.asText();
    }

    public static String getToolText(Optional<ChatCompletionToolMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        ChatCompletionToolMessageParam.Content content = optional.get().content();
        return content.asText();
    }

    public static String getDeveloperText(Optional<ChatCompletionDeveloperMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        ChatCompletionDeveloperMessageParam.Content content = optional.get().content();
        return content.asText();
    }

    public static String getFunctionText(Optional<ChatCompletionFunctionMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        Optional<String> content = optional.get().content();
        return content.orElse(null);
    }
}
