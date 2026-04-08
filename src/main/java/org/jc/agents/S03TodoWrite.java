package org.jc.agents;

import com.openai.models.chat.completions.*;
import org.jc.Commons;
import org.jc.Tools;
import org.jc.todo.TodoManager;

import java.util.*;
import java.util.function.Function;

public class S03TodoWrite {
    private static final TodoManager TODO = new TodoManager();

    private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();

    static {
        TOOL_HANDLERS.put("bash", Tools::runBash);
        TOOL_HANDLERS.put("readFile", Tools::runReadFile);
        TOOL_HANDLERS.put("writeFile", Tools::runWriteFile);
        TOOL_HANDLERS.put("editFile", Tools::runEditFile);
        TOOL_HANDLERS.put("todo", TODO::update);
    }

    private static final List<ChatCompletionTool> tools = List.of(
            Tools.bashTool()
            , Tools.readFileTool()
            , Tools.writeFileTool()
            , Tools.editFileTool()
            , Tools.todoTool()
    );


    /**
     * 测试输入：
     * <p>
     * 重构 `hello.java` 文件：添加类型注解、文档字符串，并补充程序入口守卫判断
     * 创建一个 java 包，包含 `utils.java` 以及测试文件 `tests/test_utils.java`
     * 检查所有 java 代码文件，并修复所有代码风格问题
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


    private static final String SYSTEM = "你是工作目录 " + Commons.CWD + " 下的编程智能体，使用待办工具规划多步骤任务。开始前标记为进行中，完成后标记为已完成，优先使用工具操作，而非文字说明";

    public static void agentLoop(List<ChatCompletionMessageParam> messages) {
        int roundsSinceTodo = 0;
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

            boolean usedTodo = false;
            List<ChatCompletionMessageToolCall> toolCalls = toolCallsOptional.get();
            // 遍历所有工具调用（模型可能一次调用多个工具）
            for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                ChatCompletionMessageParam toolMessage = Tools.exe(TOOL_HANDLERS, toolCall);
                if (toolMessage != null) {
                    // 将工具执行结果以 "tool" 角色发回给模型
                    messages.add(toolMessage);
                }
                if (Tools.isTodoTool(toolCall)) {
                    usedTodo = true;
                }
            }
            if (usedTodo) {
                roundsSinceTodo = 0;
            } else {
                roundsSinceTodo++;
            }
            if (roundsSinceTodo >= 3) {
                messages.add(ChatCompletionMessageParam
                        .ofUser(ChatCompletionUserMessageParam.builder()
                                .content("<reminder>更新你的待办事项</reminder>")
                                .build()));
            }
        }
    }
}
