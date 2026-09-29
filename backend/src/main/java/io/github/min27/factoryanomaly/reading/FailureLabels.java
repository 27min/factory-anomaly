package io.github.min27.factoryanomaly.reading;

import jakarta.persistence.Embeddable;

/**
 * 실제 고장 여부와 유형별 라벨 (정답 데이터).
 * 현장에서는 사후에 채워지는 값이므로 모두 null을 허용한다.
 * 여러 유형이 동시에 발생할 수 있어 유형별로 따로 저장한다 (D-008).
 */
@Embeddable
public record FailureLabels(
        Boolean machineFailure,
        Boolean twf,
        Boolean hdf,
        Boolean pwf,
        Boolean osf,
        Boolean rnf
) {
}
