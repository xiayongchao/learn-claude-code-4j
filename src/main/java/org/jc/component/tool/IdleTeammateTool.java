package org.jc.component.tool;

import org.jc.component.state.States;

public class IdleTeammateTool extends BaseTool<Void> {
    public IdleTeammateTool() {
        super("idle", Void.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "idle",
                        "description": "表示你已没有更多工作，进入空闲轮询阶段",
                        "parameters": {
                            "type": "object",
                            "properties": {}
                        }
                    }
                }
                """);
    }

    /**
     * 读取收件箱（返回对象列表）
     *
     * @param arguments
     * @return
     */
    @Override
    public String doCall(Void arguments) {
        States.teammate().setIdle(true);
        return "进入空闲阶段，将轮询等待新任务";
    }
}
