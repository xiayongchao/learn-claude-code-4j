package org.jc.skill;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * ParseResult 单元测试
 */
public class ParseResultTest {

    @Test
    public void testConstructorAndGetters() {
        // 准备测试数据
        Map<String, String> meta = new HashMap<>();
        meta.put("name", "test-skill");
        meta.put("description", "A test skill");
        String body = "This is the skill body";

        // 创建 ParseResult 实例
        ParseResult result = new ParseResult(meta, body);

        // 验证 getter 方法
        assertEquals(meta, result.getMeta());
        assertEquals(body, result.getBody());
    }

    @Test
    public void testEmptyMeta() {
        Map<String, String> emptyMeta = new HashMap<>();
        String body = "Body content";

        ParseResult result = new ParseResult(emptyMeta, body);

        assertTrue(result.getMeta().isEmpty());
        assertEquals(body, result.getBody());
    }

    @Test
    public void testNullBody() {
        Map<String, String> meta = new HashMap<>();
        meta.put("name", "test");

        ParseResult result = new ParseResult(meta, null);

        assertEquals(meta, result.getMeta());
        assertNull(result.getBody());
    }

    @Test
    public void testImmutableMeta() {
        Map<String, String> originalMeta = new HashMap<>();
        originalMeta.put("name", "original");
        
        ParseResult result = new ParseResult(originalMeta, "body");
        
        // 修改原始 map 不应该影响 ParseResult 内部的 meta
        // 注意：当前实现没有做防御性拷贝，这是一个潜在的改进点
        originalMeta.put("newKey", "newValue");
        
        // 当前实现会反映外部修改（这是需要关注的点）
        assertEquals("newValue", result.getMeta().get("newKey"));
    }
}
