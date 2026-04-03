package org.jc.team;

import org.jc.agent.TeammateAgent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeammateManager {
    private final ConcurrentHashMap<String, Thread> threads = new ConcurrentHashMap<>();

    private final Team team;


    // ===================== 构造方法（传入目录字符串） =====================
    public TeammateManager(Team team) {
        this.team = team;
    }


    // ===================== 创建/启动队友 =====================

    public String spawn(TeammateAgent agent, String prompt) {
        String name = agent.getName();
        String role = agent.getRole();
        Teammate teammate = team.findTeammate(name);
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

        team.saveTeam();

        // 启动线程
        Thread thread = new Thread(
                () -> agent.loop(prompt)
        );
        thread.setDaemon(true);
        threads.put(name, thread);
        thread.start();

        return "创建 '" + name + "' (角色: " + role + ")";
    }
}
