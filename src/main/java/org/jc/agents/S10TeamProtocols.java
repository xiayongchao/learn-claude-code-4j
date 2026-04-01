package org.jc.agents;

import com.openai.models.chat.completions.*;
import org.jc.Commons;
import org.jc.Tools;
import org.jc.agent.Agent;
import org.jc.agent.AgentConfig;
import org.jc.agent.TeammateAgent;
import org.jc.agent.ToolHandlers;
import org.jc.message.MessageBus;
import org.jc.team.TeammateManager;

import java.util.*;

public class S10TeamProtocols {
    public static final String QWEN_3_5_PLUS = "qwen3.5-plus";
    public static final String LEAD = "lead";
    private static TeammateManager teammateManager = new TeammateManager(Commons.TEAM_DIR);
    private static MessageBus bus = new MessageBus(Commons.INBOX_DIR);
    private static Object trackerLock = new Object();

    private static List<ChatCompletionTool> teammateTools = List.of(
            Tools.bashTool()
            , Tools.readFileTool()
            , Tools.writeFileTool()
            , Tools.editFileTool()
            , Tools.sendMessageTool()
            , Tools.readInboxTool()
            , Tools.teammateShutdownResponseTool()
            , Tools.planApprovalTool()
    );
    private static ToolHandlers teammateToolHandlers = ToolHandlers.of()
            .add("bash", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runBash(arguments))
            .add("readFile", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runReadFile(arguments))
            .add("writeFile", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runWriteFile(arguments))
            .add("editFile", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runEditFile(arguments))
            .add("sendMessage", (ToolHandlers.TeammateToolCall) (agent, arguments) -> agent.getLead().sendMessage(arguments))
            .add("readInbox", (ToolHandlers.TeammateToolCall) (agent, arguments) -> agent.getLead().readInbox(arguments))
            .add("shutdownResponse", (ToolHandlers.TeammateToolCall) TeammateAgent::shutdownResponse)
            .add("planApproval", (ToolHandlers.TeammateToolCall) TeammateAgent::planApproval);

    private static List<ChatCompletionTool> leadTools = List.of(
            Tools.bashTool()
            , Tools.readFileTool()
            , Tools.writeFileTool()
            , Tools.editFileTool()
            , Tools.spawnTeammateTool()
            , Tools.listTeammatesTool()
            , Tools.sendMessageTool()
            , Tools.readInboxTool()
            , Tools.broadcastTool()
            , Tools.shutdownRequestTool()
            , Tools.leadShutdownResponseTool()
            , Tools.planReviewTool()
    );
    private static ToolHandlers leadToolHandlers = ToolHandlers.of()
            .add("bash", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runBash(arguments))
            .add("readFile", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runReadFile(arguments))
            .add("writeFile", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runWriteFile(arguments))
            .add("editFile", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runEditFile(arguments))
            .add("spawnTeammate", (ToolHandlers.LeadToolCall) (agent, arguments) -> agent.spawnTeammate(arguments, teammateTools, teammateToolHandlers))
            .add("listTeammates", (ToolHandlers.LeadToolCall) (agent, arguments) -> agent.listTeammate())
            .add("sendMessage", (ToolHandlers.LeadToolCall) Agent::sendMessage)
            .add("readInbox", (ToolHandlers.LeadToolCall) Agent::readInbox)
            .add("broadcast", (ToolHandlers.LeadToolCall) Agent::broadcast)
            .add("shutdownRequest", (ToolHandlers.LeadToolCall) Agent::shutdownRequest)
            .add("shutdownResponse", (ToolHandlers.LeadToolCall) Agent::shutdownResponse)
            .add("planReview", (ToolHandlers.LeadToolCall) Agent::planReview);

    /**
     * 测试输入：
     * <p>
     * 先生成程序员 alice，然后你再发送关闭请求给她
     * 列出队员，查看 alice 在关闭获批后的状态
     * 生成 bob 并分配一项高风险重构任务。审核并拒绝他的计划
     * 生成 charlie，让他提交一项计划，然后批准该计划
     *
     * @param args
     */
    public static void main(String[] args) {
        // 1. 创建 Scanner 对象
        Scanner scanner = new Scanner(System.in);
        try {
            List<ChatCompletionMessageParam> messages = new ArrayList<>();
            while (true) {
                // 2. 提示用户输入
                System.out.print("请输入>");

                // 3. 读取输入的一行字符串
                String query = scanner.nextLine();
                if (List.of("q", "exit", "").contains(query.strip())) {
                    break;
                }

                messages.add(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam
                                .builder()
                                .content(query)
                                .build()
                ));


                AgentConfig config = AgentConfig.of();
                config.setReadInbox(true);
                config.setWorkDir(Commons.CWD);
                config.setTrackerLock(trackerLock);

                Agent agent = Agent.of();
                agent.setName(LEAD);
                agent.setPrompt("你是 " + Commons.CWD + " 工作目录下的团队负责人。创建团队成员，并通过收件箱进行通信协作");
                agent.setModel(QWEN_3_5_PLUS);
                agent.setBus(bus);
                agent.setTeammateManager(teammateManager);
                agent.setTools(leadTools);
                agent.setToolHandlers(leadToolHandlers);
                agent.setConfig(config);

                ChatCompletionMessageParam last = agent.loop(messages);
                System.out.println(">>" + Commons.getText(last));
            }
        } finally {
            // 用完关闭
            scanner.close();
        }
    }

}
