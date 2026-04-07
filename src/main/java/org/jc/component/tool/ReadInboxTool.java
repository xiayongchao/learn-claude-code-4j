package org.jc.component.tool;

import com.alibaba.fastjson2.JSON;
import com.google.inject.Inject;
import org.jc.component.inbox.InBoxMessage;
import org.jc.component.inbox.MessageBus;
import org.jc.component.state.States;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class ReadInboxTool extends BaseTool<Void> {
    private final MessageBus bus;

    @Inject
    public ReadInboxTool(MessageBus bus) {  
        super("readInbox", Void.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "readInbox",
                        "description": "读取并清空你的收件箱消息",
                        "parameters": {
                            "type": "object",
                            "properties": {
                
                            }
                        }
                    }
                }
                """);
        this.bus = Objects.requireNonNull(bus);
    }

    /**
     * 读取收件箱（返回对象列表）
     *
     * @param arguments
     * @return
     */
    @Override
    public String doCall(Void arguments) {
        List<InBoxMessage> inboxMessages;
        try {
            inboxMessages = this.bus.readInbox(States.get().getName(), true);
        } catch (IOException e) {
            return String.format("错误：读取收件箱失败, e = %s", e);
        }
        return JSON.toJSONString(inboxMessages);
    }
}
