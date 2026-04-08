package org.jc.agents;

import com.openai.models.chat.completions.*;
import org.jc.Commons;
import org.jc.Tools;
import org.jc.task.TaskManager;

import java.util.*;
import java.util.function.Function;

public class S07TaskSystem {
    private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();


    private static final TaskManager TASKS = new TaskManager(Commons.TASK_DIR);

    static {
        TOOL_HANDLERS.put("bash", Tools::runBash);
        TOOL_HANDLERS.put("readFile", Tools::runReadFile);
        TOOL_HANDLERS.put("writeFile", Tools::runWriteFile);
        TOOL_HANDLERS.put("editFile", Tools::runEditFile);
        TOOL_HANDLERS.put("taskCreate", TASKS::create);
        TOOL_HANDLERS.put("taskUpdate", TASKS::update);
        TOOL_HANDLERS.put("taskList", args -> TASKS.list());
        TOOL_HANDLERS.put("taskGet", TASKS::get);
    }

    private static final List<ChatCompletionTool> tools = List.of(
            Tools.bashTool()
            , Tools.readFileTool()
            , Tools.writeFileTool()
            , Tools.editFileTool()
            , Tools.taskCreateTool()
            , Tools.taskUpdateTool()
            , Tools.taskListTool()
            , Tools.taskGetTool()
    );

    /**
     * 测试输入：
     * <p>
     * 创建 3 个任务：“搭建项目”、“编写代码”、“编写测试”，并按顺序设置依赖关系。
     * 列出所有任务并展示依赖关系图。
     * 完成任务 1，然后列出任务，查看任务 2 是否解除阻塞。
     * 创建一个用于代码重构的任务看板：解析 → 转换 → 生成 → 测试，其中转换和生成在解析完成后可以并行执行。
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

                agentLoop(messages);

                ChatCompletionMessageParam last = messages.get(messages.size() - 1);
                System.out.println(">>" + Commons.getText(last));
            }
        } finally {
            // 用完关闭
            scanner.close();
        }
    }


    private static final String SYSTEM = "你是运行在 " + Commons.CWD + " 工作目录下的编码智能体，请使用任务工具规划并跟进各项工作";

    public static void agentLoop(List<ChatCompletionMessageParam> messages) {
        while (true) {
            List<ChatCompletionMessageParam> fullMessages = new ArrayList<>();
            fullMessages.add(ChatCompletionMessageParam.ofSystem(
                    ChatCompletionSystemMessageParam
                            .builder()
                            .content(SYSTEM)
                            .build()
            ));
            fullMessages.addAll(messages);


            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model("qwen3.5-plus")
                    .messages(fullMessages)
                    .tools(tools)
                    .build();

            ChatCompletion chatCompletion = Commons.getQwenClient().chat().completions().create(params);
            ChatCompletionMessage message = chatCompletion.choices().get(0).message();

            // 将模型的消息加入历史记录
            messages.add(ChatCompletionMessageParam.ofAssistant(message.toParam()));

            // 5. 检查是否有工具调用
            Optional<List<ChatCompletionMessageToolCall>> toolCallsOptional = message.toolCalls();
            if (toolCallsOptional.isEmpty()) {
                return;
            }

            List<ChatCompletionMessageToolCall> toolCalls = toolCallsOptional.get();
            // 遍历所有工具调用（模型可能一次调用多个工具）
            for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                ChatCompletionMessageParam toolMessage = Tools.exe(TOOL_HANDLERS, toolCall);
                if (toolMessage != null) {
                    // 将工具执行结果以 "tool" 角色发回给模型
                    messages.add(toolMessage);
                }
            }
        }
    }
}
