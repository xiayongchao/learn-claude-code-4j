package org.jc.component.team;

import java.util.List;

public class TeamConfig {
    private String teamName;
    private List<Teammate> teammates;

    public TeamConfig() {
    }

    public TeamConfig(String teamName, List<Teammate> teammates) {
        this.teamName = teamName;
        this.teammates = teammates;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public List<Teammate> getTeammates() {
        return teammates;
    }

    public void setTeammates(List<Teammate> teammates) {
        this.teammates = teammates;
    }
}
