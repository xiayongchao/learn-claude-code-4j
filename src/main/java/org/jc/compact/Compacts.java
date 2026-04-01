package org.jc.compact;

import com.alibaba.fastjson.JSON;
import com.openai.models.chat.completions.*;
import org.jc.Commons;
import org.jc.Messages;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Compacts {
    private static final int KEEP_RECENT = 3;

    // -- Layer 1: micro_compact - replace old tool results with placeholders --
    public static void microCompact(List<ChatCompletionMessageParam> messages) {
        //Collect (msg_index, tool_call_id, tool_result_dict) for all tool_result entries
        List<ChatCompletionMessageParam> toolMessages = new ArrayList<>();
        for (ChatCompletionMessageParam message : messages) {
            if (message.isTool()) {
                toolMessages.add(message);
            }
        }
        if (toolMessages.size() <= KEEP_RECENT) {
            return;
        }

        //Find tool_name for each result by matching tool_call_id in prior assistant messages
        Map<String, String> toolNameMap = new HashMap<>();
        for (ChatCompletionMessageParam message : messages) {
            if (!message.isAssistant()) {
                continue;
            }
            Optional<ChatCompletionAssistantMessageParam> assistant = message.assistant();
            if (assistant.isEmpty()) {
                continue;
            }

            Optional<List<ChatCompletionMessageToolCall>> toolCalls = assistant.get().toolCalls();
            if (toolCalls.isEmpty()) {
                continue;
            }

            for (ChatCompletionMessageToolCall toolCall : toolCalls.get()) {
                if (!toolCall.isFunction()) {
                    continue;
                }
                ChatCompletionMessageFunctionToolCall functionCall = toolCall.asFunction();
                ChatCompletionMessageFunctionToolCall.Function function = functionCall.function();
                toolNameMap.put(functionCall.id(), function.name());
            }
        }
        //Clear old results(keep last KEEP_RECENT)
        Map<ChatCompletionMessageParam, ChatCompletionMessageParam> replaceMessageMap = new HashMap<>();
        List<ChatCompletionMessageParam> toClearMessages = Commons.getFirstN(toolMessages, KEEP_RECENT);
        for (ChatCompletionMessageParam message : toClearMessages) {
            if (!message.isTool()) {
                continue;
            }
            Optional<ChatCompletionToolMessageParam> tool = message.tool();
            if (tool.isEmpty()) {
                continue;
            }
            String text = Commons.getText(message);
            if (text != null && text.length() > 100) {
                ChatCompletionToolMessageParam toolMessage = tool.get();
                String toolCallId = toolMessage.toolCallId();
                String tooName = toolNameMap.getOrDefault(toolCallId, "未知工具");
                ChatCompletionToolMessageParam newToolMessage = toolMessage.toBuilder()
                        .toolCallId(toolCallId)
                        .content(String.format("[上一步: 已使用 %s]", tooName))
                        .build();
                replaceMessageMap.put(message, ChatCompletionMessageParam.ofTool(newToolMessage));
            }
        }

        List<ChatCompletionMessageParam> newMessages = new ArrayList<>();
        for (ChatCompletionMessageParam message : messages) {
            ChatCompletionMessageParam newToolMessage = replaceMessageMap.get(message);
            if (newToolMessage != null) {
                newMessages.add(newToolMessage);
            } else {
                newMessages.add(message);
            }
        }

        messages.clear();
        messages.addAll(newMessages);
    }


    // -- Layer 2: auto_compact - save transcript, summarize, replace messages --
    public static void autoCompact(List<ChatCompletionMessageParam> messages) {
        try {
            // Save full transcript to disk
            // 1. 把传入的字符串转 Path
            Path transcriptDirPath = Paths.get(Commons.TRANSCRIPT_DIR);
            Files.createDirectories(transcriptDirPath);

            // 2. 生成文件路径
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            Path transcriptPath = transcriptDirPath.resolve("transcript_" + timestamp + ".jsonl");

            // 3. 保存会话记录
            List<String> lines = new ArrayList<>();
            for (ChatCompletionMessageParam msg : messages) {
                lines.add(Messages.toStandardJson(msg));
            }
            Files.write(transcriptPath, lines);
            System.out.println("[会话记录已保存: " + transcriptPath + "]");

            // Ask LLM to summarize
            // 4. 截取前80000字符
            String conversationText = JSON.toJSONString(messages);
            if (conversationText.length() > 80000) {
                conversationText = conversationText.substring(0, 80000);
            }

            // 5. 构造总结 prompt
            String prompt = "对本次对话进行连贯总结，需包含："
                    + "1）已完成事项；2）当前所处状态；3）达成的关键决策。"
                    + "请保持总结简洁，保留关键细节。\n"
                    + conversationText;

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model("qwen3.5-plus")
                    .messages(List.of(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                            .content(prompt)
                            .build())))
                    .maxCompletionTokens(2000)
                    .build();

            ChatCompletion chatCompletion = Commons.getClient().chat().completions().create(params);
            ChatCompletionMessage message = chatCompletion.choices().get(0).message();
            String summary = Commons.getAssistantText(message.toParam());

            // 7. 返回压缩后的2条消息
            String userContent = "[对话已压缩。会话记录： " + transcriptPath + "]\n\n" + summary;
            String assistantContent = "我已获取总结中的上下文，继续执行";

            messages.clear();
            messages.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content(userContent).build()));
            messages.add(ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder().content(assistantContent).build()));
        } catch (Exception e) {
            System.err.println("autoCompact 异常: " + e.getMessage());
            messages.add(ChatCompletionMessageParam
                    .ofAssistant(ChatCompletionAssistantMessageParam.builder()
                            .content("autoCompact 异常: " + e.getMessage()).build()));
        }
    }
}
