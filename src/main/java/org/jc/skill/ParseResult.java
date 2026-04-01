package org.jc.skill;

import java.util.Map;

// 解析结果专用对象（替代数组返回）
public class ParseResult {
    private final Map<String, String> meta;
    private final String body;

    public ParseResult(Map<String, String> meta, String body) {
        this.meta = meta;
        this.body = body;
    }

    public Map<String, String> getMeta() {
        return meta;
    }

    public String getBody() {
        return body;
    }
}

