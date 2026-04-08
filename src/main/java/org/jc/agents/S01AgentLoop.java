package org.jc.agents;

import com.openai.models.chat.completions.*;
import org.jc.Commons;
import org.jc.Tools;

import java.util.*;
import java.util.function.Function;

public class S01AgentLoop {
    private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();

    static {
        TOOL_HANDLERS.put("bash", Tools::runBash);
    }

    private static final List<ChatCompletionTool> tools = List.of(Tools.bashTool());

    /**
     * 测试输入：
     * <p>
     * 创建名为 hello.java 的文件，使其输出打印 "Hello, World!"
     * 列出当前目录下所有 java 文件
     * 当前 Git 分支是什么？
     * 创建名为 test_output 的文件夹，并在其中新建 3 个文件
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


    private static final String SYSTEM = "你当前工作目录为 " + Commons.CWD + "，作为编程智能体，使用 Bash 完成任务，直接执行、无需解释";

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
                    .model("Pro/moonshotai/Kimi-K2.5")
                    .messages(fullMessages)
                    .tools(tools)
                    .build();

            ChatCompletion chatCompletion = Commons.getKimiClient().chat().completions().create(params);
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