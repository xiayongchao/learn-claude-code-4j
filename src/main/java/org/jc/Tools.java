package org.jc;

import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class Tools {
    private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap();

    static {
        TOOL_HANDLERS.put("bash", Commons::runBash);
    }

    public static ChatCompletionMessageParam exe(ChatCompletionMessageToolCall toolCall) {
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
