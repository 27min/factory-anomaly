package io.github.min27.factoryanomaly.decision;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionFailureRepository extends JpaRepository<DecisionFailure, Long> {

    List<DecisionFailure> findByReadingIdOrderByEngine(Long readingId);
}
