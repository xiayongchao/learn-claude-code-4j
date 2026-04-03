package org.jc.agents;

import com.openai.models.chat.completions.*;
import org.jc.Commons;
import org.jc.Tools;
import org.jc.skill.SkillLoader;

import java.util.*;
import java.util.function.Function;

public class S05SkillLoading {
    private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();

    private static final SkillLoader SKILL_LOADER = new SkillLoader(Commons.SKILLS_DIR);

    static {
        TOOL_HANDLERS.put("bash", Tools::runBash);
        TOOL_HANDLERS.put("readFile", Tools::runReadFile);
        TOOL_HANDLERS.put("writeFile", Tools::runWriteFile);
        TOOL_HANDLERS.put("editFile", Tools::runEditFile);
        TOOL_HANDLERS.put("loadSkill", SKILL_LOADER::getContent);
    }

    private static final List<ChatCompletionTool> tools = List.of(
            Tools.bashTool()
            , Tools.readFileTool()
            , Tools.writeFileTool()
            , Tools.editFileTool()
            , Tools.loadSkillTool()
    );


    /**
     * 测试输入：
     * <p>
     * 有哪些可用技能？
     * 读取 xxx excel文件内容
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


    private static final String SYSTEM = "你是工作目录 " + Commons.CWD
            + " 下的编程智能体，遇到陌生业务场景前，请先调用 `load_skill` 加载专属专业知识。可用技能列表："
            + SKILL_LOADER.getDescriptions()
            + "\n\n"
            + "如果需要进一步加载资源或脚本，可以在 " + Commons.SKILLS_DIR + " 目录下进行搜索，" +
            "禁止自己创建资源或脚本文件";


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
