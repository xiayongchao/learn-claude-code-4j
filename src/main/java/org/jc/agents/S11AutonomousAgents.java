package org.jc.agents;

import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.jc.Commons;
import org.jc.Tools;
import org.jc.agent.Agent;
import org.jc.agent.AgentConfig;
import org.jc.agent.TeammateAgent;
import org.jc.agent.ToolHandlers;
import org.jc.message.MessageBus;
import org.jc.task.TaskBoard;
import org.jc.team.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class S11AutonomousAgents {
    public static final String QWEN_3_5_PLUS = "qwen3.5-plus";
    public static final String LEAD = "lead";
    private static Team team = new Team(Commons.TEAM_DIR);
    private static MessageBus bus = new MessageBus(Commons.INBOX_DIR);
    private static TaskBoard taskBoard = new TaskBoard(Commons.TASK_DIR);

    private static List<ChatCompletionTool> teammateTools = List.of(
            Tools.bashTool()
            , Tools.readFileTool()
            , Tools.writeFileTool()
            , Tools.editFileTool()
            , Tools.sendMessageTool()
            , Tools.readInboxTool()
            , Tools.teammateShutdownResponseTool()
            , Tools.planApprovalTool()

            , Tools.idleTool()
            , Tools.claimTaskTool()
    );
    private static ToolHandlers teammateToolHandlers = ToolHandlers.of()
            .add("bash", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runBash(arguments))
            .add("readFile", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runReadFile(arguments))
            .add("writeFile", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runWriteFile(arguments))
            .add("editFile", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runEditFile(arguments))
            .add("sendMessage", (ToolHandlers.TeammateToolCall) (agent, arguments) -> agent.getLead().sendMessage(arguments))
            .add("readInbox", (ToolHandlers.TeammateToolCall) (agent, arguments) -> agent.getLead().readInbox(arguments))
            .add("shutdownResponse", (ToolHandlers.TeammateToolCall) TeammateAgent::shutdownResponse)
            .add("planApproval", (ToolHandlers.TeammateToolCall) TeammateAgent::planApproval)

            .add("idle", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runIdle())
            .add("claimTask", (ToolHandlers.TeammateToolCall) (agent, arguments)
                    -> agent.getLead().claimTask(arguments, agent.getName()));

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
            , Tools.claimTaskTool()

            , Tools.taskCreateTool()
            , Tools.taskUpdateTool()
            , Tools.taskListTool()
            , Tools.taskGetTool()
    );
    private static ToolHandlers leadToolHandlers = ToolHandlers.of()
            .add("bash", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runBash(arguments))
            .add("readFile", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runReadFile(arguments))
            .add("writeFile", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runWriteFile(arguments))
            .add("editFile", (ToolHandlers.LeadToolCall) (agent, arguments) -> Tools.runEditFile(arguments))
            .add("spawnTeammate", (ToolHandlers.LeadToolCall) (agent, arguments)
                    -> agent.spawnTeammate(arguments, teammateTools, teammateToolHandlers
                    , baseAgent -> String
                            .format("你是: %s, 角色: %s, 所属团队: %s, 工作目录: %s。若无待办工作，请使用闲置工具，系统将自动为你认领新任务"
                                    , baseAgent.getName(), baseAgent.getRole(), baseAgent.getTeam().getTeamName()
                                    , baseAgent.getConfig().getWorkDir())))
            .add("listTeammates", (ToolHandlers.LeadToolCall) (agent, arguments) -> agent.getTeam().listTeammate())
            .add("sendMessage", (ToolHandlers.LeadToolCall) Agent::sendMessage)
            .add("readInbox", (ToolHandlers.LeadToolCall) Agent::readInbox)
            .add("broadcast", (ToolHandlers.LeadToolCall) Agent::broadcast)
            .add("shutdownRequest", (ToolHandlers.LeadToolCall) Agent::shutdownRequest)
            .add("shutdownResponse", (ToolHandlers.LeadToolCall) Agent::shutdownResponse)
            .add("planReview", (ToolHandlers.LeadToolCall) Agent::planReview)
            .add("claimTask", (ToolHandlers.LeadToolCall) (agent, arguments)
                    -> agent.claimTask(arguments, agent.getName()))


            .add("taskCreate", (ToolHandlers.LeadToolCall) (agent, arguments) -> agent.getTaskBoard().create(arguments))
            .add("taskUpdate", (ToolHandlers.LeadToolCall) (agent, arguments) -> agent.getTaskBoard().update(arguments))
            .add("taskList", (ToolHandlers.LeadToolCall) (agent, arguments) -> agent.getTaskBoard().renderList())
            .add("taskGet", (ToolHandlers.LeadToolCall) (agent, arguments) -> agent.getTaskBoard().render(arguments));


    /**
     * 测试输入：
     * <p>
     * 在任务面板上创建 3 个任务，然后生成成员 Alice 和 Bob，观察他们自动认领任务。
     * 生成一名程序员队友，让其从任务面板中自行寻找并执行任务。
     * 创建带有依赖关系的任务，观察团队成员遵循阻塞顺序执行任务。
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


                AgentConfig teammateConfig = AgentConfig.of();
                teammateConfig.setReadInbox(true);
                teammateConfig.setWorkDir(Commons.CWD);
                teammateConfig.setShutdownResponse("direct");
                teammateConfig.setEnableIdlePoll(true);
                teammateConfig.setTeammateConfig(null);
                teammateConfig.setIdleTimeout(5 * 60 * 1000);
                teammateConfig.setPollInterval(5 * 1000);

                AgentConfig config = AgentConfig.of();
                config.setReadInbox(true);
                config.setWorkDir(Commons.CWD);
                config.setTeammateConfig(teammateConfig);

                Agent agent = Agent.of();
                agent.setName(LEAD);
                agent.setPromptProvider(baseAgent -> "你是 " + Commons.CWD + " 目录的团队负责人。团队成员具备自主工作能力，会自行寻找任务开展工作");
                agent.setModel(QWEN_3_5_PLUS);
                agent.setBus(bus);
                agent.setTaskBoard(taskBoard);
                agent.setTeam(team);
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
