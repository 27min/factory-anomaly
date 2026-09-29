-- 시뮬레이터가 사용하는 설비 5대 (D-008: 미리 등록된 설비만 측정값을 받는다)
INSERT INTO equipment (code, name, created_at) VALUES
    ('EQ-01', N'가공 설비 1호기', SYSDATETIMEOFFSET()),
    ('EQ-02', N'가공 설비 2호기', SYSDATETIMEOFFSET()),
    ('EQ-03', N'가공 설비 3호기', SYSDATETIMEOFFSET()),
    ('EQ-04', N'가공 설비 4호기', SYSDATETIMEOFFSET()),
    ('EQ-05', N'가공 설비 5호기', SYSDATETIMEOFFSET());
