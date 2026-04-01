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
            .add("bash", (agent, arguments) -> Tools.runBash(arguments))
            .add("readFile", (agent, arguments) -> Tools.runReadFile(arguments))
            .add("writeFile", (agent, arguments) -> Tools.runWriteFile(arguments))
            .add("editFile", (agent, arguments) -> Tools.runEditFile(arguments))
            .add("sendMessage", Agent::sendMessage)
            .add("readInbox", Agent::readInbox);

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
            .add("bash", (agent, arguments) -> Tools.runBash(arguments))
            .add("readFile", (agent, arguments) -> Tools.runReadFile(arguments))
            .add("writeFile", (agent, arguments) -> Tools.runWriteFile(arguments))
            .add("editFile", (agent, arguments) -> Tools.runEditFile(arguments))
            .add("spawnTeammate", (agent, arguments) -> agent.spawnTeammate(arguments, teammateTools, teammateToolHandlers))
            .add("listTeammates", (agent, arguments) -> agent.listTeammate())
            .add("sendMessage", Agent::sendMessage)
            .add("readInbox", Agent::readInbox)
            .add("broadcast", Agent::broadcast);

    /**
     * 测试输入：
     * <p>
     * 生成 alice（程序员）和 bob（测试人员）。让 alice 给 bob 发送一条消息。
     * 向所有队友广播 “状态更新：第一阶段已完成”。
     * 查看负责人收件箱中的所有消息。
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

                ChatCompletionMessageParam last = Agent.of()
                        .name("lead")
                        .prompt("你是 " + Commons.CWD + " 工作目录下的团队负责人。创建团队成员，并通过收件箱进行通信协作")
                        .model("qwen3.5-plus")
                        .bus(bus)
                        .teammateManager(teammateManager)
                        .tools(leadTools)
                        .toolHandlers(leadToolHandlers)
                        .config(AgentConfig.of()
                                .readInbox(true)
                                .workDir(Commons.CWD))
                        .loop(messages);
                System.out.println(">>" + Commons.getText(last));
            }
        } finally {
            // 用完关闭
            scanner.close();
        }
    }

}
