package org.jc.component.tool;

import org.jc.Commons;
import org.jc.component.tool.args.EditFileToolArgs;
import org.jc.component.state.States;
import org.jc.component.util.FileUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class EditFileTool extends BaseTool<EditFileToolArgs> {

    public EditFileTool() {
        super("editFile", EditFileToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "editFile",
                        "description": "精确替换文件中的文本内容。",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "path": {
                                    "type": "string"
                                },
                                "old_text": {
                                    "type": "string"
                                },
                                "new_text": {
                                    "type": "string"
                                }
                            },
                            "required": [
                                "path",
                                "old_text",
                                "new_text"
                            ]
                        }
                    }
                }
                """);
    }

    @Override
    public String doCall(EditFileToolArgs arguments) {
        String path = arguments.getPath();
        String oldText = arguments.getOldText();
        String newText = arguments.getNewText();
        String workDir = States.get().getWorkDir();

        if (!Commons.isSafePath(workDir, path)) {
            return "错误：路径超出工作区：" + path;
        }

        try {
            Path filePath = Paths.get(workDir).resolve(path);
            String content = FileUtils.read(filePath);

            if (!content.contains(oldText)) {
                return "错误：在 " + path + " 中未找到文本";
            }

            String updated = content.replaceFirst(oldText, newText);
            FileUtils.write(filePath, updated);
            return "已编辑 " + path;
        } catch (Exception e) {
            return "错误：" + e.getMessage();
        }
    }
}
