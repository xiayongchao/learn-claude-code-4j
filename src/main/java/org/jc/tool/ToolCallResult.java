package org.jc.tool;

public class ToolCallResult {
    private final boolean idle;

    private ToolCallResult(boolean idle) {
        this.idle = idle;
    }

    public static ToolCallResult of() {
        return new ToolCallResult(false);
    }

    public static ToolCallResult of(boolean idle) {
        return new ToolCallResult(idle);
    }

    public boolean isIdle() {
        return idle;
    }
}
