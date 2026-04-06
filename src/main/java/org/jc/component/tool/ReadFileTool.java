package org.jc.component.tool;

import org.jc.Commons;
import org.jc.component.tool.args.ReadFileToolArgs;
import org.jc.component.state.States;
import org.jc.component.util.FileUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ReadFileTool extends BaseTool<ReadFileToolArgs> {
    public ReadFileTool() {
        super("readFile", ReadFileToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "readFile",
                        "description": "读取文件内容",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "path": {
                                    "type": "string"
                                },
                                "limit": {
                                    "type": "integer"
                                }
                            },
                            "required": [
                                "path"
                            ]
                        }
                    }
                }
                """);
    }

    @Override
    public String doCall(ReadFileToolArgs arguments) {
        String path = arguments.getPath();
        Integer limit = arguments.getLimit();
        String workDir = States.get().getWorkDir();

        if (!Commons.isSafePath(workDir, path)) {
            return "路径超出工作区：" + path;
        }
        try {
            Path filePath = Paths.get(workDir).resolve(path);
            List<String> lines = FileUtils.readList(filePath, String.class);

            if (limit != null && limit < lines.size()) {
                List<String> showLines = lines.subList(0, limit);
                showLines.add("... (还有 " + (lines.size() - limit) + " 行)");
                lines = showLines;
            }

            String content = String.join("\n", lines);
            return content.length() > 50000 ? content.substring(0, 50000) : content;
        } catch (Exception e) {
            return "错误：" + e.getMessage();
        }
    }
}
