package org.jc.component.tool;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.jc.component.inbox.InBoxMessage;
import org.jc.component.tool.args.ShutdownResponseToolArgs;
import org.jc.component.enums.InBoxMsgType;
import org.jc.component.enums.ShutdownRequestStatus;
import org.jc.component.inbox.MessageBus;
import org.jc.component.shutdown.ShutdownRequest;
import org.jc.component.shutdown.ShutdownRequests;
import org.jc.component.state.States;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;

public class ShutdownResponseTool extends BaseTool<ShutdownResponseToolArgs> {
    private final MessageBus bus;
    private final ShutdownRequests shutdownRequests;

    @Inject
    public ShutdownResponseTool(MessageBus bus, ShutdownRequests shutdownRequests) {
        super("shutdownResponse", ShutdownResponseToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "shutdownResponse",
                        "description": "响应停止请求，批准则停止工作，拒绝则继续工作",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "requestId": {
                                    "type": "string"
                                },
                                "approve": {
                                    "type": "boolean"
                                },
                                "reason": {
                                    "type": "string"
                                }
                            },
                            "required": [
                                "requestId",
                                "approve"
                            ]
                        }
                    }
                }
                """);
        this.bus = Objects.requireNonNull(bus);
        this.shutdownRequests = Objects.requireNonNull(shutdownRequests);
    }

    @Override
    public String doCall(ShutdownResponseToolArgs arguments) {
        String requestId = arguments.getRequestId();
        Boolean approve = arguments.getApprove();
        String reason = arguments.getReason();

        States.get().getShutdownLock().lock();
        try {
            ShutdownRequest shutdownRequest = this.shutdownRequests.get(requestId);
            if (shutdownRequest != null) {
                shutdownRequest.setStatus(Boolean.TRUE.equals(approve)
                        ? ShutdownRequestStatus.APPROVED.getValue() : ShutdownRequestStatus.REJECTED.getValue());
            }
        } finally {
            States.get().getShutdownLock().unlock();
        }

        HashMap<String, Object> extra = Maps.newHashMap();
        extra.put("requestId", requestId);
        extra.put("approve", approve);

        InBoxMessage message = new InBoxMessage();
        message.setType(InBoxMsgType.SHUTDOWN_RESPONSE.getValue());
        message.setFrom(States.get().getName());
        message.setTimestamp(System.currentTimeMillis());
        message.setExtra(extra);
        message.setContent(reason);
        try {
            this.bus.send(States.teammate().getLead(), message);
        } catch (IOException e) {
            return String.format("%s停止请求(requestId=%s)失败"
                    , Boolean.TRUE.equals(approve) ? "批准" : "拒绝", requestId);
        }

        //设置响应标志
        if (Boolean.TRUE.equals(approve)) {
            States.teammate().setShutdown(true);
        }
        return String.format("停止请求(requestId=%s)已%s"
                , requestId, Boolean.TRUE.equals(approve) ? "批准" : "拒绝");
    }
}
