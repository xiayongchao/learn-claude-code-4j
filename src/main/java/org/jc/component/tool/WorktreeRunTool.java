package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.tool.args.WorktreeRunToolArgs;
import org.jc.component.worktree.Worktrees;

public class WorktreeRunTool extends BaseTool<WorktreeRunToolArgs> {
    private final Worktrees worktrees;

    @Inject
    public WorktreeRunTool(Worktrees worktrees) {
        super("worktreeRun", WorktreeRunToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "worktreeRun",
                        "description": "Run a shell command in a named worktree directory.",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "name": {
                                    "type": "string"
                                },
                                "command": {
                                    "type": "string"
                                }
                            },
                            "required": [
                                "name",
                                "command"
                            ]
                        }
                    }
                }
                """);
        this.worktrees = worktrees;
    }

    @Override
    public String doCall(WorktreeRunToolArgs arguments) {
        return worktrees.run(arguments.getName(), arguments.getCommand());
    }
}