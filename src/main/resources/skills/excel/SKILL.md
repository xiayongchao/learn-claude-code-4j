---
name: excel-operation-skill
description: |
    基于Java+JBang实现的Excel自动化操作技能，无需手动安装依赖、无需配置环境、无需打包。
    支持自动检测并安装JBang运行环境，支持读取Excel、导出Excel、指定Sheet读取、自定义输入输出路径。
    工具类统一放在 scripts/ 目录下，与SKILL.md同级。
    适用于脚本调用、自动化任务、批处理、外部系统调用、定时调度等场景。
    全流程自动处理：检查环境→安装JBang→下载依赖→执行Excel操作，无需人工干预。
Keywords: excel, java命令, jbang, 自动安装, 动态依赖, 命令行, 导入, 导出, map, 输入路径, 输出路径, 脚本, scripts目录
---

# Excel 操作技能（Skill）

## 核心理念
模型通过 **Java + JBang** 完成全自动化 Excel 操作，无需用户安装任何依赖。
执行流程：
**前置检查环境 → 自动安装JBang → 生成执行命令 → 执行Excel操作 → 返回结果**

执行循环：
```
LOOP:
1. 用户提出需求（读取/导出）+ 提供输入路径、输出路径
2. Agent 先检查当前环境是否安装 JBang
3. 未安装 → 自动执行系统命令安装 JBang
4. 安装完成 → 生成 JBang 运行命令
5. 调用 JBang 执行 scripts 目录下的 Excel 工具
6. 自动下载依赖并完成读写，输出结果
```

## 三大核心要素

### 1. 能力（Capabilities）
- 自动检测 JBang 环境，未安装则**全自动静默安装**
- 从**自定义输入路径**读取 Excel，返回 Map 列表
- 导出数据到**自定义输出路径**生成 Excel 文件
- 支持指定 Sheet 索引读取
- 支持自定义表头 + 动态数据
- **无需 Maven/Gradle**，依赖自动下载
- 工具类位于 `scripts/` 目录，与 SKILL.md 同级

### 2. 知识（Knowledge）
- 检查 JBang 是否安装命令：`jbang --version`
- Windows 自动安装命令：`powershell -Command "iex '& { $(iwr https://ps.jbang.dev) } app setup'"`
- macOS/Linux 自动安装命令：`curl -Ls https://sh.jbang.dev | bash -s - app setup`
- 基于 Map 的无实体类 Excel 操作
- JBang 脚本自动依赖管理，无需配置
- 内置样式：表头加粗、居中、自动换行
- 大数据量流式处理，不溢出内存

### 3. 上下文（Context）
- 操作类型：read / write / readSheet
- 输入文件路径（源Excel）
- 输出文件路径（目标Excel）
- 可选：Sheet编号、表头行数
- 环境状态：jbang_installed (true/false)

---

## Scaffolding
- `scripts/ExcelTool.java` - JBang 单文件 Excel 操作工具（自动依赖、读取、导出）

---

## Agent 执行逻辑（必须严格遵守）
当用户需要操作 Excel 时：
1. **第一步：检查 JBang 是否安装**
   执行命令：`jbang --version`
   - 返回 0 → 已安装
   - 返回非 0 → 未安装，进入自动安装

2. **第二步：未安装则执行系统安装命令**
   - Windows：
     `powershell -Command "iex '& { $(iwr https://ps.jbang.dev) } app setup'"`
   - macOS/Linux：
     `curl -Ls https://sh.jbang.dev | bash -s - app setup`

3. **第三步：安装完成后，生成 JBang 执行命令**

4. **第四步：执行脚本并返回结果**

---

## 执行命令格式
### 读取 Excel
```bash
jbang scripts/ExcelTool.java read <输入Excel路径>
```

### 导出 Excel
```bash
jbang scripts/ExcelTool.java write <输出Excel路径>
```

### 读取指定 Sheet
```bash
jbang scripts/ExcelTool.java readSheet <输入路径> <sheet编号>
```

---

## 约束规则
- 工具固定位于 `scripts/ExcelTool.java`
- **必须先检查 JBang 环境，再执行命令**
- 未安装 JBang 必须先自动安装
- 输入/输出路径全部由外部传入，不硬编码
- 单文件脚本，依赖自动下载
- 异常直接输出控制台，便于脚本捕获
- Agent 必须使用 `jbang scripts/ExcelTool.java` 格式执行
```