package org.jc.component.enums;

import java.util.Objects;
import java.util.StringJoiner;

public enum PlanRequestStatus {
    APPROVED("approved"),
    REJECTED("rejected"),
    PENDING("pending");

    private final String value;

    PlanRequestStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean is(String value) {
        return Objects.equals(value, this.getValue());
    }

    public static boolean has(String value) {
        for (PlanRequestStatus anEnum : PlanRequestStatus.values()) {
            if (anEnum.is(value)) {
                return true;
            }
        }
        return false;
    }

    public static String renderValidMsgTypes() {
        StringJoiner sj = new StringJoiner(",");
        for (PlanRequestStatus value : PlanRequestStatus.values()) {
            sj.add(value.getValue());
        }
        return sj.toString();
    }
}
