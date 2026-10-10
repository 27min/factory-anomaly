package io.github.min27.factoryanomaly.alert;

import io.github.min27.factoryanomaly.common.AlertNotFoundException;
import io.github.min27.factoryanomaly.decision.Decision;
import io.github.min27.factoryanomaly.decision.EngineProperties;
import io.github.min27.factoryanomaly.equipment.Equipment;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대표 엔진의 판정으로 알람을 만들거나, 같은 설비·유형의 미해결 알람에 발생을 합친다 (D-021).
 * 다른 엔진의 판정은 비교용이라 알람을 내지 않는다. 엔진이 여러 개 돌아도 알람 기준은 하나다 (D-019).
 * 알람의 확인·해결도 여기서 처리한다 (D-022).
 */
@Slf4j
@Service
public class AlertService {

    /** 알람 처리 결과. */
    public enum Outcome { NONE, CREATED, MERGED }

    private final AlertRepository alertRepository;
    private final AlertProperties properties;
    private final String primaryEngine;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AlertService(AlertRepository alertRepository, AlertProperties properties, EngineProperties engineProperties,
                        PlatformTransactionManager transactionManager, Clock clock) {
        this.alertRepository = alertRepository;
        this.properties = properties;
        this.primaryEngine = engineProperties.primary();
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 한 측정값의 판정들 중 대표 엔진의 판정이 알람 조건에 맞으면 알람을 낸다.
     * 대표 엔진이 실패해 판정이 없으면 알람도 없다 (실패는 decision_failure에 남는다).
     */
    public Outcome raiseIfNeeded(List<Decision> decisions) {
        Optional<Decision> primary = decisions.stream().filter(d -> d.getEngine().equals(primaryEngine)).findFirst();
        if (primary.isEmpty() || !meetsCondition(primary.get())) {
            return Outcome.NONE;
        }
        Decision decision = primary.get();
        Instant now = Instant.now(clock);
        try {
            return tx.execute(status -> upsert(decision, now));
        } catch (DataIntegrityViolationException e) {
            // 다른 요청이 같은 설비·유형의 미해결 알람을 먼저 만들었다 (uq_alert_unresolved). 이제는 합치기로 끝난다
            log.debug("Concurrent alert for reading {}, retrying as merge", decision.getReading().getId());
            return tx.execute(status -> upsert(decision, now));
        }
    }

    /** 이상 판정이고 심각도가 하한 이상이면 알람. 엔진마다 정상 판정의 심각도 척도가 달라 anomaly를 먼저 본다. */
    boolean meetsCondition(Decision decision) {
        return decision.isAnomaly() && decision.getSeverity() >= properties.minSeverity();
    }

    private Outcome upsert(Decision decision, Instant now) {
        Equipment equipment = decision.getReading().getEquipment();
        int merged = alertRepository.recordOccurrence(
                equipment.getId(), decision.getCategory(), AlertStatus.UNRESOLVED, now);
        if (merged > 0) {
            return Outcome.MERGED;
        }
        alertRepository.saveAndFlush(Alert.open(decision, equipment, now));
        return Outcome.CREATED;
    }

    @Transactional
    public Alert acknowledge(long id) {
        Alert alert = alertRepository.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
        alert.acknowledge(Instant.now(clock));
        return alert;
    }

    @Transactional
    public Alert resolve(long id) {
        Alert alert = alertRepository.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
        alert.resolve(Instant.now(clock));
        return alert;
    }
}
