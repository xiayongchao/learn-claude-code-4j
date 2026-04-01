package org.jc;

import com.alibaba.fastjson2.JSON;
import com.openai.models.chat.completions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Messages {

    // 单条消息 → 标准 JSON（role + content）
    public static String toStandardJson(ChatCompletionMessageParam msg) {
        if (msg == null) return "{}";
        return JSON.toJSONString(toStandardMap(msg));
    }

    // 消息列表 → 标准 JSON 数组
    public static String toStandardJson(List<ChatCompletionMessageParam> messages) {
        if (messages == null) return "[]";

        List<Map<String, Object>> list = messages.stream()
                .map(Messages::toStandardMap)
                .collect(Collectors.toList());

        return JSON.toJSONString(list);
    }

    // 核心：转为标准 { role: "...", content: "..." }
    private static Map<String, Object> toStandardMap(ChatCompletionMessageParam msg) {
        Map<String, Object> map = new HashMap<>();

        if (msg.isUser()) {
            map.put("role", "user");
            map.put("content", Commons.getText(msg));
        } else if (msg.isAssistant()) {
            map.put("role", "assistant");
            map.put("content", Commons.getText(msg));
        } else if (msg.isSystem()) {
            map.put("role", "system");
            map.put("content", Commons.getText(msg));
        } else if (msg.isTool()) {
            ChatCompletionToolMessageParam toolMsg = msg.asTool();
            map.put("role", "tool");
            map.put("content", toolMsg.content().asText());
            map.put("tool_call_id", toolMsg.toolCallId());
        } else if (msg.isFunction()) {
            ChatCompletionFunctionMessageParam fnMsg = msg.asFunction();
            map.put("role", "function");
            map.put("content", fnMsg.content().get());
            map.put("name", fnMsg.name());
        }

        return map;
    }
}
