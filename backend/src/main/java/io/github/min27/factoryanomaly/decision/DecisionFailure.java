package io.github.min27.factoryanomaly.decision;

import io.github.min27.factoryanomaly.reading.SensorReading;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 엔진이 판정을 내지 못한 기록. decision에는 유효한 판정만 남기고 실패는 여기에 쌓는다 (D-017). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DecisionFailure {

    static final int MESSAGE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reading_id", nullable = false)
    private SensorReading reading;

    @Column(nullable = false, length = 20)
    private String engine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FailureReason reason;

    @Column(length = MESSAGE_MAX_LENGTH)
    private String message;

    private long latencyUs;       // 실패가 확정되기까지 걸린 시간, 마이크로초

    @Column(nullable = false)
    private Instant failedAt;

    @Builder
    private DecisionFailure(SensorReading reading, String engine, FailureReason reason, String message,
                            long latencyUs, Instant failedAt) {
        this.reading = reading;
        this.engine = engine;
        this.reason = reason;
        this.message = message == null || message.length() <= MESSAGE_MAX_LENGTH
                ? message : message.substring(0, MESSAGE_MAX_LENGTH);
        this.latencyUs = latencyUs;
        this.failedAt = failedAt;
    }
}
