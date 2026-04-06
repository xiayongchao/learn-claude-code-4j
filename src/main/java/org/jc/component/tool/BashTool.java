package org.jc.component.tool;

import com.alibaba.fastjson.JSON;
import org.jc.Commands;
import org.jc.component.tool.args.BashToolArgs;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BashTool extends BaseTool<BashToolArgs> {
    private static final Set<String> DANGEROUS = new HashSet<>(List.of("rm -rf /", "sudo", "shutdown", "reboot"));

    public BashTool() {
        super("bash", BashToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "bash",
                        "description": "执行一条 Shell 命令",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "command": {
                                    "type": "string"
                                }
                            },
                            "required": [
                                "command"
                            ]
                        }
                    }
                }
                """);
    }

    @Override
    public String doCall(BashToolArgs arguments) {
        String command = arguments.getCommand();
        if (DANGEROUS.contains(command)) {
            return "错误：危险命令被阻止";
        }

        Commands.CommandResult commandResult = Commands.execSync(command);
        if (commandResult.isTimeout()) {
            return "错误：命令执行超时 (120s)";
        }
        return JSON.toJSONString(commandResult);
    }
}
