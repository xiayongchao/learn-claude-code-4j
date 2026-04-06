package org.jc.component.team;

import org.jc.component.enums.TeammateStatus;
import org.jc.component.state.States;
import org.jc.component.util.FileUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Team {
    public static final String DEFAULT = "default";

    // ===================== 保存配置 =====================
    public void writeTeamConfig(TeamConfig teamConfig) {
        try {
            Path teamConfigPath = FileUtils.resolve(States.get().getWorkDir(), "team/config.json", true);
            FileUtils.write(teamConfigPath, teamConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TeamConfig readTeamConfig() {
        try {
            Path teamConfigPath = FileUtils.resolve(States.get().getWorkDir(), "team/config.json", true);
            TeamConfig teamConfig = FileUtils.read(teamConfigPath, TeamConfig.class);
            if (teamConfig != null) {
                return teamConfig;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new TeamConfig(DEFAULT, new ArrayList<>());
    }

    public void setTeammateIdle() {
        TeamConfig teamConfig = this.readTeamConfig();
        if (teamConfig == null) {
            return;
        }

        List<Teammate> teammates = teamConfig.getTeammates();
        if (teammates == null) {
            return;
        }

        Teammate teammate = teammates
                .stream()
                .filter(it -> Objects.equals(it.getName(), States.get().getName()))
                .findFirst()
                .orElse(null);
        if (teammate == null) {
            return;
        }

        if (TeammateStatus.SHUTDOWN.is(teammate.getStatus())) {
            //已经停止的直接返回
            return;
        }

        if (TeammateStatus.IDLE.is(teammate.getStatus())) {
            return;
        }

        teammate.setStatus(TeammateStatus.IDLE.getValue());
        this.writeTeamConfig(teamConfig);
    }

    public void setTeammateShutdown() {
        TeamConfig teamConfig = this.readTeamConfig();
        if (teamConfig == null) {
            return;
        }

        List<Teammate> teammates = teamConfig.getTeammates();
        if (teammates == null) {
            return;
        }

        Teammate teammate = teammates
                .stream()
                .filter(it -> Objects.equals(it.getName(), States.get().getName()))
                .findFirst()
                .orElse(null);
        if (teammate == null) {
            return;
        }

        if (TeammateStatus.SHUTDOWN.is(teammate.getStatus())) {
            //已经停止的直接返回
            return;
        }

        teammate.setStatus(TeammateStatus.SHUTDOWN.getValue());
        this.writeTeamConfig(teamConfig);
    }

    public void setTeammateWorking() {
        TeamConfig teamConfig = this.readTeamConfig();
        if (teamConfig == null) {
            return;
        }

        List<Teammate> teammates = teamConfig.getTeammates();
        if (teammates == null) {
            return;
        }

        Teammate teammate = teammates
                .stream()
                .filter(it -> Objects.equals(it.getName(), States.get().getName()))
                .findFirst()
                .orElse(null);
        if (teammate == null) {
            return;
        }

        if (TeammateStatus.WORKING.is(teammate.getStatus())) {
            return;
        }

        teammate.setStatus(TeammateStatus.WORKING.getValue());
        this.writeTeamConfig(teamConfig);
    }

    public void resetTeammateStatusAfterTurn() {
        TeamConfig teamConfig = this.readTeamConfig();
        if (teamConfig == null) {
            return;
        }

        List<Teammate> teammates = teamConfig.getTeammates();
        if (teammates == null) {
            return;
        }

        Teammate teammate = teammates
                .stream()
                .filter(it -> Objects.equals(it.getName(), States.get().getName()))
                .findFirst()
                .orElse(null);
        if (teammate == null) {
            return;
        }

        if (TeammateStatus.SHUTDOWN.is(teammate.getStatus())) {
            //已经停止的直接返回
            return;
        }

        if (States.teammate().isShutdown()) {
            //如果需要停止
            teammate.setStatus(TeammateStatus.SHUTDOWN.getValue());
            this.writeTeamConfig(teamConfig);
            return;
        }

        //否则设置为空闲
        if (TeammateStatus.IDLE.is(teammate.getStatus())) {
            return;
        }

        teammate.setStatus(TeammateStatus.IDLE.getValue());
        this.writeTeamConfig(teamConfig);
    }


    // ===================== 列出所有成员 =====================
    public String render() {
        TeamConfig teamConfig = this.readTeamConfig();
        if (teamConfig == null) {
            return "无队友成员";
        }

        List<Teammate> teammates = teamConfig.getTeammates();
        if (teammates == null || teammates.isEmpty()) {
            return "无队友成员";
        }

        List<String> lines = new ArrayList<>();
        lines.add("团队: " + teamConfig.getTeamName());
        for (Teammate teammate : teammates) {
            lines.add("  " + teammate.getName() + " (" + teammate.getRole() + "): " + teammate.getStatus());
        }
        return String.join("\n", lines);
    }

    public String getTeamName() {
        TeamConfig teamConfig = this.readTeamConfig();
        if (teamConfig == null) {
            return DEFAULT;
        }

        return teamConfig.getTeamName();
    }

    // ===================== 获取成员名称列表 =====================
    public List<String> getTeammateNames() {
        List<String> names = new ArrayList<>();

        TeamConfig teamConfig = this.readTeamConfig();
        if (teamConfig == null) {
            return names;
        }

        List<Teammate> teammates = teamConfig.getTeammates();
        if (teammates == null || teammates.isEmpty()) {
            return names;
        }

        for (Teammate teammate : teammates) {
            names.add(teammate.getName());
        }
        return names;
    }
}
