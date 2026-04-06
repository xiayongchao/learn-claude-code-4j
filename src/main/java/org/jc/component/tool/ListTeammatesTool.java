package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.team.Team;

import java.util.Objects;

public class ListTeammatesTool extends BaseTool<Void> {
    private final Team team;

    @Inject
    public ListTeammatesTool(Team team) {
        super("listTeammates", Void.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "listTeammates",
                        "description": "列出所有团队成员的姓名、角色及状态。",
                        "parameters": {
                            "type": "object",
                            "properties": {
                
                            }
                        }
                    }
                }
                """);
        this.team = Objects.requireNonNull(team);
    }

    @Override
    public String doCall(Void arguments) {
        return this.team.render();
    }
}
