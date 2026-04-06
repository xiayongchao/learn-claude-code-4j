package org.jc.component.enums;

import java.util.Objects;
import java.util.StringJoiner;

public enum ShutdownRequestStatus {
    APPROVED("approved"),
    REJECTED("rejected"),
    PENDING("pending");

    private final String value;

    ShutdownRequestStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean is(String value) {
        return Objects.equals(value, this.getValue());
    }

    public static boolean has(String value) {
        for (ShutdownRequestStatus anEnum : ShutdownRequestStatus.values()) {
            if (anEnum.is(value)) {
                return true;
            }
        }
        return false;
    }

    public static String renderValidMsgTypes() {
        StringJoiner sj = new StringJoiner(",");
        for (ShutdownRequestStatus value : ShutdownRequestStatus.values()) {
            sj.add(value.getValue());
        }
        return sj.toString();
    }
}
