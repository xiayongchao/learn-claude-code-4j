package org.jc.skill;

import com.alibaba.fastjson.JSON;
import org.jc.Commons;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SkillLoader {
    private final String skillsDir;
    private final Map<String, Skill> skills = new HashMap<>();


    public SkillLoader(String skillsDir) {
        this.skillsDir = skillsDir;
        loadAll();
    }

    private void loadAll() {
        var dirPath = Paths.get(skillsDir);
        if (!Files.exists(dirPath)) {
            return;
        }

        try {
            List<java.nio.file.Path> files = Files.walk(dirPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> "SKILL.md".equals(p.getFileName().toString()))
                    .sorted()
                    .toList();

            for (var file : files) {
                String text = Files.readString(file);
                ParseResult result = parseFrontMatter(text);

                String name = result.getMeta().getOrDefault("name",
                        file.getParent().getFileName().toString());

                Skill skill = new Skill();
                skill.meta = result.getMeta();
                skill.body = result.getBody();
                skill.path = file.toString();

                skills.put(name, skill);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 返回专用对象，优雅、类型安全
    private ParseResult parseFrontMatter(String text) {
        Pattern pattern = Pattern.compile("^---\\n(.*?)\\n---\\n(.*)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        Map<String, String> meta = new HashMap<>();
        String body = text;

        if (matcher.find()) {
            String metaStr = matcher.group(1).trim();
            body = matcher.group(2).trim();

            // ✅ 真正解析 YAML（支持 name、description | 多行）
            Yaml yaml = new Yaml();
            meta = yaml.load(metaStr);
        }

        return new ParseResult(meta, body);
    }

    public String getDescriptions() {
        if (skills.isEmpty()) {
            return "(无可用技能)";
        }

        List<String> lines = new ArrayList<>();
        for (var entry : skills.entrySet()) {
            String name = entry.getKey();
            Skill skill = entry.getValue();
            String desc = skill.meta.getOrDefault("description", "无描述");
            String tags = skill.meta.getOrDefault("tags", "");

            String line = "  - " + name + ": " + desc;
            if (!tags.isBlank()) {
                line += " [" + tags + "]";
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    public String getContent(String args) {
        String name = JSON.parseObject(args).getString("name");
        if (!skills.containsKey(name)) {
            return "错误：未知技能 '" + name + "'。 可用的技能: " + String.join(", ", skills.keySet());
        }
        Skill skill = skills.get(name);
        return "<skill name=\"" + name + "\">\n" + skill.body + "\n</skill>";
    }

    public Set<String> getSkillNames() {
        return skills.keySet();
    }

    public static void main(String[] args) {
        SkillLoader loader = new SkillLoader(Commons.SKILLS_DIR);
        System.out.println(loader.getDescriptions());
    }
}