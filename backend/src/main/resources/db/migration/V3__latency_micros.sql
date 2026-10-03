-- 응답시간 단위를 밀리초 → 마이크로초로 변경 (D-014)
-- 룰 엔진은 1ms 미만에 끝나 밀리초로는 항상 0이 저장되어 엔진 간 비교가 불가능했다
EXEC sp_rename 'decision.latency_ms', 'latency_us', 'COLUMN';
GO

UPDATE decision SET latency_us = latency_us * 1000;
