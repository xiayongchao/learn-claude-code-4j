package org.jc.component.enums;

import java.util.Objects;

public enum TeammateStatus {
    WORKING("working"),
    IDLE("idle"),
    SHUTDOWN("shutdown");

    private final String value;

    TeammateStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean is(String value) {
        return Objects.equals(value, this.getValue());
    }
}
