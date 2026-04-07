package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.tool.args.WorktreeRemoveToolArgs;
import org.jc.component.worktree.Worktrees;

public class WorktreeRemoveTool extends BaseTool<WorktreeRemoveToolArgs> {
    private final Worktrees worktrees;

    @Inject
    public WorktreeRemoveTool(Worktrees worktrees) {
        super("worktreeRemove", WorktreeRemoveToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "worktreeRemove",
                        "description": "Remove a worktree and optionally mark its bound task completed.",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "name": {
                                    "type": "string"
                                },
                                "force": {
                                    "type": "boolean"
                                },
                                "completeTask": {
                                    "type": "boolean"
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
    public String doCall(WorktreeRemoveToolArgs arguments) {
        return worktrees.remove(
                arguments.getName(),
                arguments.getForce() != null && arguments.getForce(),
                arguments.getCompleteTask() != null && arguments.getCompleteTask());
    }
}