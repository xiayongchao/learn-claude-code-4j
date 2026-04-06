package org.jc.component.loop;

import com.alibaba.fastjson2.JSON;
import com.google.inject.Inject;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.*;
import org.jc.component.inbox.InBoxMessage;
import org.jc.component.inbox.MessageBus;
import org.jc.component.state.States;
import org.jc.component.team.Team;
import org.jc.component.tool.TeammateTool;
import org.jc.component.tool.Tool;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class S10TeammateReAct implements TeammateReAct {
    private final Team team;
    private final MessageBus bus;
    private final OpenAIClient client;
    private final List<ChatCompletionTool> tools;
    private final Map<String, Tool> toolHandlers;

    @Inject
    public S10TeammateReAct(Team team, MessageBus bus, OpenAIClient client, Set<TeammateTool> tools) {
        this.team = Objects.requireNonNull(team);
        this.bus = Objects.requireNonNull(bus);
        this.client = Objects.requireNonNull(client);

        Objects.requireNonNull(tools);
        Objects.requireNonNull(tools);
        this.tools = tools
                .stream()
                .map(it -> ((Tool) it).definition())
                .collect(Collectors.toList());
        this.toolHandlers = tools
                .stream()
                .collect(Collectors
                        .toMap(it -> ((Tool) it).name(), it -> (Tool) it, (o, n) -> (Tool) o));
    }

    /**
     * 读取收件箱
     *
     * @param messages
     */
    private void readInbox(List<ChatCompletionMessageParam> messages) {
        List<InBoxMessage> inboxMessages;
        try {
            inboxMessages = this.bus.readInbox(States.get().getName(), true);
        } catch (IOException e) {
            messages.add(
                    ChatCompletionMessageParam.ofUser(
                            ChatCompletionUserMessageParam
                                    .builder()
                                    .content(String.format("错误：读取收件箱消息失败，e = %s", e))
                                    .build()
                    ));
            return;
        }
        if (inboxMessages == null || inboxMessages.isEmpty()) {
            return;
        }

        for (InBoxMessage inboxMessage : inboxMessages) {
            messages.add(
                    ChatCompletionMessageParam.ofUser(
                            ChatCompletionUserMessageParam
                                    .builder()
                                    .content(JSON.toJSONString(inboxMessage))
                                    .build()
                    ));

        }
    }

    private List<ChatCompletionMessageParam> fullMessages(List<ChatCompletionMessageParam> messages) {
        List<ChatCompletionMessageParam> fullMessages = new ArrayList<>();
        fullMessages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam
                        .builder()
                        .content(States.teammate().getPrompt())
                        .build()
        ));
        fullMessages.addAll(messages);
        return fullMessages;
    }


    private ChatCompletionAssistantMessageParam chat(List<ChatCompletionMessageParam> messages) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(States.teammate().getModel())
                .messages(this.fullMessages(messages))
                .tools(this.tools)
                .build();

        ChatCompletion chatCompletion = this.client.chat().completions().create(params);
        ChatCompletionMessage message = chatCompletion.choices().get(0).message();

        // 将模型的消息加入历史记录
        ChatCompletionAssistantMessageParam param = message.toParam();
        messages.add(ChatCompletionMessageParam.ofAssistant(param));
        return param;
    }

    public ChatCompletionMessageParam callTool(ChatCompletionMessageToolCall toolCall) {
        if (toolCall == null || !toolCall.isFunction()) {
            return null;
        }

        ChatCompletionMessageFunctionToolCall functionCall = toolCall.asFunction();
        ChatCompletionMessageFunctionToolCall.Function function = functionCall.function();
        String functionName = function.name();
        String arguments = function.arguments();

        Tool tool = toolHandlers.get(functionName);
        if (tool == null) {
            return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam
                    .builder()
                    .content(String.format("未知的工具：%s", functionName))
                    .toolCallId(functionCall.id()) // 必须对应 toolCall 的 ID
                    .build());
        }

        String content = tool.call(arguments);

        // 将工具执行结果以 "tool" 角色发回给模型
        return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam
                .builder()
                .content(content)
                .toolCallId(functionCall.id()) // 必须对应 toolCall 的 ID
                .build());
    }

    protected void callTools(List<ChatCompletionMessageToolCall> toolCalls, List<ChatCompletionMessageParam> messages) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }

        // 遍历所有工具调用（模型可能一次调用多个工具）
        for (ChatCompletionMessageToolCall toolCall : toolCalls) {
            ChatCompletionMessageParam toolMessage = this.callTool(toolCall);
            if (toolMessage != null) {
                // 将工具执行结果以 "tool" 角色发回给模型
                messages.add(toolMessage);
            }
        }
    }

    /**
     * react循环
     *
     * @return
     */
    public void loop() {
        List<ChatCompletionMessageParam> messages = States.teammate().getMessages();
        if (messages == null) {
            messages = new ArrayList<>();
            States.teammate().setMessages(messages);
        }

        messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam
                        .builder()
                        .content(States.teammate().getUserPrompt())
                        .build()
        ));

        Integer maxLoopTimes = States.teammate().getMaxLoopTimes();
        for (int i = 0; i < maxLoopTimes; i++) {
            //读取收件箱
            this.readInbox(messages);

            //如果停止，进行响应
            if (States.teammate().isShutdown()) {
                break;
            }

            //调用大模型
            ChatCompletionAssistantMessageParam assistantMessage = this.chat(messages);

            Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = assistantMessage.toolCalls();
            if (toolCallsOptional.isEmpty()) {
                break;
            }

            //调用工具
            this.callTools(toolCallsOptional.get(), messages);
        }

        //重置员工状态
        this.team.resetTeammateStatusAfterTurn();
    }
}
