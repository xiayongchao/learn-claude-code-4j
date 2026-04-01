package org.jc.agent;

import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;

import java.util.HashMap;
import java.util.Map;

public class ToolHandlers {
    public interface ToolCall {
        String call(Agent agent, String arguments);
    }

    private final Map<String, ToolCall> toolCalls = new HashMap<>();

    private ToolHandlers() {
    }

    public static ToolHandlers of() {
        return new ToolHandlers();
    }

    public ToolHandlers add(String toolName, ToolCall toolCall) {
        toolCalls.put(toolName, toolCall);
        return this;
    }

    public ChatCompletionMessageParam call(Agent agent, ChatCompletionMessageToolCall toolCall) {
        if (agent == null || toolCall == null || !toolCall.isFunction()) {
            return null;
        }

        ChatCompletionMessageFunctionToolCall functionCall = toolCall.asFunction();
        ChatCompletionMessageFunctionToolCall.Function function = functionCall.function();
        String functionName = function.name();
        String arguments = function.arguments();

        ToolCall call = toolCalls.get(functionName);
        if (call == null) {
            return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam
                    .builder()
                    .content(String.format("未知的工具：%s", functionName))
                    .toolCallId(functionCall.id()) // 必须对应 toolCall 的 ID
                    .build());
        }
        // 将工具执行结果以 "tool" 角色发回给模型
        return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam
                .builder()
                .content(call.call(agent, arguments))
                .toolCallId(functionCall.id()) // 必须对应 toolCall 的 ID
                .build());
    }
}
