package org.jc.component.state;

public class States {
    private static final ThreadLocal<State> CTX = new ThreadLocal<>();

    public static void set(State state) {
        CTX.set(state);
    }

    public static State get() {
        return CTX.get();
    }

    public static LeadState lead() {
        return (LeadState) get();
    }

    public static TeammateState teammate() {
        return (TeammateState) get();
    }

    public static void clear() {
        CTX.remove(); // 关键！
    }
}
