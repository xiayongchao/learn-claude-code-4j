package org.jc;


import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Commons {
    public static final String CWD = Paths.get("").toAbsolutePath().toString();
    public static final String SKILLS_DIR = Commons.CWD + "/src/main/resources/skills";

    public static final String TRANSCRIPT_DIR = Commons.CWD + "/src/main/resources/transcripts";
    public static final String TASK_DIR = Commons.CWD + "/src/main/resources/tasks";

    private static final OpenAIClient qwenAIClient = OpenAIOkHttpClient.builder()
            .apiKey(System.getenv("DASHSCOPE_API_KEY"))
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
            .build();

    private static final OpenAIClient kimiAIClient = OpenAIOkHttpClient.builder()
            .apiKey(System.getenv("SILICONFLOW_API_KEY"))
            .baseUrl("https://api.siliconflow.cn/v1")
            .build();

    public static OpenAIClient getQwenClient() {
        return qwenAIClient;
    }

    public static OpenAIClient getKimiClient() {
        return kimiAIClient;
    }

    public static String getText(ChatCompletionMessageParam param) {
        if (param == null) {
            return null;
        }
        if (param.isAssistant()) {
            return getAssistantText(param.assistant());
        }
        if (param.isUser()) {
            return getUserText(param.user());
        }
        if (param.isSystem()) {
            return getSystemText(param.system());
        }
        if (param.isTool()) {
            return getToolText(param.tool());
        }
        if (param.isDeveloper()) {
            return getDeveloperText(param.developer());
        }
        if (param.isFunction()) {
            return getFunctionText(param.function());
        }
        if (param.isValid()) {
            ChatCompletionMessageParam validate = param.validate();
            return getText(validate);
        }

        return null;
    }

    public static String getAssistantText(Optional<ChatCompletionAssistantMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        return getAssistantText(optional.get());
    }

    public static String getAssistantText(ChatCompletionAssistantMessageParam param) {
        if (param == null) {
            return null;
        }

        Optional<ChatCompletionAssistantMessageParam.Content> content = param.content();
        return content.map(ChatCompletionAssistantMessageParam.Content::asText).orElse(null);
    }

    public static String getUserText(Optional<ChatCompletionUserMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        ChatCompletionUserMessageParam.Content content = optional.get().content();
        return content.asText();
    }

    public static String getSystemText(Optional<ChatCompletionSystemMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        ChatCompletionSystemMessageParam.Content content = optional.get().content();
        return content.asText();
    }

    public static String getToolText(Optional<ChatCompletionToolMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        ChatCompletionToolMessageParam.Content content = optional.get().content();
        return content.asText();
    }

    public static String getDeveloperText(Optional<ChatCompletionDeveloperMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        ChatCompletionDeveloperMessageParam.Content content = optional.get().content();
        return content.asText();
    }

    public static String getFunctionText(Optional<ChatCompletionFunctionMessageParam> optional) {
        if (optional.isEmpty()) {
            return null;
        }

        Optional<String> content = optional.get().content();
        return content.orElse(null);
    }

    /**
     * 安全路径校验（防止路径穿越）
     *
     * @param workDirPath 工作目录（字符串路径，动态传入）
     * @param path        子路径
     * @return 是否安全的
     */
    public static boolean isSafePath(String workDirPath, String path) {
        Path workDir = Paths.get(workDirPath).normalize();
        Path targetPath = workDir.resolve(path).normalize();

        if (!targetPath.startsWith(workDir)) {
            return false;
        }
        return true;
    }

    public static <T> List<T> getLastN(List<T> list, int N) {
        if (list == null || list.isEmpty() || N <= 0) {
            return list;
        }
        // 数据量不足 N，直接返回全部
        if (list.size() <= N) {
            return list;
        }
        // 保留最后 N 条
        return list.subList(list.size() - N, list.size());
    }

    public static <T> List<T> getFirstN(List<T> list, int N) {
        if (list == null || list.isEmpty() || N <= 0) {
            return List.of();
        }
        if (list.size() <= N) {
            return List.of(); // 没有要删的
        }
        // 取 0 ~ (总数 - N) 的数据
        return list.subList(0, list.size() - N);
    }
}
