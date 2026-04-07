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
import org.jc.component.loop.*;
import org.jc.component.state.LeadState;
import org.jc.component.tool.*;
import org.jc.component.worktree.EventBus;
import org.jc.component.worktree.Worktrees;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class S12WorktreeTaskIsolation extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder<LeadTool> leadToolBinder = Multibinder.newSetBinder(binder(), LeadTool.class);
        leadToolBinder.addBinding().to(BashTool.class);
        leadToolBinder.addBinding().to(ReadFileTool.class);
        leadToolBinder.addBinding().to(WriteFileTool.class);
        leadToolBinder.addBinding().to(EditFileTool.class);

        leadToolBinder.addBinding().to(TaskCreateTool.class);
        leadToolBinder.addBinding().to(TaskListTool.class);
        leadToolBinder.addBinding().to(TaskGetTool.class);
        leadToolBinder.addBinding().to(TaskUpdateTool.class);
        leadToolBinder.addBinding().to(TaskBindWorktreeTool.class);

        leadToolBinder.addBinding().to(WorktreeCreateTool.class);
        leadToolBinder.addBinding().to(WorktreeListTool.class);
        leadToolBinder.addBinding().to(WorktreeStatusTool.class);
        leadToolBinder.addBinding().to(WorktreeRunTool.class);
        leadToolBinder.addBinding().to(WorktreeKeepTool.class);
        leadToolBinder.addBinding().to(WorktreeRemoveTool.class);
        leadToolBinder.addBinding().to(WorktreeEventsTool.class);

        Multibinder<TeammateTool> teammateToolBinder = Multibinder.newSetBinder(binder(), TeammateTool.class);
        teammateToolBinder.addBinding().to(BashTool.class);
        teammateToolBinder.addBinding().to(ReadFileTool.class);
        teammateToolBinder.addBinding().to(WriteFileTool.class);
        teammateToolBinder.addBinding().to(EditFileTool.class);

        teammateToolBinder.addBinding().to(TaskCreateTool.class);
        teammateToolBinder.addBinding().to(TaskListTool.class);
        teammateToolBinder.addBinding().to(TaskGetTool.class);
        teammateToolBinder.addBinding().to(TaskUpdateTool.class);
        teammateToolBinder.addBinding().to(TaskBindWorktreeTool.class);

        teammateToolBinder.addBinding().to(WorktreeCreateTool.class);
        teammateToolBinder.addBinding().to(WorktreeListTool.class);
        teammateToolBinder.addBinding().to(WorktreeStatusTool.class);
        teammateToolBinder.addBinding().to(WorktreeRunTool.class);
        teammateToolBinder.addBinding().to(WorktreeKeepTool.class);
        teammateToolBinder.addBinding().to(WorktreeRemoveTool.class);
        teammateToolBinder.addBinding().to(WorktreeEventsTool.class);


        //大模型客户端
        bind(OpenAIClient.class).toInstance(Commons.getClient());
        bind(TeammateReAct.class).to(S12TeammateReAct.class);
        bind(LeadReAct.class).in(Singleton.class);
        bind(EventBus.class).in(Singleton.class);
        bind(ReActs.class).to(ReActsImpl.class);
    }

    /**
     * 创建后端认证与前端登录页面的任务，然后列出任务。
     * 为任务1创建工作树 `auth-refactor`，然后将任务2绑定到新工作树 `ui-login`
     * 在工作树 `auth-refactor` 中执行命令 `git status --short`
     * 保留工作树 `ui-login`，然后列出工作树并查看事件
     * 删除工作树 `auth-refactor`（`complete_task=true`），然后列出任务、工作树和事件
     *
     * @param args
     */
    public static void main(String[] args) {
        Injector injector = Guice.createInjector(new S12WorktreeTaskIsolation());

        final ReActs reActs = injector.getInstance(ReActs.class);

        Worktrees worktrees = injector.getInstance(Worktrees.class);

        LeadState state = LeadState.builder()
                .name("lead")
                .model("qwen3.5-plus")
                .role("lead")
                .prompt("你是 " + Commons.CWD + " 的编码Agent。使用 task + worktree 工具进行多任务工作。" +
                        "对于并行或风险变更：创建任务、分配worktree lane、在这些lane中运行命令，然后在关闭时选择keep/remove。" +
                        "需要生命周期可见性时使用 worktree_events。")
                .workDir(Commons.CWD)
                .messages(new ArrayList<>())
                .build();

        Scanner scanner = new Scanner(System.in);
        try {
            System.out.println("S12: Worktree + Task Isolation");
            System.out.println("Git available: " + worktrees.isGitAvailable());
            System.out.println("Type 'q' to exit.");

            List<ChatCompletionMessageParam> messages = state.getMessages();
            while (true) {
                System.out.print("\nS12 >> ");

                String query = scanner.nextLine();
                if (List.of("q", "exit", "").contains(query.trim())) {
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
            scanner.close();
        }
    }
}