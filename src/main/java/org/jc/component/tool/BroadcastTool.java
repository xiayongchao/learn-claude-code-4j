package org.jc.component.tool;

import com.google.inject.Inject;
import org.jc.component.tool.args.BroadcastToolArgs;
import org.jc.component.inbox.InBoxMessage;
import org.jc.component.inbox.MessageBus;
import org.jc.component.state.States;
import org.jc.component.team.Team;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class BroadcastTool extends BaseTool<BroadcastToolArgs> {
    private final MessageBus bus;
    private final Team team;

    @Inject
    public BroadcastTool(MessageBus bus, Team team) {
        super("broadcast", BroadcastToolArgs.class, """
                {
                    "type": "function",
                    "function": {
                        "name": "broadcast",
                        "description": "向所有团队成员发送消息。",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "content": {
                                    "type": "string"
                                }
                            },
                            "required": [
                                "content"
                            ]
                        }
                    }
                }
                """);
        this.bus = Objects.requireNonNull(bus);
        this.team = Objects.requireNonNull(team);
    }

    @Override
    public String doCall(BroadcastToolArgs arguments) {
        List<String> teammateNames = this.team.getTeammateNames();
        int count = 0;
        if (teammateNames != null) {
            for (String teammateName : teammateNames) {
                try {
                    InBoxMessage message = new InBoxMessage();
                    message.setContent(arguments.getContent());
                    message.setFrom(States.get().getName());
                    message.setExtra(null);
                    message.setTimestamp(System.currentTimeMillis());
                    message.setType("broadcast");
                    this.bus.send(teammateName, message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return String.format("广播 %s 个队友", count);
    }
}
