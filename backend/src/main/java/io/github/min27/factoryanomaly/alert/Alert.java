package io.github.min27.factoryanomaly.alert;

import io.github.min27.factoryanomaly.common.InvalidAlertTransitionException;
import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.FailureType;
import io.github.min27.factoryanomaly.equipment.Equipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 사람이 처리할 이상 상황. 같은 설비·유형으로 미해결 알람이 있는 동안의 반복 발생은 새 알람을 만들지 않고
 * {@code occurrenceCount}와 {@code lastOccurredAt}에 합친다 (D-021). {@code decision}과 {@code severity}는 처음 발생 기준이다.
 *
 * <p>{@code @DynamicUpdate}: 확인·해결은 바뀐 컬럼만 UPDATE한다. 전체 컬럼을 쓰면, 엔티티를 읽은 뒤 다른 요청이
 * {@link AlertRepository#recordOccurrence}로 늘린 발생 횟수를 옛 값으로 덮어쓴다 (D-022).
 */
@Entity
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false, unique = true)
    private Decision decision;

    // decision → reading → equipment로도 찾을 수 있지만, 설비별 알람 조회가 잦아 직접 참조한다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    private double severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FailureType category;

    private int occurrenceCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastOccurredAt;

    private Instant acknowledgedAt;

    private Instant resolvedAt;

    private Alert(Decision decision, Equipment equipment, Instant createdAt) {
        if (!decision.isAnomaly()) {
            throw new IllegalArgumentException("alert requires an anomaly decision, got " + decision.getCategory());
        }
        this.decision = decision;
        this.equipment = equipment;
        this.severity = decision.getSeverity();
        this.category = decision.getCategory();
        this.occurrenceCount = 1;
        this.status = AlertStatus.OPEN;
        this.createdAt = createdAt;
        this.lastOccurredAt = createdAt;
    }

    public static Alert open(Decision decision, Equipment equipment, Instant createdAt) {
        return new Alert(decision, equipment, createdAt);
    }

    /** 담당자가 알람을 봤다. OPEN에서만 가능하다. */
    public void acknowledge(Instant at) {
        if (status != AlertStatus.OPEN) {
            throw new InvalidAlertTransitionException(id, status.name(), AlertStatus.ACKNOWLEDGED.name());
        }
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedAt = at;
    }

    /** 조치가 끝났다. 확인을 건너뛰고 바로 해결할 수도 있다. 이후 같은 설비·유형의 발생은 새 알람이 된다. */
    public void resolve(Instant at) {
        if (status == AlertStatus.RESOLVED) {
            throw new InvalidAlertTransitionException(id, status.name(), AlertStatus.RESOLVED.name());
        }
        this.status = AlertStatus.RESOLVED;
        this.resolvedAt = at;
    }
}
