package org.jc.component.tool;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.jc.component.inbox.InBoxMessage;
import org.jc.component.tool.args.PlanApprovalToolArgs;
import org.jc.component.enums.InBoxMsgType;
import org.jc.component.enums.PlanRequestStatus;
import org.jc.component.inbox.MessageBus;
import org.jc.component.plan.PlanRequest;
import org.jc.component.plan.PlanRequests;
import org.jc.component.state.States;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class PlanApprovalTool extends BaseTool<PlanApprovalToolArgs> {
    private final MessageBus bus;
    private final PlanRequests planRequests;

    @Inject
    public PlanApprovalTool(MessageBus bus, PlanRequests planRequests) {
        super("planApproval", PlanApprovalToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "planApproval",
                        "description": "提交计划以供负责人审批，请提供计划内容",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "plan": {
                                    "type": "string"
                                }
                            },
                            "required": [
                                "plan"
                            ]
                        }
                    }
                }
                """);
        this.bus = Objects.requireNonNull(bus);
        this.planRequests = Objects.requireNonNull(planRequests);
    }

    @Override
    public String doCall(PlanApprovalToolArgs arguments) {
        String plan = arguments.getPlan();
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        States.get().getPlanLock().lock();
        try {
            this.planRequests.put(requestId, new PlanRequest(States.get().getName()
                    , plan, PlanRequestStatus.PENDING.getValue()));
        } finally {
            States.get().getPlanLock().unlock();
        }


        HashMap<String, Object> extra = Maps.newHashMap();
        extra.put("requestId", requestId);
        extra.put("plan", plan);

        InBoxMessage message = new InBoxMessage();
        message.setContent(plan);
        message.setExtra(extra);
        message.setType(InBoxMsgType.PLAN_APPROVAL_RESPONSE.getValue());
        message.setFrom(States.get().getName());
        message.setTimestamp(System.currentTimeMillis());
        try {
            this.bus.send(States.teammate().getLead(), message);
        } catch (IOException e) {
            return "错误：提交计划失败";
        }
        return String.format("计划已提交（request_id=%s），正在等待负责人审批", requestId);
    }
}
