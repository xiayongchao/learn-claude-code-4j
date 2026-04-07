package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.worktree.Worktrees;

public class WorktreeListTool extends BaseTool<Void> {
    private final Worktrees worktrees;

    @Inject
    public WorktreeListTool(Worktrees worktrees) {
        super("worktreeList", Void.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "worktreeList",
                        "description": "List worktrees tracked in .worktrees/index.json.",
                        "parameters": {
                            "type": "object",
                            "properties": {}
                        }
                    }
                }
                """);
        this.worktrees = worktrees;
    }

    @Override
    public String doCall(Void arguments) {
        return worktrees.list();
    }
}