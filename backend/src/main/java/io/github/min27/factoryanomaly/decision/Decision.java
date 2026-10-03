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

/** 한 측정값에 대한 한 엔진의 판정 결과. 벤치마크 모드에서는 측정값 하나에 엔진 수만큼 쌓인다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reading_id", nullable = false)
    private SensorReading reading;

    @Column(nullable = false, length = 20)
    private String engine;

    private boolean anomaly;

    private double severity;      // 0~100

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FailureType category;

    private double confidence;    // 0~1

    private long latencyUs;     // 마이크로초 (D-014)

    @Column(nullable = false)
    private Instant decidedAt;

    @Builder
    private Decision(SensorReading reading, String engine, boolean anomaly, double severity,
                     FailureType category, double confidence, long latencyUs, Instant decidedAt) {
        this.reading = reading;
        this.engine = engine;
        this.anomaly = anomaly;
        this.severity = severity;
        this.category = category;
        this.confidence = confidence;
        this.latencyUs = latencyUs;
        this.decidedAt = decidedAt;
    }
}
