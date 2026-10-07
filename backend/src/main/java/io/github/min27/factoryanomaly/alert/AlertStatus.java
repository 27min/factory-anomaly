package io.github.min27.factoryanomaly.alert;

import java.util.Set;

public enum AlertStatus {
    OPEN, ACKNOWLEDGED, RESOLVED;

    /** 아직 처리되지 않은 상태. 설비·유형마다 이 상태의 알람은 하나뿐이다 (uq_alert_unresolved). */
    public static final Set<AlertStatus> UNRESOLVED = Set.of(OPEN, ACKNOWLEDGED);
}
