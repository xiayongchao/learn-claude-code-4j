package org.jc.agents;

import com.openai.models.chat.completions.*;
import org.jc.Commons;
import org.jc.Tools;
import org.jc.background.BackgroundManager;

import java.util.*;
import java.util.function.Function;

public class S08BackgroundTasks {
    private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();
    private static final BackgroundManager BG = new BackgroundManager();

    static {
        TOOL_HANDLERS.put("bash", Tools::runBash);
        TOOL_HANDLERS.put("readFile", Tools::runReadFile);
        TOOL_HANDLERS.put("writeFile", Tools::runWriteFile);
        TOOL_HANDLERS.put("editFile", Tools::runEditFile);
        TOOL_HANDLERS.put("backgroundRun", BG::run);
        TOOL_HANDLERS.put("checkBackground", BG::check);
    }

    private static final List<ChatCompletionTool> tools = List.of(
            Tools.bashTool()
            , Tools.readFileTool()
            , Tools.writeFileTool()
            , Tools.editFileTool()
            , Tools.backgroundRunTool()
            , Tools.checkBackgroundTool()
    );

    /**
     * 测试输入：
     * <p>
     * 在后台运行 sleep 5 && echo done，并在其执行期间创建一个文件
     * 启动 3 个后台任务：sleep 2s、sleep 4s、sleep 6s，并查看它们的状态
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


    private static final String SYSTEM = "你是运行在 " + Commons.CWD + " 工作目录下的编码智能体，长时间运行的命令请使用 backgroundRun 执行";

    public static void agentLoop(List<ChatCompletionMessageParam> messages) {
        while (true) {
            // Drain background notifications and inject as system message before LLM call
            String notifs = BG.drainNotifications();
            if (notifs != null && !notifs.isBlank()) {
                messages.add(
                        ChatCompletionMessageParam.ofUser(
                                ChatCompletionUserMessageParam
                                        .builder()
                                        .content(String.format("<background-results>\n%s\n</background-results>", notifs))
                                        .build()
                        ));
                messages.add(ChatCompletionMessageParam.ofAssistant(
                        ChatCompletionAssistantMessageParam
                                .builder()
                                .content("已记录后台执行结果")
                                .build()
                ));
            }


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
