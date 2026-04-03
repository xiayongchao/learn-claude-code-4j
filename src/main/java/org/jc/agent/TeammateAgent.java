package org.jc.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.Maps;
import com.openai.models.chat.completions.*;
import org.jc.message.ReadResult;
import org.jc.task.Task;
import org.jc.task.TaskClaimResult;
import org.jc.tool.ToolCallResult;

import java.util.*;

public class TeammateAgent extends BaseAgent {
    private int maxLoopTimes;
    private Agent lead;

    protected TeammateAgent() {
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

    public Agent getLead() {
        return lead;
    }

    public void setLead(Agent lead) {
        this.lead = lead;
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
        synchronized (this.getLead().getTrackerLock()) {
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

        synchronized (this.getLead().getTrackerLock()) {
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
    public void loop(String prompt) {
        List<ChatCompletionMessageParam> messages = new ArrayList<>();

        messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam
                        .builder()
                        .content(prompt)
                        .build()
        ));

        boolean idlePoll = true;
        while (idlePoll) {
            for (int i = 0; i < this.getMaxLoopTimes(); i++) {
                // 读取收件箱
                if (this.getLead().readInbox(messages).isShutdown()) {
                    //直接停止
                    this.getTeam().shutdown(this.getName());
                    return;
                }

                ChatCompletionAssistantMessageParam assistantMessage = this.chat(messages);

                Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = assistantMessage.toolCalls();
                if (toolCallsOptional.isEmpty()) {
                    break;
                }

                ToolCallResult toolCallResult = this.toolCall(toolCallsOptional.get(), messages);
                if (toolCallResult.isIdle()) {
                    break;
                }
            }

            if (this.getTeam().isShutdown(this.getName())) {
                return;
            }

            // 结束后设置 idle
            this.getTeam().idle(this.getName());
            idlePoll = this.idlePoll(messages);
        }
    }

    /**
     * false:停止
     * true：继续
     *
     * @param messages
     * @return
     */
    private boolean idlePoll(List<ChatCompletionMessageParam> messages) {
        if (!this.getConfig().isEnableIdlePoll()) {
            return false;
        }

        boolean resume = false;
        int pollInterval = this.getConfig().getPollInterval();
        int polls = this.getConfig().getIdleTimeout() / pollInterval;
        for (int i = 0; i < polls; i++) {
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // 读取收件箱
            ReadResult readResult = this.getLead().readInbox(messages);
            if (readResult.isShutdown()) {
                //直接停止
                this.getTeam().shutdown(this.getName());
                return false;
            }
            if (readResult.getSize() > 0) {
                resume = true;
                break;
            }

            //认领任务
            Task unclaimedTask = this.getLead().getTaskBoard().getUnclaimedTask();
            if (unclaimedTask != null) {
                TaskClaimResult taskClaimResult = this.getLead().getTaskBoard()
                        .claimTask(unclaimedTask.getTaskId(), this.getName());
                if (taskClaimResult.isError()) {
                    continue;
                }
                if (messages.size() <= 3) {
                    //身份重新注入
                    this.identityReInjection(messages);
                }
                messages.add(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(String.format("<auto-claimed>任务 %s: %s\n%s</auto-claimed>"
                                        , unclaimedTask.getTaskId(), unclaimedTask.getSubject(), unclaimedTask.getDescription()))
                                .build()
                ));
                messages.add(ChatCompletionMessageParam.ofAssistant(
                        ChatCompletionAssistantMessageParam.builder()
                                .content(String.format("已认领任务 %s，正在处理中。", unclaimedTask.getTaskId()))
                                .build()
                ));
                resume = true;
                break;
            }
        }
        if (!resume) {
            this.getTeam().shutdown(this.getName());
            return false;
        }

        this.getTeam().working(this.getName());
        return true;
    }

    private void identityReInjection(List<ChatCompletionMessageParam> messages) {
        List<ChatCompletionMessageParam> tmpMessages = new ArrayList<>(messages);
        messages.clear();

        messages.add(this.makeIdentityBlock());
        messages.add(ChatCompletionMessageParam.ofAssistant(
                ChatCompletionAssistantMessageParam.builder()
                        .content(String.format("我是 %s，继续执行", this.getName()))
                        .build()
        ));
        messages.addAll(tmpMessages);
    }
}
