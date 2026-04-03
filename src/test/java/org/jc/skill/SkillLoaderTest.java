package org.jc.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * SkillLoader 单元测试
 */
public class SkillLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    public void testLoadSkillWithValidFrontMatter() throws IOException {
        // 创建技能目录
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectories(skillDir);

        // 创建 SKILL.md 文件
        String skillContent = """
                ---
                name: test-skill
                description: A test skill for unit testing
                tags: test,unit
                ---
                
                # Test Skill Body
                
                This is the body of the test skill.
                It can contain multiple lines.
                """;

        Path skillFile = skillDir.resolve("SKILL.md");
        Files.writeString(skillFile, skillContent);

        // 创建 SkillLoader
        SkillLoader loader = new SkillLoader(tempDir.toString());

        // 验证技能被加载
        Set<String> skillNames = loader.getSkillNames();
        assertTrue(skillNames.contains("test-skill"));
        assertEquals(1, skillNames.size());

        // 验证描述输出
        String descriptions = loader.getDescriptions();
        assertTrue(descriptions.contains("test-skill"));
        assertTrue(descriptions.contains("A test skill for unit testing"));
        assertTrue(descriptions.contains("[test,unit]"));
    }

    @Test
    public void testLoadSkillWithoutFrontMatter() throws IOException {
        // 创建技能目录
        Path skillDir = tempDir.resolve("no-frontmatter");
        Files.createDirectories(skillDir);

        // 创建没有 front matter 的 SKILL.md 文件
        String skillContent = """
                # Just a body without front matter
                
                This skill has no YAML front matter.
                """;

        Path skillFile = skillDir.resolve("SKILL.md");
        Files.writeString(skillFile, skillContent);

        // 创建 SkillLoader
        SkillLoader loader = new SkillLoader(tempDir.toString());

        // 验证技能被加载，使用目录名作为默认名称
        Set<String> skillNames = loader.getSkillNames();
        assertTrue(skillNames.contains("no-frontmatter"));
    }

    @Test
    public void testLoadSkillWithPartialFrontMatter() throws IOException {
        // 创建技能目录
        Path skillDir = tempDir.resolve("partial");
        Files.createDirectories(skillDir);

        // 创建只有 name 的 front matter
        String skillContent = """
                ---
                name: custom-name
                ---
                
                Body content here.
                """;

        Path skillFile = skillDir.resolve("SKILL.md");
        Files.writeString(skillFile, skillContent);

        SkillLoader loader = new SkillLoader(tempDir.toString());

        // 验证技能被加载
        Set<String> skillNames = loader.getSkillNames();
        assertTrue(skillNames.contains("custom-name"));

        // 验证 getContent 方法
        String content = loader.getContent("{\"name\": \"custom-name\"}");
        assertTrue(content.contains("<skill name=\"custom-name\">"));
        assertTrue(content.contains("Body content here."));
        assertTrue(content.contains("</skill>"));
    }

    @Test
    public void testGetContentWithUnknownSkill() throws IOException {
        SkillLoader loader = new SkillLoader(tempDir.toString());

        // 请求不存在的技能
        String content = loader.getContent("{\"name\": \"non-existent\"}");
        assertTrue(content.contains("错误：未知技能"));
        assertTrue(content.contains("non-existent"));
    }

    @Test
    public void testEmptySkillsDirectory() {
        // 在空目录上创建 SkillLoader
        SkillLoader loader = new SkillLoader(tempDir.toString());

        // 验证没有技能
        assertTrue(loader.getSkillNames().isEmpty());
        assertEquals("(无可用技能)", loader.getDescriptions());
    }

    @Test
    public void testNonExistentSkillsDirectory() {
        // 使用不存在的目录
        SkillLoader loader = new SkillLoader("/non/existent/path");

        // 验证没有技能（应该优雅处理）
        assertTrue(loader.getSkillNames().isEmpty());
        assertEquals("(无可用技能)", loader.getDescriptions());
    }

    @Test
    public void testMultipleSkills() throws IOException {
        // 创建多个技能目录
        createSkill(tempDir, "skill1", "First skill", "tag1");
        createSkill(tempDir, "skill2", "Second skill", "tag2");
        createSkill(tempDir, "skill3", "Third skill", "");

        SkillLoader loader = new SkillLoader(tempDir.toString());

        // 验证所有技能都被加载
        Set<String> skillNames = loader.getSkillNames();
        assertEquals(3, skillNames.size());
        assertTrue(skillNames.contains("skill1"));
        assertTrue(skillNames.contains("skill2"));
        assertTrue(skillNames.contains("skill3"));
    }

    @Test
    public void testGetDescriptionsFormat() throws IOException {
        createSkill(tempDir, "test-skill", "Test description", "test");

        SkillLoader loader = new SkillLoader(tempDir.toString());
        String descriptions = loader.getDescriptions();

        // 验证格式
        assertTrue(descriptions.contains("  - test-skill: Test description [test]"));
    }

    private void createSkill(Path baseDir, String name, String description, String tags) throws IOException {
        Path skillDir = baseDir.resolve(name);
        Files.createDirectories(skillDir);

        StringBuilder content = new StringBuilder();
        content.append("---\n");
        content.append("name: ").append(name).append("\n");
        content.append("description: ").append(description).append("\n");
        if (!tags.isEmpty()) {
            content.append("tags: ").append(tags).append("\n");
        }
        content.append("---\n\n");
        content.append("# ").append(name).append("\n\n");
        content.append("Body content for ").append(name);

        Files.writeString(skillDir.resolve("SKILL.md"), content.toString());
    }
}
