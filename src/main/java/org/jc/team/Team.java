package org.jc.team;


import java.util.ArrayList;
import java.util.List;

public class Team {
    private String teamName = "default";
    private List<Teammate> teammates = new ArrayList<>();

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
