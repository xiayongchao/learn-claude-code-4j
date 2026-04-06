package org.jc.component.inbox;

import java.util.Map;

public class InBoxMessage {
    private String type;
    private String from;
    private String content;
    private Long timestamp;

    public InBoxMessage() {
    }

    public InBoxMessage(String type, String from, String content, Long timestamp, Map<String, Object> extra) {
        this.type = type;
        this.from = from;
        this.content = content;
        this.timestamp = timestamp;
        this.extra = extra;
    }

    // 扩展字段
    private Map<String, Object> extra;

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

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }
}
