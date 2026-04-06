package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.tool.args.SpawnTeammateToolArgs;
import org.jc.component.enums.TeammateStatus;
import org.jc.component.loop.ReActs;
import org.jc.component.state.States;
import org.jc.component.state.TeammateState;
import org.jc.component.team.Team;
import org.jc.component.team.TeamConfig;
import org.jc.component.team.Teammate;

import java.util.ArrayList;
import java.util.Objects;

public class S10SpawnTeammateTool extends BaseTool<SpawnTeammateToolArgs> {
    private final ReActs reActs;
    private final Team team;

    @Inject
    public S10SpawnTeammateTool(ReActs reActs, Team team) {
        super("spawnTeammate", SpawnTeammateToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "spawnTeammate",
                        "description": "生成一个在独立线程中运行的常驻团队成员",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "name": {
                                    "type": "string"
                                },
                                "role": {
                                    "type": "string"
                                },
                                "prompt": {
                                    "type": "string"
                                }
                            },
                            "required": [
                                "name",
                                "role",
                                "prompt"
                            ]
                        }
                    }
                }
                """);
        this.reActs = reActs;
        this.team = team;
    }

    @Override
    public String doCall(SpawnTeammateToolArgs arguments) {
        String name = arguments.getName();
        String role = arguments.getRole();
        String prompt = arguments.getPrompt();

        String workDir = States.get().getWorkDir();

        TeammateState state = new TeammateState();
        state.setName(name);
        state.setModel(States.get().getModel());
        state.setRole(role);
        state.setPrompt(String.format("你是 %s，角色：%s，工作目录：%s，" +
                "执行重要工作前需通过 planApproval 提交方案审批，" +
                "收到 shutdownRequest 时请以 shutdownResponse 回应", name, role, workDir));
        state.setUserPrompt(prompt);
        state.setMaxLoopTimes(50);
        state.setWorkDir(workDir);
        state.setLead(States.get().getName());
        state.setMessages(new ArrayList<>());

        //设置队友
        this.teammate(name, role);
        this.reActs.start(state);

        return String.format("创建 '%s' (角色: %s)", name, role);
    }

    private void teammate(String name, String role) {
        TeamConfig teamConfig = this.team.readTeamConfig();
        if (teamConfig == null) {
            teamConfig = new TeamConfig();
        }

        if (teamConfig.getTeamName() == null) {
            teamConfig.setTeamName(Team.DEFAULT);
        }

        if (teamConfig.getTeammates() == null) {
            teamConfig.setTeammates(new ArrayList<>());
        }

        Teammate teammate = teamConfig.getTeammates().stream()
                .filter(it -> Objects.equals(it.getName(), name))
                .findFirst()
                .orElse(null);
        if (teammate == null) {
            teammate = new Teammate();
            teammate.setName(name);
            teammate.setRole(role);
            teammate.setStatus(TeammateStatus.WORKING.getValue());
            teamConfig.getTeammates().add(teammate);
        } else {
            teammate.setRole(role);
            teammate.setStatus(TeammateStatus.WORKING.getValue());
        }
        this.team.writeTeamConfig(teamConfig);
    }
}
