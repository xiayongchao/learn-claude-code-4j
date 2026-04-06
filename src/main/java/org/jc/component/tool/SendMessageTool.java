package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.inbox.InBoxMessage;
import org.jc.component.tool.args.SendMessageToolArgs;
import org.jc.component.enums.InBoxMsgType;
import org.jc.component.inbox.MessageBus;
import org.jc.component.state.States;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class SendMessageTool extends BaseTool<SendMessageToolArgs> {
    private final MessageBus bus;

    @Inject
    public SendMessageTool(MessageBus bus) {
        super("sendMessage", SendMessageToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "sendMessage",
                        "description": "向团队成员的收件箱发送消息",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "to": {
                                    "type": "string"
                                },
                                "content": {
                                    "type": "string"
                                },
                                "msg_type": {
                                    "type": "string",
                                    "enum": [\"%s\"]
                                }
                            },
                            "required": [
                                "to",
                                "content"
                            ]
                        }
                    }
                }
                """.formatted(Arrays.stream(InBoxMsgType.values()).map(InBoxMsgType::getValue)
                .collect(Collectors.joining("\", \""))));
        this.bus = Objects.requireNonNull(bus);
    }

    @Override
    public String doCall(SendMessageToolArgs arguments) {
        String msgType = arguments.getMsgType();
        if (msgType == null || msgType.isBlank()) {
            msgType = InBoxMsgType.MESSAGE.getValue();
        } else if (!InBoxMsgType.has(msgType)) {
            return "错误: 无效的消息类型 '" + msgType + "', 有效的消息类型包括: " + InBoxMsgType.renderValidMsgTypes();
        }

        long timestamp = System.currentTimeMillis();
        InBoxMessage message = new InBoxMessage(msgType, States.get().getName()
                , arguments.getContent(), timestamp, null);

        String to = arguments.getTo();
        try {
            this.bus.send(to, message);
        } catch (Exception e) {
            return "发送消息失败: " + e.getMessage();
        }

        return String.format("已经发送 %s 消息给 %s 了", msgType, to);
    }
}
