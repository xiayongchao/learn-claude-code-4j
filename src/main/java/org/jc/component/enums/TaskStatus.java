package org.jc.component.enums;

import java.util.Objects;

public enum TaskStatus {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean is(String value) {
        return Objects.equals(value, this.getValue());
    }

    public static boolean has(String value) {
        for (TaskStatus anEnum : TaskStatus.values()) {
            if (anEnum.is(value)) {
                return true;
            }
        }
        return false;
    }
}
