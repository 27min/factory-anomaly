package io.github.min27.factoryanomaly.reading;

import io.github.min27.factoryanomaly.equipment.Equipment;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private ProductType productType;

    // 원본 센서값
    private double airTemp;       // K
    private double processTemp;   // K
    private int rotSpeed;         // rpm
    private double torque;        // Nm
    private int toolWear;         // min

    // 파생변수 (StateBuilder가 계산)
    private double tempDiff;      // K
    private double power;         // W
    private double wearTorque;    // min·Nm

    @Embedded
    private FailureLabels labels;

    private Integer sourceUdi;

    @Column(nullable = false)
    private Instant receivedAt;

    @Builder
    private SensorReading(Equipment equipment, ProductType productType,
                          double airTemp, double processTemp, int rotSpeed, double torque, int toolWear,
                          double tempDiff, double power, double wearTorque,
                          FailureLabels labels, Integer sourceUdi, Instant receivedAt) {
        this.equipment = equipment;
        this.productType = productType;
        this.airTemp = airTemp;
        this.processTemp = processTemp;
        this.rotSpeed = rotSpeed;
        this.torque = torque;
        this.toolWear = toolWear;
        this.tempDiff = tempDiff;
        this.power = power;
        this.wearTorque = wearTorque;
        this.labels = labels;
        this.sourceUdi = sourceUdi;
        this.receivedAt = receivedAt;
    }
}
