package org.jc.agent;

import com.openai.models.chat.completions.*;
import org.jc.Commons;

import java.util.ArrayList;
import java.util.List;

public class BaseAgent {
    protected String name;
    private String model;
    protected String prompt;
    protected List<ChatCompletionTool> tools;
    protected ToolHandlers toolHandlers;
    protected AgentConfig config;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<ChatCompletionTool> getTools() {
        return tools;
    }

    public void setTools(List<ChatCompletionTool> tools) {
        this.tools = tools;
    }

    public ToolHandlers getToolHandlers() {
        return toolHandlers;
    }

    public void setToolHandlers(ToolHandlers toolHandlers) {
        this.toolHandlers = toolHandlers;
    }

    public AgentConfig getConfig() {
        return config;
    }

    public void setConfig(AgentConfig config) {
        this.config = config;
    }

    protected List<ChatCompletionMessageParam> fullMessages(List<ChatCompletionMessageParam> messages) {
        List<ChatCompletionMessageParam> fullMessages = new ArrayList<>();
        fullMessages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam
                        .builder()
                        .content(this.getPrompt())
                        .build()
        ));
        fullMessages.addAll(messages);
        return fullMessages;
    }

    protected ChatCompletionAssistantMessageParam chat(List<ChatCompletionMessageParam> messages) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(this.getModel())
                .messages(this.fullMessages(messages))
                .tools(this.getTools())
                .build();

        ChatCompletion chatCompletion = Commons.getClient().chat().completions().create(params);
        ChatCompletionMessage message = chatCompletion.choices().get(0).message();

        // 将模型的消息加入历史记录
        ChatCompletionAssistantMessageParam param = message.toParam();
        messages.add(ChatCompletionMessageParam.ofAssistant(param));
        return param;
    }

    protected void toolCall(List<ChatCompletionMessageToolCall> toolCalls, List<ChatCompletionMessageParam> messages) {
        if (toolCalls == null) {
            return;
        }
        // 遍历所有工具调用（模型可能一次调用多个工具）
        for (ChatCompletionMessageToolCall toolCall : toolCalls) {
            ChatCompletionMessageParam toolMessage = this.getToolHandlers().call(this, toolCall);
            if (toolMessage != null) {
                // 将工具执行结果以 "tool" 角色发回给模型
                messages.add(toolMessage);
            }
        }
    }
}
