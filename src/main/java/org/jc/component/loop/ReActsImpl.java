package org.jc.component.loop;

import com.google.inject.Inject;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import org.jc.component.state.LeadState;
import org.jc.component.state.States;
import org.jc.component.state.TeammateState;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ReActsImpl implements ReActs {
    private final ThreadPoolExecutor theadPools = new ThreadPoolExecutor(0, 5
            , 300, TimeUnit.SECONDS
            , new ArrayBlockingQueue<>(50)
            , AiThreadFactory.create("loop", true));

    private final LeadReAct leadReAct;
    private final TeammateReAct teammateReAct;

    @Inject
    public ReActsImpl(LeadReAct leadReAct, TeammateReAct teammateReAct) {
        this.leadReAct = Objects.requireNonNull(leadReAct);
        this.teammateReAct = Objects.requireNonNull(teammateReAct);
    }

    public ChatCompletionMessageParam start(LeadState state) {
        try {
            // 1. 设置值
            States.set(state);

            // 2. 执行业务
            leadReAct.loop();

            //返回最后一条消息
            return States.lead().getLastMessage();
        } finally {
            // 3. 无论如何都要清空！！！
            States.clear();
        }
    }

    public void start(TeammateState state) {
        theadPools.submit(() -> {
            try {
                // 1. 设置值
                States.set(state);

                // 2. 执行业务
                teammateReAct.loop();
            } finally {
                // 3. 无论如何都要清空！！！
                States.clear();
            }
        });
    }
}
