package io.github.min27.factoryanomaly.decision;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionRepository extends JpaRepository<Decision, Long> {

    List<Decision> findByReadingIdOrderByEngine(Long readingId);

    Optional<Decision> findByReadingIdAndEngine(Long readingId, String engine);

    List<Decision> findByReadingIdIn(Collection<Long> readingIds);

    /** 엔진별 요약용. 엔티티 대신 필요한 값만 읽는다. */
    @Query("select new io.github.min27.factoryanomaly.decision.DecisionStat(d.engine, d.anomaly, d.latencyUs)"
            + " from Decision d where d.reading.id >= :minReadingId")
    List<DecisionStat> findStatsSince(@Param("minReadingId") Long minReadingId);
}
