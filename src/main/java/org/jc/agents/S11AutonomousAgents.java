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
import org.jc.component.plan.PlanRequests;
import org.jc.component.shutdown.ShutdownRequests;
import org.jc.component.state.LeadState;
import org.jc.component.task.Tasks;
import org.jc.component.team.Team;
import org.jc.component.tool.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class S11AutonomousAgents extends AbstractModule {

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
        leadToolBinder.addBinding().to(S11SpawnTeammateTool.class);
        //请求团队成员停止工作
        leadToolBinder.addBinding().to(ShutdownRequestTool.class);
        //检查请求状态
        leadToolBinder.addBinding().to(ShutdownCheckTool.class);
        //审核团队成员的工作计划
        leadToolBinder.addBinding().to(PlanReviewTool.class);
        //领导空闲
        leadToolBinder.addBinding().to(IdleLeadTool.class);

        //任务相关
        leadToolBinder.addBinding().to(TaskCreateTool.class);
        leadToolBinder.addBinding().to(TaskUpdateTool.class);
        leadToolBinder.addBinding().to(TaskGetTool.class);
        leadToolBinder.addBinding().to(TaskListTool.class);
        //认领任务
        leadToolBinder.addBinding().to(ClaimTaskTool.class);


        //队员工具
        Multibinder<TeammateTool> teammateToolBinder = Multibinder.newSetBinder(binder(), TeammateTool.class);
        teammateToolBinder.addBinding().to(BashTool.class);
        teammateToolBinder.addBinding().to(ReadFileTool.class);
        teammateToolBinder.addBinding().to(WriteFileTool.class);
        teammateToolBinder.addBinding().to(EditFileTool.class);
        teammateToolBinder.addBinding().to(SendMessageTool.class);
        teammateToolBinder.addBinding().to(ReadInboxTool.class);

        //响应停止请求
        teammateToolBinder.addBinding().to(ShutdownResponseTool.class);
        //提交计划
        teammateToolBinder.addBinding().to(PlanApprovalTool.class);
        //队员空闲
        teammateToolBinder.addBinding().to(IdleTeammateTool.class);
        //认领任务
        teammateToolBinder.addBinding().to(ClaimTaskTool.class);

        /////////////////////////////////////////////////////////////////////////////////

        //大模型客户端
        bind(OpenAIClient.class).toInstance(Commons.getClient());
        //收件箱
        bind(MessageBus.class).in(Singleton.class);
        //管理停止请求
        bind(ShutdownRequests.class).in(Singleton.class);
        //管理计划请求
        bind(PlanRequests.class).in(Singleton.class);
        //团队
        bind(Team.class).in(Singleton.class);
        //任务看板
        bind(Tasks.class).in(Singleton.class);
        //循环
        bind(ReActs.class).to(ReActsImpl.class);


        bind(LeadReAct.class).in(Singleton.class);
        bind(TeammateReAct.class).to(S11TeammateReAct.class);
    }

    /**
     * 测试输入：
     * <p>
     * 在任务面板上创建 3 个任务，然后生成成员 Alice 和 Bob，观察他们自动认领任务
     * 生成一名程序员队友，让其从任务面板中自行寻找并执行任务。
     * 创建带有依赖关系的任务，观察团队成员遵循阻塞顺序执行任务。
     *
     * @param args
     */
    public static void main(String[] args) {
        Injector injector = Guice.createInjector(new S11AutonomousAgents());

        final ReActs reActs = injector.getInstance(ReActs.class);

        LeadState state = LeadState.builder()
                .name("lead")
                .model("qwen3.5-plus")
                .role("lead")
                .prompt("你是 " + Commons.CWD + " 目录的团队负责人。团队成员具备自主工作能力，会自行寻找任务开展工作")
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
