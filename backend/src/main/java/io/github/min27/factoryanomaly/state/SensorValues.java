package io.github.min27.factoryanomaly.state;

import io.github.min27.factoryanomaly.reading.ProductType;
import java.util.Objects;

/** 설비에서 들어온 원본 센서값. */
public record SensorValues(
        ProductType productType,
        double airTemp,       // K
        double processTemp,   // K
        int rotSpeed,         // rpm
        double torque,        // Nm
        int toolWear          // min
) {
    public SensorValues {
        Objects.requireNonNull(productType, "productType");
    }
}
