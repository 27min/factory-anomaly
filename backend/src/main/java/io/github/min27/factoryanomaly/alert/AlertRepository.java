package io.github.min27.factoryanomaly.alert;

import io.github.min27.factoryanomaly.decision.FailureType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    /**
     * 같은 설비·유형의 미해결 알람에 발생 1회를 더한다. 읽고 고쳐 쓰지 않고 UPDATE 한 번으로 처리해
     * 동시에 들어온 요청끼리 횟수를 덮어쓰지 않는다. 영속성 컨텍스트를 거치지 않는 UPDATE라,
     * 같은 트랜잭션에서 이미 읽은 알람이 옛 값으로 남지 않도록 실행 전후로 flush·clear한다.
     *
     * @return 갱신된 행 수 (미해결 알람이 없으면 0, 있으면 1 — 유니크 인덱스 uq_alert_unresolved가 보장)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Alert a
               set a.occurrenceCount = a.occurrenceCount + 1, a.lastOccurredAt = :at
             where a.equipment.id = :equipmentId and a.category = :category and a.status in :statuses
            """)
    int recordOccurrence(@Param("equipmentId") Long equipmentId, @Param("category") FailureType category,
                         @Param("statuses") Collection<AlertStatus> statuses, @Param("at") Instant at);

    Optional<Alert> findByEquipmentIdAndCategoryAndStatusIn(Long equipmentId, FailureType category,
                                                            Collection<AlertStatus> statuses);

    @EntityGraph(attributePaths = "equipment")
    List<Alert> findByStatusInOrderByLastOccurredAtDesc(Collection<AlertStatus> statuses);
}
