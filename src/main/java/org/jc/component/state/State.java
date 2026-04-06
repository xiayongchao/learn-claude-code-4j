package org.jc.component.state;

import com.openai.models.chat.completions.ChatCompletionMessageParam;

import java.util.List;

public interface State {
    String getName();

    String getRole();

    String getModel();

    String getPrompt();

    String getWorkDir();

    List<ChatCompletionMessageParam> getMessages();
}
