package org.jc.component.tool;

import com.openai.models.chat.completions.ChatCompletionTool;

public interface Tool extends LeadTool, TeammateTool {
    /**
     * 工具名称
     *
     * @return
     */
    String name();

    /**
     * 工具定义
     *
     * @return
     */
    ChatCompletionTool definition();

    /**
     * 执行工具
     *
     * @param arguments
     * @return
     */
    String call(String arguments);
}
