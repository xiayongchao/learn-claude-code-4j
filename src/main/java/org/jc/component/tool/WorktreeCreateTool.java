package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.tool.args.WorktreeCreateToolArgs;
import org.jc.component.worktree.Worktrees;

public class WorktreeCreateTool extends BaseTool<WorktreeCreateToolArgs> {
    private final Worktrees worktrees;

    @Inject
    public WorktreeCreateTool(Worktrees worktrees) {
        super("worktreeCreate", WorktreeCreateToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "worktreeCreate",
                        "description": "创建一个 Git 工作树，并可选择将其绑定到一个任务",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "name": {
                                    "type": "string"
                                },
                                "taskId": {
                                    "type": "integer"
                                },
                                "baseRef": {
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
    public String doCall(WorktreeCreateToolArgs arguments) {
        return worktrees.create(
                arguments.getName(),
                arguments.getTaskId(),
                arguments.getBaseRef());
    }
}