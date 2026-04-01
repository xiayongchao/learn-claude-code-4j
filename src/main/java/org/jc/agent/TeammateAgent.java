package org.jc.agent;

import com.openai.models.chat.completions.*;
import org.jc.message.MessageBus;
import org.jc.team.Teammate;
import org.jc.team.TeammateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TeammateAgent extends Agent {
    protected String prompt;
    private int maxLoopTimes;
    private String role;

    protected TeammateAgent() {
        this.prompt = "你是: %s, 角色: %s, 工作目录: %s";
    }

    public static TeammateAgent of() {
        return new TeammateAgent();
    }

    public TeammateAgent role(String role) {
        this.role = role;
        return this;
    }

    public TeammateAgent maxLoopTimes(int maxLoopTimes) {
        this.maxLoopTimes = maxLoopTimes;
        return this;
    }


    public TeammateAgent name(String name) {
        this.name = name;
        return this;
    }

    public TeammateAgent prompt(String prompt) {
        this.prompt = prompt;
        return this;
    }

    public TeammateAgent model(String model) {
        this.model = model;
        return this;
    }

    public TeammateAgent bus(MessageBus bus) {
        this.bus = bus;
        return this;
    }

    public TeammateAgent teammateManager(TeammateManager teammateManager) {
        this.teammateManager = teammateManager;
        return this;
    }

    public TeammateAgent tools(List<ChatCompletionTool> tools) {
        this.tools = tools;
        return this;
    }

    public TeammateAgent toolHandlers(ToolHandlers toolHandlers) {
        this.toolHandlers = toolHandlers;
        return this;
    }

    public TeammateAgent config(AgentConfig config) {
        this.config = config;
        return this;
    }

    //

    public String role() {
        return this.role;
    }

    public int maxLoopTimes() {
        return this.maxLoopTimes;
    }

    public String prompt() {
        return String.format(this.prompt, this.name(), this.role(), this.config().workDir());
    }


//    -------------------------

    public ChatCompletionMessageParam loop(String prompt) {
        List<ChatCompletionMessageParam> messages = new ArrayList<>();

        messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam
                        .builder()
                        .content(prompt)
                        .build()
        ));

        // 模拟循环 50 次
        for (int i = 0; i < maxLoopTimes; i++) {
            // 读取收件箱
            this.readInbox(messages);

            ChatCompletionAssistantMessageParam assistantMessage = this.chat(messages);

            Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = assistantMessage.toolCalls();
            if (toolCallsOptional.isEmpty()) {
                break;
            }

            this.toolCall(toolCallsOptional.get(), messages);
        }

        // 结束后设置 idle
        Teammate teammate = this.teammateManager().findTeammate(this.name());
        if (teammate != null && !"shutdown".equals(teammate.getStatus())) {
            teammate.setStatus("idle");
            this.teammateManager().saveTeam();
        }
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }
}
