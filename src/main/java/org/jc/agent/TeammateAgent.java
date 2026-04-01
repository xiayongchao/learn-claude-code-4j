package org.jc.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.Maps;
import com.openai.models.chat.completions.*;
import org.jc.team.Teammate;

import java.util.*;

public class TeammateAgent extends BaseAgent {
    private int maxLoopTimes;
    private String role;
    private Agent lead;

    protected TeammateAgent() {
        this.prompt = "你是: %s, 角色: %s, 工作目录: %s";
    }

    public static TeammateAgent of() {
        return new TeammateAgent();
    }


    public int getMaxLoopTimes() {
        return maxLoopTimes;
    }

    public void setMaxLoopTimes(int maxLoopTimes) {
        this.maxLoopTimes = maxLoopTimes;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Agent getLead() {
        return lead;
    }

    public void setLead(Agent lead) {
        this.lead = lead;
    }

    public String getPrompt() {
        return String.format(this.prompt, this.getName(), this.getRole(), this.getConfig().getWorkDir());
    }

    //    -------------------------

    /**
     * 应答负责人的停止请求
     *
     * @param arguments
     * @return
     */
    public String shutdownResponse(String arguments) {
        JSONObject object = JSON.parseObject(arguments);
        String requestId = object.getString("requestId");
        Boolean approve = object.getBoolean("approve");
        String reason = object.getString("reason");
        synchronized (this.getConfig().getTrackerLock()) {
            AgentShutdownRequest shutdownRequest = this.getLead().getShutdownRequests().get(requestId);
            if (shutdownRequest != null) {
                shutdownRequest.setStatus(Boolean.TRUE.equals(approve) ? "approved" : "rejected");
            }
        }

        Map<String, Object> extra = Maps.newHashMap();
        extra.put("requestId", requestId);
        extra.put("approve", approve);
        this.getLead().getBus().send(
                this.getName(), this.getLead().getName(), reason, "shutdown_response", extra
        );
        return "停止 " + (Boolean.TRUE.equals(approve) ? "approved" : "rejected");
    }

    /**
     * 队员提交计划给负责人
     *
     * @param arguments
     * @return
     */
    public String planApproval(String arguments) {
        JSONObject object = JSON.parseObject(arguments);
        String planText = object.getString("plan");
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        synchronized (this.getConfig().getTrackerLock()) {
            AgentPlanRequest planRequest = new AgentPlanRequest();
            planRequest.setFrom(this.getName());
            planRequest.setPlan(planText);
            planRequest.setStatus("pending");
            this.getLead().getPlanRequests().put(requestId, planRequest);
        }


        HashMap<String, Object> extra = Maps.newHashMap();
        extra.put("requestId", requestId);
        extra.put("plan", planText);
        this.getLead().getBus().send(this.getName(), this.getLead().getName(), planText, "plan_approval_response", extra);
        return String.format("计划已提交（请求ID=%s），正在等待负责人审批。", requestId);
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
        for (int i = 0; i < this.getMaxLoopTimes(); i++) {
            // 读取收件箱
            this.getLead().readInbox(messages);

            ChatCompletionAssistantMessageParam assistantMessage = this.chat(messages);

            Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = assistantMessage.toolCalls();
            if (toolCallsOptional.isEmpty()) {
                break;
            }

            this.toolCall(toolCallsOptional.get(), messages);
        }

        // 结束后设置 idle
        Teammate teammate = this.getLead().getTeammateManager().findTeammate(this.getName());
        if (teammate != null && !"shutdown".equals(teammate.getStatus())) {
            teammate.setStatus("idle");
            this.getLead().getTeammateManager().saveTeam();
        }
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }
}
