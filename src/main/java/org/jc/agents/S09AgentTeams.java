package org.jc.agents;

import com.openai.models.chat.completions.*;
import org.jc.Commons;
import org.jc.Tools;
import org.jc.agent.Agent;
import org.jc.agent.AgentConfig;
import org.jc.agent.ToolHandlers;
import org.jc.message.MessageBus;
import org.jc.team.TeammateManager;

import java.util.*;

public class S09AgentTeams {

    public static final String LEAD = "lead";
    public static final String QWEN_3_5_PLUS = "qwen3.5-plus";
    private static TeammateManager teammateManager = new TeammateManager(Commons.TEAM_DIR);
    private static MessageBus bus = new MessageBus(Commons.INBOX_DIR);

    private static List<ChatCompletionTool> teammateTools = List.of(
            Tools.bashTool()
            , Tools.readFileTool()
            , Tools.writeFileTool()
            , Tools.editFileTool()
            , Tools.sendMessageTool()
            , Tools.readInboxTool()
    );
    private static ToolHandlers teammateToolHandlers = ToolHandlers.of()
            .add("bash", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runBash(arguments))
            .add("readFile", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runReadFile(arguments))
            .add("writeFile", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runWriteFile(arguments))
            .add("editFile", (ToolHandlers.TeammateToolCall) (agent, arguments) -> Tools.runEditFile(arguments))
            .add("sendMessage", (ToolHandlers.TeammateToolCall) (agent, arguments) -> agent.getLead().sendMessage(arguments))
            .add("readInbox", (ToolHandlers.TeammateToolCall) (agent, arguments) -> agent.getLead().readInbox(arguments));

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
            .add("broadcast", (ToolHandlers.LeadToolCall) Agent::broadcast);

    /**
     * 测试输入：
     * <p>
     * 生成 alice（程序员）和 bob（测试人员）。让 alice 给 bob 发送一条消息
     * 向所有队友广播 “状态更新：第一阶段已完成”
     * 查看负责人收件箱中的所有消息
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
                config.setTrackerLock(null);

                Agent agent = Agent.of();
                agent.setName(LEAD);
                agent.setModel(QWEN_3_5_PLUS);
                agent.setPrompt("你是 " + Commons.CWD + " 工作目录下的团队负责人。创建团队成员，并通过收件箱进行通信协作");
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
