-- 알람 확인·해결 시각 (D-022)
--   OPEN → ACKNOWLEDGED → RESOLVED, OPEN → RESOLVED. 상태와 시각이 어긋나지 않도록 CHECK로 묶는다
ALTER TABLE alert ADD
    acknowledged_at  DATETIMEOFFSET(6) NULL,
    resolved_at      DATETIMEOFFSET(6) NULL;
GO

ALTER TABLE alert ADD
    CONSTRAINT ck_alert_acknowledged_at CHECK (status <> 'ACKNOWLEDGED' OR acknowledged_at IS NOT NULL),
    CONSTRAINT ck_alert_resolved_at CHECK (
        (status = 'RESOLVED' AND resolved_at IS NOT NULL) OR (status <> 'RESOLVED' AND resolved_at IS NULL));

-- 대시보드의 미해결 알람 목록
CREATE INDEX ix_alert_status_last_occurred ON alert (status, last_occurred_at DESC);
