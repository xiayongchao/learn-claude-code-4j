package org.jc.component.inbox;

import org.jc.component.state.States;
import org.jc.component.util.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class MessageBus {
    private Path getInboxPath(String to) {
        try {
            return FileUtils.resolve(Objects.requireNonNull(States.get().getWorkDir())
                    , String.format("inbox/%s.jsonl", to), true, true);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 发送消息
     *
     * @param to
     * @param message
     */
    public void send(String to, InBoxMessage message) throws IOException {
        Path inboxPath = this.getInboxPath(to);
        Objects.requireNonNull(inboxPath, "发送消息失败");
        FileUtils.write(inboxPath, message);
    }


    /**
     * 读取收件箱
     *
     * @param name
     * @param clear
     * @return
     */
    public List<InBoxMessage> readInbox(String name, boolean clear) throws IOException {
        final Path inboxPath = this.getInboxPath(name);
        Objects.requireNonNull(inboxPath, "读取收件箱失败");
        if (!FileUtils.exists(inboxPath)) {
            return new ArrayList<>();
        }

        List<InBoxMessage> inBoxMessages = FileUtils
                .readList(inboxPath, InBoxMessage.class);
        if (clear) {
            //读取收件箱之后需要清空
            FileUtils.clear(inboxPath);
        }
        return inBoxMessages;
    }
}
