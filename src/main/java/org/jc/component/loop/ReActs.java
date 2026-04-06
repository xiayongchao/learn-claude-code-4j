package org.jc.component.loop;

import com.openai.models.chat.completions.ChatCompletionMessageParam;
import org.jc.component.state.LeadState;
import org.jc.component.state.TeammateState;

public interface ReActs {
    ChatCompletionMessageParam start(LeadState state);

    void start(TeammateState state);
}
