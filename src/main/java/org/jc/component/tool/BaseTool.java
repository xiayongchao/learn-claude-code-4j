package org.jc.component.tool;

import com.alibaba.fastjson2.JSON;
import com.openai.models.chat.completions.ChatCompletionTool;
import org.jc.component.util.ToolUtils;

import java.util.Objects;

public abstract class BaseTool<T> implements Tool {
    private final String name;
    private final Class<T> tClass;
    private final ChatCompletionTool definition;

    public BaseTool(String name, Class<T> tClass, String definition) {
        this.name = name;
        this.tClass = Objects.requireNonNull(tClass);
        this.definition = ToolUtils.fromJson(Objects.requireNonNull(definition));
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public ChatCompletionTool definition() {
        return this.definition;
    }

    @Override
    public String call(String arguments) {
        // 如果是 Void 类型，直接调用，不解析 JSON
        if (tClass == Void.class) {
            return doCall(null);
        }
        return this.doCall(JSON.parseObject(arguments, tClass));
    }

    public abstract String doCall(T arguments);
}
