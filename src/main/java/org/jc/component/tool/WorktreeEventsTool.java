package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.worktree.Worktrees;

public class WorktreeEventsTool extends BaseTool<Void> {
    private final Worktrees worktrees;

    @Inject
    public WorktreeEventsTool(Worktrees worktrees) {
        super("worktreeEvents", Void.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "worktreeEvents",
                        "description": "List recent worktree/task lifecycle events from .worktrees/events.jsonl.",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "limit": {
                                    "type": "integer"
                                }
                            }
                        }
                    }
                }
                """);
        this.worktrees = worktrees;
    }

    @Override
    public String doCall(Void arguments) {
        return worktrees.events().listRecentJson(20);
    }
}