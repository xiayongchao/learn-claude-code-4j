package org.jc.component.tool;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.jc.component.inbox.InBoxMessage;
import org.jc.component.tool.args.ShutdownRequestToolArgs;
import org.jc.component.enums.InBoxMsgType;
import org.jc.component.enums.ShutdownRequestStatus;
import org.jc.component.inbox.MessageBus;
import org.jc.component.shutdown.ShutdownRequest;
import org.jc.component.shutdown.ShutdownRequests;
import org.jc.component.state.States;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class ShutdownRequestTool extends BaseTool<ShutdownRequestToolArgs> {
    private final MessageBus bus;
    private final ShutdownRequests shutdownRequests;

    @Inject
    public ShutdownRequestTool(MessageBus bus, ShutdownRequests shutdownRequests) {
        super("shutdownRequest", ShutdownRequestToolArgs.class, """
                {
                     "type": "function",
                     "function": {
                         "name": "shutdownRequest",
                         "description": "请求某位团队成员优雅平稳地停止运行，并返回用于追踪记录的请求ID（request_id）。",
                         "parameters": {
                             "type": "object",
                             "properties": {
                                 "teammate": {
                                     "type": "string"
                                 }
                             },
                             "required": [
                                 "teammate"
                             ]
                         }
                     }
                 }
                """);
        this.bus = Objects.requireNonNull(bus);
        this.shutdownRequests = Objects.requireNonNull(shutdownRequests);
    }

    @Override
    public String doCall(ShutdownRequestToolArgs arguments) {
        String teammate = arguments.getTeammate();
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        this.shutdownRequests.put(requestId, new ShutdownRequest(teammate
                , ShutdownRequestStatus.PENDING.getValue()));

        HashMap<String, Object> extra = Maps.newHashMap();
        extra.put("requestId", requestId);

        InBoxMessage message = new InBoxMessage();
        message.setType(InBoxMsgType.SHUTDOWN_REQUEST.getValue());
        message.setFrom(States.get().getName());
        message.setTimestamp(System.currentTimeMillis());
        message.setExtra(extra);
        message.setContent("请优雅地停止你当前的工作");
        try {
            this.bus.send(teammate, message);
        } catch (IOException e) {
            return String.format("向 '%s' 发送停止请求失败", teammate);
        }
        return String.format("已经向 '%s' 发送了停止请求: %s (状态: 待处理)", teammate, requestId);
    }
}
