package org.jc.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.Maps;
import com.openai.models.chat.completions.*;
import org.jc.message.Message;
import org.jc.message.MessageBus;
import org.jc.message.ReadResult;
import org.jc.task.TaskBoard;
import org.jc.task.TaskClaimResult;
import org.jc.team.Team;
import org.jc.team.TeammateManager;

import java.util.*;

public class Agent extends BaseAgent {
    private MessageBus bus;
    private TaskBoard taskBoard;
    private TeammateManager teammateManager;
    private final Map<String, AgentShutdownRequest> shutdownRequests = new HashMap<>();
    private final Map<String, AgentPlanRequest> planRequests = new HashMap<>();
    private final Object trackerLock = new Object();

    protected Agent() {
    }

    public static Agent of() {
        return new Agent();
    }

    public MessageBus getBus() {
        return bus;
    }

    public void setBus(MessageBus bus) {
        this.bus = bus;
    }

    public TeammateManager getTeammateManager() {
        return teammateManager;
    }

    @Override
    public void setTeam(Team team) {
        super.setTeam(team);
        this.teammateManager = new TeammateManager(team);
    }

    public Map<String, AgentShutdownRequest> getShutdownRequests() {
        return shutdownRequests;
    }

    public Map<String, AgentPlanRequest> getPlanRequests() {
        return planRequests;
    }

    public Object getTrackerLock() {
        return trackerLock;
    }

    public TaskBoard getTaskBoard() {
        return taskBoard;
    }

    public void setTaskBoard(TaskBoard taskBoard) {
        this.taskBoard = taskBoard;
    }

    /**
     * 领取任务
     */
    public String claimTask(String arguments, String owner) {
        Integer taskId = JSON.parseObject(arguments).getInteger("taskId");
        TaskClaimResult taskClaimResult = this.getTaskBoard().claimTask(taskId, owner);
        return taskClaimResult.getInfo();
    }


    // ===================== 创建/启动队友 =====================
    public String spawnTeammate(String arguments, List<ChatCompletionTool> tools, ToolHandlers toolHandlers
            , PromptProvider promptProvider) {
        JSONObject object = JSON.parseObject(arguments);
        String name = object.getString("name");
        String role = object.getString("role");

        TeammateAgent teammateAgent = TeammateAgent.of();
        teammateAgent.setName(name);
        teammateAgent.setRole(role);
        teammateAgent.setPromptProvider(promptProvider);
        teammateAgent.setTeam(this.getTeam());
        teammateAgent.setModel(this.getModel());
        teammateAgent.setLead(this);
        teammateAgent.setMaxLoopTimes(50);
        teammateAgent.setTools(tools);
        teammateAgent.setToolHandlers(toolHandlers);
        teammateAgent.setConfig(this.getConfig().getTeammateConfig());
        return this.getTeammateManager().spawn(
                teammateAgent, object.getString("prompt")
        );
    }

    public String sendMessage(String arguments) {
        JSONObject object = JSON.parseObject(arguments);
        String msgType = object.getString("msgType");
        return this.getBus().send(
                name
                , object.getString("to")
                , object.getString("content")
                , msgType == null || msgType.isBlank() ? "message" : msgType
                , null
        );
    }

    public String broadcast(String arguments) {
        JSONObject object = JSON.parseObject(arguments);

        String sender = this.getName();
        List<String> teammates = this.getTeam().listTeammateNames();
        int count = 0;
        if (teammates != null) {
            for (String name : teammates) {
                if (!name.equals(sender)) {
                    this.getBus().send(sender, name, object.getString("content"), "broadcast", null);
                    count++;
                }
            }
        }
        return "广播 " + count + " 个队友";
    }

    /**
     * 请求队员停止执行
     *
     * @param arguments
     * @return
     */
    public String shutdownRequest(String arguments) {
        String teammate = JSON.parseObject(arguments).getString("teammate");
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        synchronized (this.getTrackerLock()) {
            this.getShutdownRequests().put(requestId, new AgentShutdownRequest(teammate, "pending"));
        }
        HashMap<String, Object> extra = Maps.newHashMap();
        extra.put("requestId", requestId);
        this.getBus().send(this.getName(), teammate, "请优雅地停止", "shutdown_request", extra);
        return String.format("已向 %s 发送停止请求 %s（状态：待处理）", requestId, teammate);
    }

    /**
     * 批准或拒绝队友的计划。需提供 request_id、approve 以及可选的反馈信息
     *
     * @param arguments
     * @return
     */
    public String planReview(String arguments) {
        JSONObject object = JSON.parseObject(arguments);
        String requestId = object.getString("requestId");
        boolean approve = object.getBoolean("approve");
        String feedback = object.getString("feedback");

        AgentPlanRequest planRequest;
        synchronized (this.getTrackerLock()) {
            planRequest = this.getPlanRequests().get(requestId);
        }

        if (planRequest == null) {
            return String.format("错误：未知的计划请求ID %s", requestId);
        }

        synchronized (this.getTrackerLock()) {
            planRequest.setStatus(approve ? "approved" : "rejected");
        }

        HashMap<String, Object> extra = Maps.newHashMap();
        extra.put("requestId", requestId);
        extra.put("approve", approve);
        extra.put("feedback", feedback);
        this.getBus().send(this.getName(), planRequest.getFrom(), feedback
                , "plan_approval_response", extra);
        return String.format("把 %s 的计划状态设置为 %s"
                , planRequest.getFrom(), planRequest.getStatus());
    }

    /**
     * 查询停止请求的状态，即队员的应答情况
     *
     * @param arguments
     * @return
     */
    public String shutdownResponse(String arguments) {
        JSONObject object = JSON.parseObject(arguments);
        String requestId = object.getString("requestId");
        synchronized (this.getTrackerLock()) {
            return JSON.toJSONString(shutdownRequests.get(requestId));
        }
    }


    public String readInbox(String arguments) {
        return JSON.toJSONString(this.getBus().readInbox(arguments, true));
    }

    public ReadResult readInbox(List<ChatCompletionMessageParam> messages) {
        if (!this.getConfig().isReadInbox()) {
            return ReadResult.of();
        }
        int size = 0;
        List<Message> inboxMessages = this.getBus().readInbox(this.getName(), false);
        if (inboxMessages != null && !inboxMessages.isEmpty()) {
            for (Message inboxMessage : inboxMessages) {
                size++;
                if (Objects.equals("shutdownRequest", inboxMessage.getType())
                        && Objects.equals("direct", this.getConfig().getShutdownResponse())) {
                    return ReadResult.of(size, true);
                }

                messages.add(
                        ChatCompletionMessageParam.ofUser(
                                ChatCompletionUserMessageParam
                                        .builder()
                                        .content(String.format("<inbox>\n%s\n</inbox>", JSON.toJSONString(inboxMessage)))
                                        .build()
                        ));

            }
        }
        return ReadResult.of(size);
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
