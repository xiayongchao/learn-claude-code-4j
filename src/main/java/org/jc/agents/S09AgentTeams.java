package org.jc.agents;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.jc.Commons;
import org.jc.component.inbox.MessageBus;
import org.jc.component.loop.*;
import org.jc.component.state.LeadState;
import org.jc.component.team.Team;
import org.jc.component.tool.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class S09AgentTeams extends AbstractModule {
    @Override
    protected void configure() {
        //领导工具
        Multibinder<LeadTool> leadToolBinder = Multibinder.newSetBinder(binder(), LeadTool.class);
        leadToolBinder.addBinding().to(BashTool.class);
        leadToolBinder.addBinding().to(ReadFileTool.class);
        leadToolBinder.addBinding().to(WriteFileTool.class);
        leadToolBinder.addBinding().to(EditFileTool.class);
        leadToolBinder.addBinding().to(SendMessageTool.class);
        leadToolBinder.addBinding().to(ReadInboxTool.class);

        leadToolBinder.addBinding().to(BroadcastTool.class);
        leadToolBinder.addBinding().to(S09SpawnTeammateTool.class);


        //队员工具
        Multibinder<TeammateTool> teammateToolBinder = Multibinder.newSetBinder(binder(), TeammateTool.class);
        teammateToolBinder.addBinding().to(BashTool.class);
        teammateToolBinder.addBinding().to(ReadFileTool.class);
        teammateToolBinder.addBinding().to(WriteFileTool.class);
        teammateToolBinder.addBinding().to(EditFileTool.class);
        teammateToolBinder.addBinding().to(SendMessageTool.class);
        teammateToolBinder.addBinding().to(ReadInboxTool.class);

        //大模型客户端
        bind(OpenAIClient.class).toInstance(Commons.getClient());
        //收件箱
        bind(MessageBus.class).in(Singleton.class);
        //团队
        bind(Team.class).in(Singleton.class);
        //循环
        bind(ReActs.class).to(ReActsImpl.class);

        bind(LeadReAct.class).in(Singleton.class);
        bind(TeammateReAct.class).to(S09TeammateReAct.class);
    }

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
        Injector injector = Guice.createInjector(new S09AgentTeams());

        final ReActs reActs = injector.getInstance(ReActs.class);

        LeadState state = LeadState.builder()
                .name("lead")
                .model("qwen3.5-plus")
                .role("lead")
                .prompt("你是 " + Commons.CWD + " 工作目录下的团队负责人。创建团队成员，并通过收件箱进行通信协作")
                .workDir(Commons.CWD)
                .messages(new ArrayList<>())
                .build();

        // 1. 创建 Scanner 对象
        Scanner scanner = new Scanner(System.in);
        try {
            List<ChatCompletionMessageParam> messages = state.getMessages();
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

                state.setMessages(messages);
                ChatCompletionMessageParam last = reActs.start(state);
                System.out.println(">>" + Commons.getText(last));
            }
        } finally {
            // 用完关闭
            scanner.close();
        }
    }

}
