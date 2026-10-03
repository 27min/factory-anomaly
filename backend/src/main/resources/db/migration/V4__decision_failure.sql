-- 엔진 판정 실패 기록 (D-017)
--   decision에는 유효한 판정만 남겨 기존 제약과 집계를 그대로 두고, 실패는 따로 쌓는다
--   벤치마크에서 엔진별 실패율 = 실패 건수 / 측정값 건수
CREATE TABLE decision_failure (
    id           BIGINT IDENTITY(1,1) NOT NULL,
    reading_id   BIGINT               NOT NULL,
    engine       VARCHAR(20)          NOT NULL,
    reason       VARCHAR(20)          NOT NULL,
    message      VARCHAR(500)         NULL,
    latency_us   BIGINT               NOT NULL,
    failed_at    DATETIMEOFFSET(6)    NOT NULL,
    CONSTRAINT pk_decision_failure PRIMARY KEY (id),
    CONSTRAINT fk_decision_failure_reading FOREIGN KEY (reading_id) REFERENCES sensor_reading (id),
    CONSTRAINT uq_decision_failure_reading_engine UNIQUE (reading_id, engine),
    CONSTRAINT ck_decision_failure_reason
        CHECK (reason IN ('TIMEOUT', 'CONNECTION', 'HTTP_ERROR', 'INVALID_RESPONSE', 'UNEXPECTED'))
);
CREATE INDEX ix_decision_failure_engine_failed ON decision_failure (engine, failed_at);
