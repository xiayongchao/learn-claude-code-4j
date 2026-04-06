package org.jc.component.tool;

import com.alibaba.fastjson2.JSON;
import com.google.inject.Inject;
import org.jc.component.tool.args.ShutdownCheckToolArgs;
import org.jc.component.shutdown.ShutdownRequests;

import java.util.Objects;

public class ShutdownCheckTool extends BaseTool<ShutdownCheckToolArgs> {
    private final ShutdownRequests shutdownRequests;

    @Inject
    public ShutdownCheckTool(ShutdownRequests shutdownRequests) {
        super("shutdownCheck", ShutdownCheckToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "shutdownCheck",
                        "description": "通过 requestId 检查停止请求的状态",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "requestId": {
                                    "type": "string"
                                }
                            },
                            "required": [
                                "requestId"
                            ]
                        }
                    }
                }
                """);
        this.shutdownRequests = Objects.requireNonNull(shutdownRequests);
    }

    @Override
    public String doCall(ShutdownCheckToolArgs arguments) {
        String requestId = arguments.getRequestId();
        return JSON.toJSONString(this.shutdownRequests.get(requestId));
    }
}
