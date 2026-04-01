package org.jc.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openai.models.chat.completions.*;
import org.jc.Commons;
import org.jc.message.Message;
import org.jc.message.MessageBus;
import org.jc.team.TeammateManager;

import java.util.*;

public class Agent {
    protected String name;
    protected String model;
    protected String prompt;
    protected List<ChatCompletionTool> tools;
    protected ToolHandlers toolHandlers;
    protected MessageBus bus;
    protected AgentConfig config;
    protected TeammateManager teammateManager;

    protected Agent() {
    }

    public static Agent of() {
        return new Agent();
    }

    public Agent name(String name) {
        this.name = name;
        return this;
    }

    public Agent prompt(String prompt) {
        this.prompt = prompt;
        return this;
    }

    public Agent model(String model) {
        this.model = model;
        return this;
    }

    public Agent bus(MessageBus bus) {
        this.bus = bus;
        return this;
    }

    public Agent teammateManager(TeammateManager teammateManager) {
        this.teammateManager = teammateManager;
        return this;
    }

    public Agent tools(List<ChatCompletionTool> tools) {
        this.tools = tools;
        return this;
    }

    public Agent toolHandlers(ToolHandlers toolHandlers) {
        this.toolHandlers = toolHandlers;
        return this;
    }

    public Agent config(AgentConfig config) {
        this.config = config;
        return this;
    }

    //

    public String name() {
        return this.name;
    }

    public String prompt() {
        return this.prompt;
    }

    public String model() {
        return this.model;
    }

    public MessageBus bus() {
        return this.bus;
    }

    public TeammateManager teammateManager() {
        return this.teammateManager;
    }

    public List<ChatCompletionTool> tools() {
        return this.tools;
    }

    public ToolHandlers toolHandlers() {
        return this.toolHandlers;
    }

    public AgentConfig config() {
        return this.config;
    }


    // ===================== 创建/启动队友 =====================
    public String spawnTeammate(String arguments, List<ChatCompletionTool> tools, ToolHandlers toolHandlers) {
        JSONObject object = JSON.parseObject(arguments);
        String name = object.getString("name");
        String role = object.getString("role");

        return this.teammateManager().spawn(
                TeammateAgent.of()
                        .name(name)
                        .role(role)
                        .model(this.model())
                        .maxLoopTimes(50)
                        .bus(this.bus())
                        .tools(tools)
                        .toolHandlers(toolHandlers)
                        .config(
                                AgentConfig.of()
                                        .readInbox(true)
                                        .workDir(this.config().workDir())
                        )
                , object.getString("prompt")
        );
    }

    public String listTeammate() {
        return this.teammateManager().listTeammate();
    }

    public String sendMessage(String arguments) {
        JSONObject object = JSON.parseObject(arguments);
        String msgType = object.getString("msgType");
        return this.bus().send(
                name
                , object.getString("to")
                , object.getString("content")
                , msgType == null || msgType.isBlank() ? "message" : msgType
                , null
        );
    }

    public String broadcast(String arguments) {
        JSONObject object = JSON.parseObject(arguments);

        String sender = this.name();
        List<String> teammates = teammateManager.listTeammateNames();
        int count = 0;
        if (teammates != null) {
            for (String name : teammates) {
                if (!name.equals(sender)) {
                    this.bus().send(sender, name, object.getString("content"), "broadcast", null);
                    count++;
                }
            }
        }
        return "广播 " + count + " 个队友";
    }


    public String readInbox(String arguments) {
        return JSON.toJSONString(this.bus().readInbox(arguments, true));
    }

    protected void readInbox(List<ChatCompletionMessageParam> messages) {
        if (!this.config().readInbox()) {
            return;
        }
        List<Message> inboxMessages = this.bus().readInbox(this.name(), false);
        if (inboxMessages != null && !inboxMessages.isEmpty()) {
            messages.add(
                    ChatCompletionMessageParam.ofUser(
                            ChatCompletionUserMessageParam
                                    .builder()
                                    .content(String.format("<inbox>\n%s\n</inbox>", JSON.toJSONString(inboxMessages)))
                                    .build()
                    ));
            messages.add(ChatCompletionMessageParam.ofAssistant(
                    ChatCompletionAssistantMessageParam
                            .builder()
                            .content("已查看收件箱消息")
                            .build()
            ));
        }
    }

    protected List<ChatCompletionMessageParam> fullMessages(List<ChatCompletionMessageParam> messages) {
        List<ChatCompletionMessageParam> fullMessages = new ArrayList<>();
        fullMessages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam
                        .builder()
                        .content(this.prompt())
                        .build()
        ));
        fullMessages.addAll(messages);
        return fullMessages;
    }

    protected ChatCompletionAssistantMessageParam chat(List<ChatCompletionMessageParam> messages) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(this.model())
                .messages(fullMessages(messages))
                .tools(this.tools())
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
            ChatCompletionMessageParam toolMessage = this.toolHandlers().call(this, toolCall);
            if (toolMessage != null) {
                // 将工具执行结果以 "tool" 角色发回给模型
                messages.add(toolMessage);
            }
        }
    }

    public ChatCompletionMessageParam loop(List<ChatCompletionMessageParam> messages) {
        while (true) {
            this.readInbox(messages);

            ChatCompletionAssistantMessageParam assistantMessage = this.chat(messages);

            Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = assistantMessage.toolCalls();
            if (toolCallsOptional.isEmpty()) {
                break;
            }

            this.toolCall(toolCallsOptional.get(), messages);
        }
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }
}
