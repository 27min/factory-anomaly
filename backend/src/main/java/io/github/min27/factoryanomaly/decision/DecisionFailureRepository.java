package io.github.min27.factoryanomaly.decision;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionFailureRepository extends JpaRepository<DecisionFailure, Long> {

    List<DecisionFailure> findByReadingIdOrderByEngine(Long readingId);

    List<DecisionFailure> findByReadingIdIn(Collection<Long> readingIds);

    /** 엔진별 실패 건수. */
    @Query("select new io.github.min27.factoryanomaly.decision.FailureCount(f.engine, count(f))"
            + " from DecisionFailure f where f.reading.id >= :minReadingId group by f.engine")
    List<FailureCount> countByEngineSince(@Param("minReadingId") Long minReadingId);
}
