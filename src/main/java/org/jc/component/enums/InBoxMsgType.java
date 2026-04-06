package org.jc.component.enums;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public enum InBoxMsgType {
    MESSAGE("message"),
    BROADCAST("broadcast"),
    SHUTDOWN_REQUEST("shutdown_request"),
    SHUTDOWN_RESPONSE("shutdown_response"),
    PLAN_APPROVAL_RESPONSE("plan_approval_response");

    private final String value;

    InBoxMsgType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static final Set<String> VALID_MSG_TYPES = Arrays.stream(InBoxMsgType.values())
            .map(InBoxMsgType::getValue)
            .collect(Collectors.toSet());

    public boolean is(String value) {
        return Objects.equals(value, this.getValue());
    }

    public static boolean has(String value) {
        for (InBoxMsgType anEnum : InBoxMsgType.values()) {
            if (anEnum.is(value)) {
                return true;
            }
        }
        return false;
    }

    public static String renderValidMsgTypes() {
        StringJoiner sj = new StringJoiner(",");
        for (InBoxMsgType value : InBoxMsgType.values()) {
            sj.add(value.getValue());
        }
        return sj.toString();
    }
}
