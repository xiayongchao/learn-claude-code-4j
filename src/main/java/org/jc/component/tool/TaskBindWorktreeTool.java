package org.jc.component.tool;

import com.alibaba.fastjson2.JSON;
import com.google.inject.Inject;
import org.jc.component.tool.args.TaskBindWorktreeToolArgs;
import org.jc.component.worktree.Worktrees;

public class TaskBindWorktreeTool extends BaseTool<TaskBindWorktreeToolArgs> {
    private final Worktrees worktrees;

    @Inject
    public TaskBindWorktreeTool(Worktrees worktrees) {
        super("taskBindWorktree", TaskBindWorktreeToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "taskBindWorktree",
                        "description": "Bind a task to a worktree name.",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "taskId": {
                                    "type": "integer"
                                },
                                "worktree": {
                                    "type": "string"
                                },
                                "owner": {
                                    "type": "string"
                                }
                            },
                            "required": [
                                "taskId",
                                "worktree"
                            ]
                        }
                    }
                }
                """);
        this.worktrees = worktrees;
    }

    @Override
    public String doCall(TaskBindWorktreeToolArgs arguments) {
        int taskId = arguments.getTaskId();
        String worktree = arguments.getWorktree();
        String owner = arguments.getOwner();

        worktrees.tasks().bindWorktree(taskId, worktree, owner);
        var task = worktrees.tasks().readTask(taskId);
        return JSON.toJSONString(task);
    }
}