package org.jc.message;

import java.util.Map;

/**
 * 消息实体（替代 Map<String, Object>）
 */
public class Message {
    private String type;
    private String from;
    private String content;
    private double timestamp;

    // 扩展字段
    private Map<String, Object> extra;

    // 全参构造
    public Message(String type, String from, String content, double timestamp, Map<String, Object> extra) {
        this.type = type;
        this.from = from;
        this.content = content;
        this.timestamp = timestamp;
        this.extra = extra;
    }

    // getter + setter
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }
}
