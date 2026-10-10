package io.github.min27.factoryanomaly.reading;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    /** 설비의 최신 측정값. ix_sensor_reading_equipment_received를 탄다. */
    Optional<SensorReading> findFirstByEquipmentIdOrderByReceivedAtDescIdDesc(Long equipmentId);

    @EntityGraph(attributePaths = "equipment")
    List<SensorReading> findByOrderByIdDesc(Limit limit);

    @Query("select r.id from SensorReading r order by r.id desc")
    List<Long> findRecentIds(Limit limit);
}
