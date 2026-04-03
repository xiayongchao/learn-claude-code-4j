package org.jc.team;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Team {
    private final Path configPath;
    private String teamName;
    private List<Teammate> teammates;


    public Team(String teamDir) {
        Path dir = Paths.get(teamDir);
        this.configPath = dir.resolve("team.json");

        this.load();
    }

    // 单独的加载方法 → 职责清晰
    public void load() {
        try {
            Path parentDir = configPath.getParent();
            Files.createDirectories(parentDir);

            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                // 直接赋值给当前对象，不创建新对象
                JSONObject loaded = JSON.parseObject(json);

                String teamName = loaded.getString("teamName");
                this.teamName = teamName == null || teamName.isBlank() ? "default" : teamName;
                String teammates = loaded.getString("teammates");
                this.teammates = teammates == null || teammates.isBlank()
                        ? new ArrayList<>() : JSON.parseArray(teammates, Teammate.class);
            } else {
                this.teamName = "default";
                this.teammates = new ArrayList<>();
            }
        } catch (Exception e) {
            throw new RuntimeException("加载团队配置失败: " + configPath, e);
        }
    }

    public boolean isIdle(String name) {
        Teammate teammate = this.findTeammate(name);
        return teammate != null && Objects.equals("idle", teammate.getStatus());
    }

    public void idle(String name) {
        Teammate teammate = this.findTeammate(name);
        if (teammate != null) {
            teammate.setStatus("idle");
            this.saveTeam();
        }
    }

    public boolean isShutdown(String name) {
        Teammate teammate = this.findTeammate(name);
        return teammate != null && Objects.equals("shutdown", teammate.getStatus());
    }

    public void shutdown(String name) {
        Teammate teammate = this.findTeammate(name);
        if (teammate != null) {
            teammate.setStatus("shutdown");
            this.saveTeam();
        }
    }

    public void working(String name) {
        Teammate teammate = this.findTeammate(name);
        if (teammate != null) {
            teammate.setStatus("working");
            this.saveTeam();
        }
    }

    // ===================== 保存配置 =====================
    public void saveTeam() {
        try {
            String json = JSON.toJSONString(this);
            Files.writeString(configPath, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===================== 查找成员 =====================
    public Teammate findTeammate(String name) {
        for (Teammate m : this.getTeammates()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    // ===================== 列出所有成员 =====================
    public String listTeammate() {
        if (this.getTeammates().isEmpty()) {
            return "无队友成员";
        }
        List<String> lines = new ArrayList<>();
        lines.add("团队: " + this.getTeamName());
        for (Teammate m : this.getTeammates()) {
            lines.add("  " + m.getName() + " (" + m.getRole() + "): " + m.getStatus());
        }
        return String.join("\n", lines);
    }

    // ===================== 获取成员名称列表 =====================
    public List<String> listTeammateNames() {
        List<String> names = new ArrayList<>();
        for (Teammate m : this.getTeammates()) {
            names.add(m.getName());
        }
        return names;
    }

    /// //////////

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
