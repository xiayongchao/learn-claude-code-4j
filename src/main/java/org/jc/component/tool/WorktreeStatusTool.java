package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.tool.args.WorktreeStatusToolArgs;
import org.jc.component.worktree.Worktrees;

public class WorktreeStatusTool extends BaseTool<WorktreeStatusToolArgs> {
    private final Worktrees worktrees;

    @Inject
    public WorktreeStatusTool(Worktrees worktrees) {
        super("worktreeStatus", WorktreeStatusToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "worktreeStatus",
                        "description": "Show git status for one worktree.",
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
        return worktrees.status(arguments.getName());
    }
}