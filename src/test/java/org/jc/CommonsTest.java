package org.jc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Commons 工具类单元测试
 */
public class CommonsTest {

    @Test
    public void testCWDNotNull() {
        // 验证 CWD 不为空
        assertNotNull(Commons.CWD);
        assertFalse(Commons.CWD.isEmpty());
    }

    @Test
    public void testSKILLS_DIR() {
        // 验证 SKILLS_DIR 基于 CWD
        assertNotNull(Commons.SKILLS_DIR);
        assertTrue(Commons.SKILLS_DIR.contains(Commons.CWD));
        assertTrue(Commons.SKILLS_DIR.endsWith("/src/main/resources/skills"));
    }

    @Test
    public void testIsSafePathWithSafePath() {
        String workDir = "/home/user/project";
        String path = "src/main/java";

        assertTrue(Commons.isSafePath(workDir, path));
    }

    @Test
    public void testIsSafePathWithRelativePath() {
        String workDir = "/home/user/project";
        String path = "./src/main/java";

        assertTrue(Commons.isSafePath(workDir, path));
    }

    @Test
    public void testIsSafePathWithParentTraversal() {
        String workDir = "/home/user/project";
        String path = "../secret/file.txt";

        assertFalse(Commons.isSafePath(workDir, path));
    }

    @Test
    public void testIsSafePathWithDeepTraversal() {
        String workDir = "/home/user/project";
        String path = "src/../../etc/passwd";

        assertFalse(Commons.isSafePath(workDir, path));
    }

    @Test
    public void testIsSafePathWithAbsolutePath() {
        String workDir = "/home/user/project";
        String path = "/etc/passwd";

        // 绝对路径应该被拒绝
        assertFalse(Commons.isSafePath(workDir, path));
    }

    @Test
    public void testIsSafePathWithSubdirectory() {
        String workDir = "/home/user/project";
        String path = "src/main/java/org/jc";

        assertTrue(Commons.isSafePath(workDir, path));
    }

    @Test
    public void testGetLastNWithNullList() {
        List<String> result = Commons.getLastN(null, 3);
        assertNull(result);
    }

    @Test
    public void testGetLastNWithEmptyList() {
        List<String> result = Commons.getLastN(Collections.emptyList(), 3);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetLastNWithZeroN() {
        List<String> list = Arrays.asList("a", "b", "c");
        List<String> result = Commons.getLastN(list, 0);
        assertEquals(list, result);
    }

    @Test
    public void testGetLastNWithNegativeN() {
        List<String> list = Arrays.asList("a", "b", "c");
        List<String> result = Commons.getLastN(list, -1);
        assertEquals(list, result);
    }

    @Test
    public void testGetLastNWithNLessThanSize() {
        List<String> list = Arrays.asList("a", "b", "c", "d", "e");
        List<String> result = Commons.getLastN(list, 3);
        assertEquals(3, result.size());
        assertEquals(Arrays.asList("c", "d", "e"), result);
    }

    @Test
    public void testGetLastNWithNGreaterThanSize() {
        List<String> list = Arrays.asList("a", "b", "c");
        List<String> result = Commons.getLastN(list, 5);
        assertEquals(list, result);
    }

    @Test
    public void testGetLastNWithNEqualsSize() {
        List<String> list = Arrays.asList("a", "b", "c");
        List<String> result = Commons.getLastN(list, 3);
        assertEquals(list, result);
    }

    @Test
    public void testGetFirstNWithNullList() {
        List<String> result = Commons.getFirstN(null, 3);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetFirstNWithEmptyList() {
        List<String> result = Commons.getFirstN(Collections.emptyList(), 3);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetFirstNWithZeroN() {
        List<String> list = Arrays.asList("a", "b", "c");
        List<String> result = Commons.getFirstN(list, 0);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetFirstNWithNegativeN() {
        List<String> list = Arrays.asList("a", "b", "c");
        List<String> result = Commons.getFirstN(list, -1);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetFirstNWithNLessThanSize() {
        List<String> list = Arrays.asList("a", "b", "c", "d", "e");
        // 保留前 (5-2)=3 个元素，删除最后 2 个
        List<String> result = Commons.getFirstN(list, 2);
        assertEquals(3, result.size());
        assertEquals(Arrays.asList("a", "b", "c"), result);
    }

    @Test
    public void testGetFirstNWithNGreaterThanSize() {
        List<String> list = Arrays.asList("a", "b", "c");
        List<String> result = Commons.getFirstN(list, 5);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetFirstNWithNEqualsSize() {
        List<String> list = Arrays.asList("a", "b", "c");
        List<String> result = Commons.getFirstN(list, 3);
        assertTrue(result.isEmpty());
    }
}
