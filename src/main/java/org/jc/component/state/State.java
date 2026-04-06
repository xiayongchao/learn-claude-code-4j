package org.jc.component.state;

import com.openai.models.chat.completions.ChatCompletionMessageParam;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public interface State {
    String getName();

    String getRole();

    String getModel();

    String getPrompt();

    String getWorkDir();

    List<ChatCompletionMessageParam> getMessages();

    ReentrantLock getShutdownLock();

    ReentrantLock getPlanLock();
}
