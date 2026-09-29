-- 설비
CREATE TABLE equipment (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    code        VARCHAR(20)          NOT NULL,
    name        NVARCHAR(100)        NOT NULL,
    created_at  DATETIMEOFFSET(6)    NOT NULL,
    CONSTRAINT pk_equipment PRIMARY KEY (id),
    CONSTRAINT uq_equipment_code UNIQUE (code)
);

-- 센서 측정값 + 파생변수 + 정답 라벨
--   product_type: 측정 시점에 가공 중인 제품의 등급 (설비가 아니라 측정의 속성, D-008)
--   machine_failure ~ rnf: 정답 라벨. 현장에서는 사후에 채워지므로 NULL 허용
--   source_udi: 원본 CSV의 UDI. 벤치마크 시 data/split.csv와 연결하기 위함
CREATE TABLE sensor_reading (
    id               BIGINT IDENTITY(1,1) NOT NULL,
    equipment_id     BIGINT               NOT NULL,
    product_type     CHAR(1)              NOT NULL,
    air_temp         FLOAT                NOT NULL,
    process_temp     FLOAT                NOT NULL,
    rot_speed        INT                  NOT NULL,
    torque           FLOAT                NOT NULL,
    tool_wear        INT                  NOT NULL,
    temp_diff        FLOAT                NOT NULL,
    power            FLOAT                NOT NULL,
    wear_torque      FLOAT                NOT NULL,
    machine_failure  BIT                  NULL,
    twf              BIT                  NULL,
    hdf              BIT                  NULL,
    pwf              BIT                  NULL,
    osf              BIT                  NULL,
    rnf              BIT                  NULL,
    source_udi       INT                  NULL,
    received_at      DATETIMEOFFSET(6)    NOT NULL,
    CONSTRAINT pk_sensor_reading PRIMARY KEY (id),
    CONSTRAINT fk_sensor_reading_equipment FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT ck_sensor_reading_product_type CHECK (product_type IN ('L', 'M', 'H'))
);
CREATE INDEX ix_sensor_reading_equipment_received ON sensor_reading (equipment_id, received_at DESC);

-- 엔진별 판정 결과
CREATE TABLE decision (
    id           BIGINT IDENTITY(1,1) NOT NULL,
    reading_id   BIGINT               NOT NULL,
    engine       VARCHAR(20)          NOT NULL,
    anomaly      BIT                  NOT NULL,
    severity     FLOAT                NOT NULL,
    category     VARCHAR(20)          NOT NULL,
    confidence   FLOAT                NOT NULL,
    latency_ms   BIGINT               NOT NULL,
    decided_at   DATETIMEOFFSET(6)    NOT NULL,
    CONSTRAINT pk_decision PRIMARY KEY (id),
    CONSTRAINT fk_decision_reading FOREIGN KEY (reading_id) REFERENCES sensor_reading (id),
    CONSTRAINT uq_decision_reading_engine UNIQUE (reading_id, engine),
    CONSTRAINT ck_decision_severity CHECK (severity BETWEEN 0 AND 100),
    CONSTRAINT ck_decision_confidence CHECK (confidence BETWEEN 0 AND 1)
);
CREATE INDEX ix_decision_engine_decided ON decision (engine, decided_at);

-- 알람
CREATE TABLE alert (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    decision_id   BIGINT               NOT NULL,
    equipment_id  BIGINT               NOT NULL,
    severity      FLOAT                NOT NULL,
    status        VARCHAR(20)          NOT NULL,
    created_at    DATETIMEOFFSET(6)    NOT NULL,
    CONSTRAINT pk_alert PRIMARY KEY (id),
    CONSTRAINT fk_alert_decision FOREIGN KEY (decision_id) REFERENCES decision (id),
    CONSTRAINT fk_alert_equipment FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT uq_alert_decision UNIQUE (decision_id)
);
CREATE INDEX ix_alert_equipment_created ON alert (equipment_id, created_at DESC);
