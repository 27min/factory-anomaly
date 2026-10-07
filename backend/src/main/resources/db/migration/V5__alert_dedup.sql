-- 같은 설비·같은 유형의 미해결 알람에 반복 발생을 합친다 (D-021)
--   V4까지는 알람을 만드는 코드가 없어 alert는 항상 비어 있으므로, 기존 행을 채우는 처리 없이 NOT NULL 컬럼을 추가한다
ALTER TABLE alert ADD
    category          VARCHAR(20)       NOT NULL,
    occurrence_count  INT               NOT NULL,
    last_occurred_at  DATETIMEOFFSET(6) NOT NULL;
GO

ALTER TABLE alert ADD
    CONSTRAINT ck_alert_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    CONSTRAINT ck_alert_category CHECK (category <> 'NORMAL'),
    CONSTRAINT ck_alert_occurrence_count CHECK (occurrence_count >= 1);

-- 설비·유형마다 미해결 알람은 하나뿐이다. 동시에 들어온 요청이 둘 다 새 알람을 만들려 해도 DB가 막는다
CREATE UNIQUE INDEX uq_alert_unresolved ON alert (equipment_id, category)
    WHERE status IN ('OPEN', 'ACKNOWLEDGED');
