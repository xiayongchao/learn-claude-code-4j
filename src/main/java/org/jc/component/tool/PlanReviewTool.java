package org.jc.component.tool;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.jc.component.inbox.InBoxMessage;
import org.jc.component.tool.args.PlanReviewToolArgs;
import org.jc.component.enums.InBoxMsgType;
import org.jc.component.inbox.MessageBus;
import org.jc.component.plan.PlanRequest;
import org.jc.component.plan.PlanRequests;
import org.jc.component.state.States;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;

public class PlanReviewTool extends BaseTool<PlanReviewToolArgs> {
    private final MessageBus bus;
    private final PlanRequests planRequests;

    @Inject
    public PlanReviewTool(MessageBus bus, PlanRequests planRequests) {
        super("planReview", PlanReviewToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "planReview",
                        "description": "批准或拒绝团队成员的方案，需要提供 requestId、approve 参数，以及可选的 feedback 参数",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "requestId": {
                                    "type": "string"
                                },
                                "approve": {
                                    "type": "boolean"
                                },
                                "feedback": {
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
        this.planRequests = Objects.requireNonNull(planRequests);
    }

    @Override
    public String doCall(PlanReviewToolArgs arguments) {
        String requestId = arguments.getRequestId();
        Boolean approve = arguments.getApprove();
        String feedback = arguments.getFeedback();

        PlanRequest planRequest;
        States.get().getPlanLock().lock();
        try {
            planRequest = this.planRequests.get(requestId);
        } finally {
            States.get().getPlanLock().unlock();
        }

        if (planRequest == null) {
            return String.format("错误：未知的计划请求ID %s", requestId);
        }

        States.get().getPlanLock().lock();
        try {
            planRequest.setStatus(Boolean.TRUE.equals(approve) ? "approved" : "rejected");
        } finally {
            States.get().getPlanLock().unlock();
        }

        HashMap<String, Object> extra = Maps.newHashMap();
        extra.put("requestId", requestId);
        extra.put("approve", approve);
        extra.put("feedback", feedback);

        InBoxMessage message = new InBoxMessage();
        message.setType(InBoxMsgType.PLAN_APPROVAL_RESPONSE.getValue());
        message.setFrom(States.get().getName());
        message.setTimestamp(System.currentTimeMillis());
        message.setExtra(extra);
        message.setContent(feedback);

        String from = planRequest.getFrom();
        try {
            this.bus.send(from, message);
        } catch (IOException e) {
            return String.format("错误：%s 计划请求(requestId=%s) 失败"
                    , Boolean.TRUE.equals(approve) ? "批准" : "拒绝"
                    , from);
        }
        return String.format("已经把 %s 的计划状态设置为： %s", from, planRequest.getStatus());
    }
}
