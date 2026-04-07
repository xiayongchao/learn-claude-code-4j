package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.tool.args.WorktreeStatusToolArgs;
import org.jc.component.worktree.Worktrees;

public class WorktreeKeepTool extends BaseTool<WorktreeStatusToolArgs> {
    private final Worktrees worktrees;

    @Inject
    public WorktreeKeepTool(Worktrees worktrees) {
        super("worktreeKeep", WorktreeStatusToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "worktreeKeep",
                        "description": "Mark a worktree as kept in lifecycle state without removing it.",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "name": {
                                    "type": "string"
                                }
                            },
                            "required": [
                                "name"
                            ]
                        }
                    }
                }
                """);
        this.worktrees = worktrees;
    }

    @Override
    public String doCall(WorktreeStatusToolArgs arguments) {
        return worktrees.keep(arguments.getName());
    }
}