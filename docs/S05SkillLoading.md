# S05SkillLoading - 技能加载：用时再加载，不用不加载

## 核心理念

**"技能是模型 KNOWS 的东西，工具是模型 CAN DO 的东西" -- 按需注入知识，不预设工作流。**

- 源码：https://github.com/xiayongchao/learn-claude-code-4j/blob/main/src/main/java/org/jc/agents/S05SkillLoading.java
- 原版：https://github.com/shareAI-lab/learn-claude-code
- 上篇：[S04Subagent - 子 Agent](./S04Subagent.md)

## 上篇回顾

上篇文章我们实现了 Subagent 机制，通过独立的 messages[] 隔离子任务的上下文，不污染父 Agent 的对话历史。

## 问题

知识注入有两种方式：
1. **预设到 System Prompt** - 全部加载，上下文膨胀
2. **按需注入到 tool_result** - 用时加载，不用不加载

哪种更好？显然是第二种。但怎么实现"按需"？

## 解决方案

```
+------------------+      +------------------+
|  SkillLoader     |      |  SKILLS_DIR     |
|                  | ---> |  /skills        |
|  getContent(name)|      |    /agent-builder|
|  getDescriptions()|     |      /SKILL.md   |
+------------------+      |    /mcp-builder  |
         |                |      /SKILL.md   |
         v                +------------------+
+--------+--------+
| load_skill tool  |
| name: "agent-builder"  |
+------------------+
         |
         v
<skill name="agent-builder">
  # Agent Builder
  Build AI agents for any domain...
</skill>
```

**核心思想：通过 `load_skill` 工具按需加载专业知识，返回 `<skill>` 标签包裹的内容。**

## Java 实现详解

### 1. 技能文件结构

```
src/main/resources/skills/
└── excel/
    ├── SKILL.md
    └── scripts/        # Java 脚本目录（JBang 调用）
        └── ExcelTool.java
```

### 2. SKILL.md 格式（YAML Front Matter）

```markdown
---
name: excel
description: |
    基于Java+JBang实现的Excel自动化操作技能，无需手动安装依赖、无需配置环境、无需打包。
    支持自动检测并安装JBang运行环境，支持读取Excel、导出Excel、指定Sheet读取、自定义输入输出路径。
    工具类统一放在 scripts/ 目录下，与SKILL.md同级。
    适用于脚本调用、自动化任务、批处理、外部系统调用、定时调度等场景。
Keywords: excel, jbang, 自动安装, 动态依赖, 命令行, 导入, 导出, 脚本
---

# Excel 操作技能

基于 Java + JBang 实现，真正做到"零配置"运行。

## JBang 机制

> **JBang**：Java 脚本运行器，自动下载 JDK + 依赖

特点：
- 无需手动安装 JDK
- 自动下载依赖（pom.xml 声明的库）
- 一行命令执行：`jbang run ExcelTool.java --input data.xlsx --output result.xlsx`

## 使用方式

```bash
# 自动检测并安装 JBang，执行 Excel 操作
jbang scripts/ExcelTool.java --input path/to/input.xlsx --output path/to/output.xlsx
```

## 核心功能

- 读取 Excel（支持指定 Sheet）
- 导出 Excel
- 自定义输入输出路径
...
```

### 3. SkillLoader：技能加载器

```java
public class SkillLoader {
    private final String skillsDir;
    private final Map<String, Skill> skills = new HashMap<>();

    public SkillLoader(String skillsDir) {
        this.skillsDir = skillsDir;
        loadAll();  // 启动时扫描所有 SKILL.md
    }

    private void loadAll() {
        var dirPath = Paths.get(skillsDir);
        if (!Files.exists(dirPath)) {
            return;  // 目录不存在则跳过
        }

        try {
            List<java.nio.file.Path> files = Files.walk(dirPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> "SKILL.md".equals(p.getFileName().toString()))
                    .sorted()  // 排序保证顺序一致
                    .toList();

            for (var file : files) {
                String text = Files.readString(file);
                ParseResult result = parseFrontMatter(text);

                String name = result.getMeta().getOrDefault("name",
                        file.getParent().getFileName().toString());

                Skill skill = new Skill();
                skill.meta = result.getMeta();
                skill.body = result.getBody();
                skill.path = file.toString();  // 记录文件路径

                skills.put(name, skill);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getContent(String args) {
        String name = JSON.parseObject(args).getString("name");
        if (!skills.containsKey(name)) {
            return "错误：未知技能 '" + name + "'。 可用的技能: " + String.join(", ", skills.keySet());
        }
        Skill skill = skills.get(name);
        return "<skill name=\"" + name + "\">\n" + skill.body + "\n</skill>";
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
                line += " [" + tags + "]";  // 支持 Tags 显示
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }
}
```

