package org.jc.message;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import java.util.*;

public class MessageBus {

    public static final Set<String> VALID_MSG_TYPES = new HashSet<>(
            Arrays.asList(
                    "message",
                    "broadcast",
                    "shutdown_request",
                    "shutdown_response",
                    "plan_approval_response")
    );

    private final Path dirPath;

    public MessageBus(String inboxDir) {
        this.dirPath = Paths.get(inboxDir);
        try {
            Files.createDirectories(this.dirPath);
        } catch (Exception e) {
            throw new RuntimeException("创建消息目录失败", e);
        }
    }

    // ===================== 发送消息（纯对象） =====================
    public String send(String sender, String to, String content,
                       String msgType, Map<String, Object> extra) {
        if (!VALID_MSG_TYPES.contains(msgType)) {
            return "错误: 无效的消息类型 '" + msgType + "'. Valid: " + VALID_MSG_TYPES;
        }

        // ✅ 使用对象，不再用 Map
        double timestamp = System.currentTimeMillis() / 1000.0;
        Message msg = new Message(msgType, sender, content, timestamp, extra);

        Path inboxPath = dirPath.resolve(to + ".jsonl");
        String line = JSON.toJSONString(msg) + "\n";

        try {
            Files.write(inboxPath, line.getBytes(),
                    Files.exists(inboxPath)
                            ? java.nio.file.StandardOpenOption.APPEND
                            : java.nio.file.StandardOpenOption.CREATE);
        } catch (Exception e) {
            return "发送消息失败: " + e.getMessage();
        }

        return "发送 " + msgType + " 给 " + to;
    }


    // ===================== 读取收件箱（返回对象列表） =====================
    public List<Message> readInbox(String arguments, boolean json) {
        String name = arguments;
        if (json) {
            JSONObject object = JSON.parseObject(arguments);
            name = object.getString("name");
        }
        Path inboxPath = dirPath.resolve(name + ".jsonl");
        if (!Files.exists(inboxPath)) {
            return new ArrayList<>();
        }

        List<Message> messages = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(inboxPath);
            for (String line : lines) {
                if (line.isBlank()) continue;
                // ✅ 直接转成 Message 对象
                messages.add(JSON.parseObject(line, Message.class));
            }
            Files.write(inboxPath, new byte[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return messages;
    }
}