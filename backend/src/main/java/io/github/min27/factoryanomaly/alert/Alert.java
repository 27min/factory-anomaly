package io.github.min27.factoryanomaly.alert;

import io.github.min27.factoryanomaly.decision.Decision;
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

@Entity
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
    private AlertStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Alert(Decision decision, Equipment equipment, double severity, Instant createdAt) {
        this.decision = decision;
        this.equipment = equipment;
        this.severity = severity;
        this.status = AlertStatus.OPEN;
        this.createdAt = createdAt;
    }

    public static Alert open(Decision decision, Equipment equipment, Instant createdAt) {
        return new Alert(decision, equipment, decision.getSeverity(), createdAt);
    }
}