### 4. 前置元数据解析

```java
private ParseResult parseFrontMatter(String text) {
    Pattern pattern = Pattern.compile("^---\\n(.*?)\\n---\\n(.*)", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(text);

    Map<String, String> meta = new HashMap<>();
    String body = text;

    if (matcher.find()) {
        String metaStr = matcher.group(1).trim();
        body = matcher.group(2).trim();
        
        Yaml yaml = new Yaml();
        meta = yaml.load(metaStr);
    }
    return new ParseResult(meta, body);
}
```

### 5. 工具注册

```java
private static final SkillLoader SKILL_LOADER = new SkillLoader(Commons.SKILLS_DIR);

private static final Map<String, Function<String, String>> TOOL_HANDLERS = new HashMap<>();

static {
    TOOL_HANDLERS.put("bash", Tools::runBash);
    TOOL_HANDLERS.put("readFile", Tools::runReadFile);
    TOOL_HANDLERS.put("writeFile", Tools::runWriteFile);
    TOOL_HANDLERS.put("editFile", Tools::runEditFile);
    TOOL_HANDLERS.put("loadSkill", SKILL_LOADER::getContent);  // 新增
}

private static final List<ChatCompletionTool> tools = List.of(
    Tools.bashTool(),
    Tools.readFileTool(),
    Tools.writeFileTool(),
    Tools.editFileTool(),
    Tools.loadSkillTool()  // 新增
);
```

### 6. SYSTEM 提示词

```java
private static final String SYSTEM = "你是工作目录 " + Commons.CWD
        + " 下的编程智能体，遇到陌生业务场景前，请先调用 `load_skill` 加载专属专业知识。可用技能列表："
        + SKILL_LOADER.getDescriptions()
        + "\n\n"
        + "如果需要进一步加载资源或脚本，可以在 " + Commons.SKILLS_DIR + " 目录下进行搜索，"
        + "禁止自己创建资源或脚本文件";
```

生成的 SYSTEM 示例：
```
你是工作目录 /path/to/project 下的编程智能体...
可用技能列表：
  - excel-operation-skill: 基于Java+JBang实现的Excel自动化操作技能 [excel, jbang]
  - mcp-builder: Build MCP servers for any service [mcp, server, api]

如果需要进一步加载资源或脚本，可以在 skills/ 目录下进行搜索，禁止自己创建资源或脚本文件
```

### 7. loadSkillTool 定义

```java
public static ChatCompletionTool loadSkillTool() {
    Map<String, JsonValue> paramMap = new HashMap<>();
    paramMap.put("type", JsonValue.from("object"));

    Map<String, JsonValue> nameProp = new HashMap<>();
    nameProp.put("type", JsonValue.from("string"));
    nameProp.put("description", JsonValue.from("技能名称，如 excel"));

    paramMap.put("properties", JsonValue.from(Map.of("name", JsonValue.from(nameProp))));
    paramMap.put("required", JsonValue.from(List.of("name")));

    return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
            .function(FunctionDefinition.builder()
                    .name("loadSkill")
                    .description("加载技能文档，获取专业领域知识")
                    .parameters(FunctionParameters.builder()
                            .putAllAdditionalProperties(paramMap)
                            .build())
                    .build())
            .build());
}
```

## 工具 vs 技能：核心区别

| 维度 | 工具 (Tool) | 技能 (Skill) |
|------|-------------|--------------|
| 作用 | 模型 CAN DO | 模型 KNOWS |
| 存在形式 | 可执行代码 | 文档/Markdown |
| 注入方式 | 工具定义 (JSON Schema) | `<skill>` 标签包裹的文本 |
| 加载时机 | 始终可用 | 按需调用 |
| 示例 | bash, readFile | excel |

## 相对 s04 的变更

| 组件 | s04 | s05 |
|------|-----|-----|
| Tools | 5 (基础) + task | 5 (基础) + loadSkill |
| 知识管理 | 无 | SkillLoader 按需加载 |
| SYSTEM | 固定提示 | 动态注入技能列表 |

## 试试看

1. `有哪些可用技能？`
2. `加载 excel 技能，帮我把 data.xlsx 的第一个 sheet 导出为 CSV`

## 核心要义

> **"Load knowledge when you need it, not upfront"**  
> 知识按需加载，工具始终在线

**设计原则：**
- 工具是能力，技能是知识
- 工具预定义，技能按需加载
- `<skill>` 标签包裹返回内容，方便模型识别

下篇预告：[S06ContextCompact - 上下文压缩：上下文会满，你需要腾出空间](./S06ContextCompact.md)
