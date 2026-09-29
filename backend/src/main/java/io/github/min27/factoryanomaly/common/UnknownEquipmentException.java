package io.github.min27.factoryanomaly.common;

public class UnknownEquipmentException extends RuntimeException {

    public UnknownEquipmentException(String equipmentCode) {
        super("등록되지 않은 설비입니다: " + equipmentCode);
    }
}
