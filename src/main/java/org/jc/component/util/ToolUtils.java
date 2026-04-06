package org.jc.component.util;

import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.*;

/**
 * 官方 openai-java 4.29.1 专用
 * 使用 Fastjson2 解析：OpenAI 工具 JSON 字符串 → ChatCompletionTool
 */
public class ToolUtils {

    /**
     * 将 OpenAI 格式的工具 JSON 字符串 转为 ChatCompletionTool
     */
    public static ChatCompletionTool fromJson(String toolJson) {
        // 1. Fastjson 解析根对象
        JSONObject root = JSON.parseObject(toolJson);
        JSONObject funcObj = root.getJSONObject("function");

        // 2. 提取 function 基础信息
        String name = funcObj.getString("name");
        String description = funcObj.getString("description");

        // 3. 解析 parameters 并转为 Map<String, JsonValue>
        JSONObject paramsObj = funcObj.getJSONObject("parameters");
        Map<String, JsonValue> paramMap = convertToJsonValueMap(paramsObj);

        // 4. 构建 FunctionParameters
        FunctionParameters parameters = FunctionParameters.builder()
                .putAllAdditionalProperties(paramMap)
                .build();

        // 5. 构建 FunctionDefinition
        FunctionDefinition funcDef = FunctionDefinition.builder()
                .name(name)
                .description(description)
                .parameters(parameters)
                .build();

        // 6. 构建最终 ChatCompletionTool
        return ChatCompletionTool.ofFunction(
                ChatCompletionFunctionTool.builder()
                        .function(funcDef)
                        .build()
        );
    }

    /**
     * 递归将 Fastjson JSONObject 转为 Map<String, JsonValue>
     */
    private static Map<String, JsonValue> convertToJsonValueMap(JSONObject obj) {
        Map<String, JsonValue> map = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            map.put(key, convertValue(value));
        }
        return map;
    }

    /**
     * 递归将 JSON 值转为 JsonValue（支持对象/数组/字符串/数字/布尔）
     */
    private static JsonValue convertValue(Object value) {
        if (value instanceof JSONObject obj) {
            return JsonValue.from(convertToJsonValueMap(obj));
        }
        if (value instanceof JSONArray arr) {
            List<Object> list = new ArrayList<>();
            for (Object item : arr) {
                list.add(convertValue(item));
            }
            return JsonValue.from(list);
        }
        if (value instanceof String str) {
            return JsonValue.from(str);
        }
        if (value instanceof Number num) {
            return JsonValue.from(num);
        }
        if (value instanceof Boolean bool) {
            return JsonValue.from(bool);
        }
        return JsonValue.from(String.valueOf(value));
    }
}