package org.jc;

import com.openai.models.chat.completions.ChatCompletionMessageParam;

import java.util.List;

public class Tokens {

    /**
     * 通用中英文 token 估算（适配千问等国产模型）
     * 是估算，不是 100% 精准
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        double tokenCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // 中文汉字、中文标点
            if (isChinese(c)) {
                tokenCount++;
            } else {
                // 英文、数字、符号：每 4 个字符 ≈ 1 token
                tokenCount += 0.25;
            }
        }

        // 向上取整
        return (int) Math.ceil(tokenCount);
    }

    /**
     * 判断是否为中文字符
     */
    private static boolean isChinese(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }


    public static int countMessageTokens(ChatCompletionMessageParam message) {
        if (message == null) {
            return 0;
        }
        return estimateTokens(Commons.getText(message));
    }

    public static int countDialogTokens(List<ChatCompletionMessageParam> messages) {
        if (messages == null || messages.isEmpty()) return 0;

        int total = 0;
        for (ChatCompletionMessageParam msg : messages) {
            total += countMessageTokens(msg);
        }

        // 对话格式额外开销
        return total + 5;
    }
}
