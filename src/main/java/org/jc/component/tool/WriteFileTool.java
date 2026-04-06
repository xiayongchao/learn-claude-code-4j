package org.jc.component.tool;

import org.jc.Commons;
import org.jc.component.tool.args.WriteFileToolArgs;
import org.jc.component.state.States;
import org.jc.component.util.FileUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class WriteFileTool extends BaseTool<WriteFileToolArgs> {
    public WriteFileTool() {
        super("writeFile", WriteFileToolArgs.class, """
                {
                     "type": "function",
                     "function": {
                         "name": "writeFile",
                         "description": "写入文件内容",
                         "parameters": {
                             "type": "object",
                             "properties": {
                                 "path": {
                                     "type": "string"
                                 },
                                 "content": {
                                     "type": "string"
                                 }
                             },
                             "required": [
                                 "path",
                                 "content"
                             ]
                         }
                     }
                 }
                """);
    }

    @Override
    public String doCall(WriteFileToolArgs arguments) {
        String path = arguments.getPath();
        String content = arguments.getContent();
        String workDir = States.get().getWorkDir();

        if (!Commons.isSafePath(workDir, path)) {
            return "错误：路径超出工作区：" + path;
        }

        try {
            Path filePath = Paths.get(workDir).resolve(path);
            FileUtils.write(filePath, content);
            return "已写入 " + content.length() + " 字符";
        } catch (Exception e) {
            return "错误：" + e.getMessage();
        }
    }
}
