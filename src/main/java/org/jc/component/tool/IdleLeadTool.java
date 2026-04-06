package org.jc.component.tool;

public class IdleLeadTool extends BaseTool<Void> {
    public IdleLeadTool() {
        super("idle", Void.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "idle",
                        "description": "进入空闲状态",
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
        return "负责人需保持工作状态，不得空闲";
    }
}
