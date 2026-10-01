package io.github.min27.factoryanomaly.decision;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, Long> {

    List<Decision> findByReadingIdOrderByEngine(Long readingId);
}
