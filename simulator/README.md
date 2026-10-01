# simulator

AI4I CSV 행을 설비 N대가 일정 간격으로 보내는 센서 스트림처럼 재생해 `POST /api/readings`로 전송한다.

## 준비

```bash
cd simulator
python3.11 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

backend와 DB가 떠 있어야 한다 (`docker compose up -d` 후 `backend/`에서 `./gradlew bootRun`).

## 실행

```bash
# 기본: 전체 10,000행, 설비 5대 × 1초 간격 (초당 5건)
.venv/bin/python simulator.py

# 테스트셋(2,000행)만 최대 속도로
.venv/bin/python simulator.py --split test --interval 0

# 앞 20행만
.venv/bin/python simulator.py --limit 20
```

| 옵션 | 기본값 | 설명 |
|---|---|---|
| `--base-url` | `http://localhost:8080` (환경변수 `SIMULATOR_BASE_URL`) | backend 주소 |
| `--split` | `all` | `all` / `train` / `test` (`data/split.csv` 기준) |
| `--limit` | 없음 | 앞에서부터 N행만 |
| `--equipment` | 5 | 설비 수. 행 i → `EQ-{(i % N) + 1}` |
| `--interval` | 1.0 | 틱 간격(초). 한 틱에 설비마다 1건. 0이면 최대 속도 |
| `--timeout` | 5.0 | 요청 타임아웃(초) |
| `--max-retries` | 3 | 연결 실패·5xx 시 최대 시도 횟수 |

## 실패 처리와 종료 코드

- 연결 실패 / 타임아웃 / 5xx → 지수 백오프로 재시도, 모두 실패하면 중단 (exit 1)
- 4xx (검증 실패, 미등록 설비) → 경고 로그를 남기고 계속 진행, 끝나면 exit 2
- 모두 성공 → exit 0, Ctrl+C → 요약 출력 후 exit 130

## 주의

- 설비별 시계열이 아니라, 서로 독립인 행을 순서대로 재생하는 것이다 (데이터 한계, README 참고).
- 정답 라벨(`labels`)과 원본 UDI(`sourceUdi`)를 함께 보낸다. 벤치마크 전용 필드다 (`docs/decisions.md` D-009).
