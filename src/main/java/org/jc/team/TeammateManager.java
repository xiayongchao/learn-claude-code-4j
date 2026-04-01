package org.jc.team;

import com.alibaba.fastjson2.JSON;
import org.jc.agent.TeammateAgent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeammateManager {
    // 线程：name -> Thread
    private final ConcurrentHashMap<String, Thread> threads = new ConcurrentHashMap<>();

    private final Path configPath;
    private final Team team;


    // ===================== 构造方法（传入目录字符串） =====================
    public TeammateManager(String teamDir) {
        Path dir = Paths.get(teamDir);
        this.configPath = dir.resolve("team.json");

        try {
            Files.createDirectories(dir);
            this.team = loadTeam();
        } catch (Exception e) {
            throw new RuntimeException("初始化团队失败", e);
        }
    }

    // ===================== 加载配置 =====================
    private Team loadTeam() {
        try {
            if (Files.exists(configPath)) {
                return JSON.parseObject(Files.readString(configPath), Team.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Team();
    }

    // ===================== 保存配置 =====================
    public void saveTeam() {
        try {
            String json = JSON.toJSONString(team);
            Files.writeString(configPath, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===================== 查找成员 =====================
    public Teammate findTeammate(String name) {
        for (Teammate m : team.getTeammates()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    // ===================== 创建/启动队友 =====================

    public String spawn(TeammateAgent agent, String prompt) {
        String name = agent.name();
        String role = agent.role();
        Teammate teammate = findTeammate(name);
        if (teammate != null) {
            String status = teammate.getStatus();
            if (!"idle".equals(status) && !"shutdown".equals(status)) {
                return "错误：'" + name + "' 当前状态为 " + status;
            }
            teammate.setStatus("working");
            teammate.setRole(role);
        } else {
            teammate = new Teammate(name, role, "working");
            team.getTeammates().add(teammate);
        }

        saveTeam();

        // 启动线程
        Thread thread = new Thread(
                () -> agent.loop(prompt)
        );
        thread.setDaemon(true);
        threads.put(name, thread);
        thread.start();

        return "创建 '" + name + "' (角色: " + role + ")";
    }

    // ===================== 列出所有成员 =====================
    public String listTeammate() {
        if (team.getTeammates().isEmpty()) {
            return "无队友成员";
        }
        List<String> lines = new ArrayList<>();
        lines.add("团队: " + team.getTeamName());
        for (Teammate m : team.getTeammates()) {
            lines.add("  " + m.getName() + " (" + m.getRole() + "): " + m.getStatus());
        }
        return String.join("\n", lines);
    }

    // ===================== 获取成员名称列表 =====================
    public List<String> listTeammateNames() {
        List<String> names = new ArrayList<>();
        for (Teammate m : team.getTeammates()) {
            names.add(m.getName());
        }
        return names;
    }
}
