package org.jc.agent;

import com.openai.models.chat.completions.*;
import org.jc.Commons;
import org.jc.Tools;
import org.jc.team.Team;
import org.jc.tool.ToolCallResult;

import java.util.ArrayList;
import java.util.List;

public class BaseAgent {
    protected String name;
    protected String role;
    protected String model;
    protected PromptProvider promptProvider;
    protected List<ChatCompletionTool> tools;
    protected ToolHandlers toolHandlers;
    protected Team team;
    protected AgentConfig config;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public PromptProvider getPromptProvider() {
        return promptProvider;
    }

    public void setPromptProvider(PromptProvider promptProvider) {
        this.promptProvider = promptProvider;
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

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    protected List<ChatCompletionMessageParam> fullMessages(List<ChatCompletionMessageParam> messages) {
        List<ChatCompletionMessageParam> fullMessages = new ArrayList<>();
        fullMessages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam
                        .builder()
                        .content(this.getPromptProvider().get(this))
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

    protected ToolCallResult toolCall(List<ChatCompletionMessageToolCall> toolCalls, List<ChatCompletionMessageParam> messages) {
        if (toolCalls == null) {
            return ToolCallResult.of();
        }
        // 遍历所有工具调用（模型可能一次调用多个工具）
        boolean idle = false;
        for (ChatCompletionMessageToolCall toolCall : toolCalls) {
            ChatCompletionMessageParam toolMessage = this.getToolHandlers().call(this, toolCall);
            if (toolMessage != null) {
                // 将工具执行结果以 "tool" 角色发回给模型
                messages.add(toolMessage);
            }
            if (Tools.isTool(toolCall, "idle")) {
                idle = true;
                break;
            }
        }
        return ToolCallResult.of(idle);
    }

    /**
     * 生成身份注入块（压缩后重新注入身份信息）
     */
    public ChatCompletionMessageParam makeIdentityBlock() {
        String content = String.format(
                "<identity>你是：%s，角色：%s，团队：%s。继续执行你的工作</identity>",
                this.getName(), this.getRole(), this.getTeam().getTeamName()
        );
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(content)
                        .build()
        );
    }
}
