package org.jc.agents;

import com.openai.models.chat.completions.*;
import org.jc.Commons;
import org.jc.Tokens;
import org.jc.Tools;
import org.jc.compact.Compacts;

import java.util.*;
import java.util.function.Function;

public class S06ContextCompact {
    private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();

    static {
        TOOL_HANDLERS.put("bash", Tools::runBash);
        TOOL_HANDLERS.put("readFile", Tools::runReadFile);
        TOOL_HANDLERS.put("writeFile", Tools::runWriteFile);
        TOOL_HANDLERS.put("editFile", Tools::runEditFile);
        TOOL_HANDLERS.put("compact", args -> "已请求手动压缩");
    }

    private static final List<ChatCompletionTool> tools = List.of(
            Tools.bashTool()
            , Tools.readFileTool()
            , Tools.writeFileTool()
            , Tools.editFileTool()
            , Tools.compactTool()
    );

    /**
     * 测试输入：
     * <p>
     * 逐一读取 `agents/` 目录下的每个 java 文件
     * 持续读取文件，直到压缩功能自动触发
     * 使用 compact 工具手动压缩对话
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


    private static final String SYSTEM = "你是运行在 " + Commons.CWD + " 工作目录下的编程智能体，请使用工具完成各项任务";
    private static final int THRESHOLD = 3000;


    public static void agentLoop(List<ChatCompletionMessageParam> messages) {
        while (true) {
            // Layer 1: micro_compact before each LLM call
            Compacts.microCompact(messages);
            // Layer 2: auto_compact if token estimate exceeds threshold
            if (Tokens.countDialogTokens(messages) > THRESHOLD) {
                System.out.println("[已触发自动压缩]");
                Compacts.autoCompact(messages);
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

            ChatCompletion chatCompletion = Commons.getClient().chat().completions().create(params);
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
            boolean manualCompact = false;
            for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                if (Tools.isCompactTool(toolCall)) {
                    manualCompact = true;
                    continue;
                }
                ChatCompletionMessageParam toolMessage = Tools.exe(TOOL_HANDLERS, toolCall);
                if (toolMessage != null) {
                    // 将工具执行结果以 "tool" 角色发回给模型
                    messages.add(toolMessage);
                }
            }
            // Layer 3: manual compact triggered by the compact tool
            if (manualCompact) {
                System.out.println("[手动压缩]");
                Compacts.autoCompact(messages);
            }
        }
    }

}
