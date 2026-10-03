# 설계 결정 기록

중요한 기술 선택과 그 이유를 남긴다. 형식: 결정 / 선택지 / 이유 / 트레이드오프.

---

## D-001. Python 환경 관리: venv (2026-09-26)

- **선택지**: venv / uv / conda
- **결정**: `venv` + Homebrew Python 3.11
- **이유**: 추가 도구 설치 없이 표준 라이브러리만으로 충분하고, `requirements.txt`가 Docker 이미지 빌드와 그대로 연결된다.
- **트레이드오프**: lock 파일이 없어 간접 의존성 버전이 고정되지 않는다 → 주요 패키지는 버전을 명시한다.
- **메모**: 로컬 pyenv 기본값이 3.7이므로 venv 생성 시 `python3.11 -m venv`로 버전을 명시한다.

## D-002. DB: SQL Server (2026-09-26)

- **선택지**: SQL Server 2022 (Docker) / PostgreSQL / Azure SQL Edge
- **결정**: SQL Server 2022
- **이유**: 제조업 IT 현장(MES 등)에서 많이 쓰이는 DB라 타깃 직무와 연결된다.
- **제외**: Azure SQL Edge는 2025년 9월 지원 종료.
- **트레이드오프**: 공식 이미지가 amd64 전용이라 Apple Silicon에서는 Docker Desktop의 Rosetta 에뮬레이션이 필요하다. 로컬 성능 측정 시 이 점을 감안한다.

## D-003. 평가 정답 라벨: `machineFailure` (2026-09-29)

- **선택지**: `machineFailure` 컬럼 / 유형 라벨(TWF~RNF) 중 하나라도 1이면 고장
- **결정**: `machineFailure`
- **이유**: 현장의 "설비가 실제로 멈췄는가"에 해당하고, DB 스키마의 `actual_failure`와 같은 의미다.
  유형 라벨 기준으로 하면 `machineFailure = 0`인 RNF 18건이 정답에 섞여, 원리상 예측 불가능한 고장이 엔진 간 차이를 흐린다.
- **트레이드오프**: 유형 라벨 없는 고장 9건은 어떤 엔진도 원인을 설명할 수 없어 재현율 상한이 100%보다 낮다. 오히려 현실적이다.

## D-004. 룰 엔진의 TWF 처리: 경고 수준 판정 (2026-09-29)

- **선택지**: `toolWear ≥ 200`이면 낮은 심각도로 이상 판정 / 룰 엔진에서 TWF 제외
- **결정**: `toolWear ≥ 200` → TWF, 심각도 40 (HDF·PWF·OSF는 90)
- **이유**: TWF는 200~240분 구간에서 무작위로 발생해 룰로는 "위험 구간 진입"까지만 알 수 있다.
  제외하면 TWF를 전부 놓치고, 포함하되 심각도를 낮추면 알람 기준(심각도 임계값)으로 재현율과 정밀도를 조절할 수 있다.
- **트레이드오프**: 경고까지 이상으로 보면 오탐이 크게 늘어난다 (전체 기준 FP 678건, 정밀도 0.33).
- **근거**: `ml-server/notebooks/03_rule_engine.ipynb`
- **미정**: 심각도 값(90 / 40)은 1차 값이며 Phase 4 알람 임계값과 함께 확정한다.

## D-005. 테스트셋 고정: 층화 20%, seed 42 (2026-09-29)

- **결정**: `machineFailure` 비율을 유지하는 층화 분할, test 20% (2,000행, 고장 68건), `random_state = 42`
- **이유**: 세 엔진을 같은 테스트셋으로 비교하고(원칙 3), ML이 테스트셋을 학습에 쓰지 않도록 ML 학습 전에 분할을 파일로 고정한다.
- **산출물**: `data/split.csv` (`udi`, `split`), 생성 스크립트 `ml-server/scripts/make_split.py`
- **트레이드오프**: test 고장이 68건뿐이라 재현율 1건 차이가 약 1.5%p다. 결과 해석 시 전체 데이터 수치를 함께 본다.

## D-006. 백엔드 프로젝트 기본 구성 (2026-09-29)

- **Spring Boot**: 4.1.1 (선택지: 4.x 최신 / 3.5.x로 내려서 사용).
  처음에는 가이드 기준인 3.5.x로 정했으나, 3.5의 OSS 지원이 끝나 start.spring.io에서 선택할 수 없었다.
  보안 패치가 계속 나오는 버전을 쓰기 위해 4.x로 변경했고, start.spring.io 기본값인 4.1.1을 그대로 쓴다. Java 17은 그대로 지원된다.
- **Gradle**: 9.7.1 (Wrapper, start.spring.io 생성값)
  3.x 대비 달라진 점(starter 이름 일부 변경, Jackson 3 등)은 작업하면서 이 항목에 추가한다.
- **빌드 스크립트**: Gradle Groovy DSL `build.gradle` (선택지: Groovy / Kotlin DSL). 국내 자료와 예시 대부분이 Groovy다.
- **패키지**: `io.github.min27.factoryanomaly`. GitHub 계정(`27min`) 기반이며, Java 패키지는 숫자로 시작할 수 없어 `min27`로 조정했다.
- **Lombok**: 사용. JPA 엔티티는 record로 만들 수 없어 보일러플레이트가 많다.
  단, 엔티티에는 `@Getter`, `@NoArgsConstructor(access = PROTECTED)` 정도만 쓰고 `@Setter`, `@Data`는 쓰지 않는다 (무분별한 상태 변경과 `equals/hashCode` 문제 방지).
  DTO는 Java record를 쓴다.

## D-007. DB 설정 방식: application.yml + Flyway + 초기화 컨테이너 (2026-09-29)

- **설정 파일**: `application.yml` (선택지: yml / properties). 설정이 계층적으로 늘어나도(DB, JPA, 엔진 선택) 읽기 쉽다.
- **스키마 관리**: Flyway + `ddl-auto: validate` (선택지: Flyway / `ddl-auto=update`).
  DDL을 직접 써서 SQL Server 타입과 인덱스를 의도대로 정하고, 변경 이력을 파일로 남긴다. JPA는 엔티티와 스키마가 맞는지만 검사한다.
  Boot 4에서는 `spring-boot-starter-flyway`와 DB별 모듈 `flyway-sqlserver`가 필요하다.
- **DB 생성**: compose의 `db-init` 컨테이너 (선택지: 초기화 컨테이너 / 수동 생성).
  SQL Server 이미지는 환경변수로 DB를 만들어주지 않는다. `db`가 healthy가 되면 `sqlcmd`로 `CREATE DATABASE`를 한 번 실행하고 종료한다 (이미 있으면 건너뜀).
- **비밀값**: 레포 루트 `.env` (git 제외, 예시는 `.env.example`). compose는 `.env`를 자동으로 읽고,
  Spring은 `spring.config.import: optional:file:../.env[.properties]`로 같은 파일을 읽는다 → 비밀번호를 한 곳에서만 관리.
- **트레이드오프**: 로컬 개발 편의를 위해 앱이 `sa` 계정으로 접속한다. 운영이라면 앱 전용 계정과 최소 권한이 필요하다.

## D-008. 엔티티 / 스키마 설계 (2026-09-29)

- **제품 타입 위치**: `equipment`가 아니라 `sensor_reading.product_type` (선택지: 측정값에 둠 / 설비에 고정).
  데이터에서 제품 타입은 행마다 다르다. 제품 타입은 "그 순간 가공 중인 제품의 등급"이므로 측정의 속성으로 본다.
  설비에 고정하면 시뮬레이터가 타입별로 행을 나눠 보내야 해서 원본 분포(L 60% / M 30% / H 10%)를 재생하기 어렵다.
- **정답 라벨**: `machine_failure`, `twf`, `hdf`, `pwf`, `osf`, `rnf`를 각각 BIT 컬럼으로, NULL 허용 (선택지: 유형별 컬럼 / 대표 유형 하나).
  여러 유형이 동시에 발생한 24행을 손실 없이 저장한다. 현장에서는 사후에 채워지는 값이라 NULL을 허용한다.
  JPA에서는 `@Embeddable record FailureLabels`로 묶는다.
- **설비 등록**: Flyway `V2`로 5대(EQ-01~05)를 미리 등록하고, 등록되지 않은 설비의 측정값은 거부한다 (선택지: 미리 등록 / 자동 생성).
- **기술 기본값**: ID는 `BIGINT IDENTITY`, 시간은 `Instant` ↔ `DATETIMEOFFSET(6)`(UTC), enum은 문자열 저장, 패키지는 도메인별(equipment / reading / decision / alert).
- **추가한 것**
  - `sensor_reading.source_udi`: 원본 CSV의 UDI. 벤치마크에서 `data/split.csv`와 연결해 test 행만 집계하기 위함.
  - `decision (reading_id, engine)` 유니크: 같은 측정값을 같은 엔진이 두 번 판정해 중복 저장되는 것을 막는다.
  - `alert.equipment_id`: `decision → reading → equipment`로도 찾을 수 있지만 설비별 알람 조회가 잦아 직접 참조한다.
- **미정**: `decision.category`는 우선 단일 값. 룰 엔진은 여러 조건이 동시에 맞을 수 있으므로 Phase 3에서 다시 정한다 (바뀌면 새 마이그레이션 추가).
- **메모**: Hibernate는 `length = 1` 문자열을 `CHAR(1)`로 기대한다. 처음에 `VARCHAR(1)`로 만들었다가 `ddl-auto: validate`가 불일치를 잡아냈다.

## D-009. 수집 API 설계 (2026-09-29)

- **정답 라벨 수신**: 같은 `POST /api/readings`의 선택 필드 `labels`, `sourceUdi` (선택지: 선택 필드 / 라벨을 나중에 채우는 별도 API).
  시뮬레이션·벤치마크 전용이며 실제 설비는 보내지 않는다. 현장이라면 고장 확인 후 라벨을 채우는 별도 API가 맞다.
- **성공 응답**: `201 Created` + `Location: /api/readings/{id}` + 저장된 파생변수 (선택지: 201 / 202).
  동기로 저장까지 끝낸 뒤 응답하므로 202(비동기 수락)는 의미가 맞지 않는다. `Location`이 실제 주소를 가리키도록 `GET /api/readings/{id}`도 둔다.
- **입력 검증 범위**: 물리적으로 불가능한 값만 거부한다 — 온도 200~500 K, 회전수·토크·공구마모 0 이상 (선택지: 물리적 범위 / 데이터셋 최소~최대).
  데이터셋 범위로 막으면 진짜 이상값이 판정 엔진에 도달하기 전에 걸러져 이상탐지 시스템의 목적과 충돌한다.
  숫자 필드는 래퍼 타입 + `@NotNull`로 누락을 잡는다 (기본형이면 누락 시 0으로 들어가 정상값처럼 보인다).
- **에러 형식**: RFC 9457 `ProblemDetail` (선택지: ProblemDetail / 자체 ErrorResponse). 검증 실패 시 `errors: [{field, message}]`를 추가한다.
  - 400: 검증 실패, JSON 형식 오류, 알 수 없는 제품 타입
  - 422: 형식은 맞지만 등록되지 않은 설비
  - 404: 없는 측정값 조회
- **시간**: `Clock`을 Bean으로 두고 `Instant.now(clock)`로 수신 시각을 정한다. 테스트에서 고정 시각으로 바꿔 검증한다.

## D-010. 시뮬레이터 설계 (2026-09-29)

- **재생 순서**: 원본 CSV 순서 그대로, `--split`(all / train / test)과 `--limit`으로 범위 선택 (선택지: 원래 순서 / 섞어서).
  실행할 때마다 같은 순서로 재생되어 결과를 재현할 수 있다.
- **설비 배분과 속도**: 라운드 로빈(행 i → `EQ-{(i % 5) + 1}`), 틱마다 설비 5대가 1건씩, 기본 1초 간격 (선택지: 라운드 로빈 + 틱 / 설비별 스레드).
  `--interval 0`이면 최대 속도로 보낸다 (로컬 측정: 약 85건/초).
- **Python 환경**: `simulator/.venv`에 `requests`만 설치 (선택지: 전용 venv / ml-server venv 공유). Docker 이미지로 만들 때 가볍다.
- **실패 처리**: 연결 실패·5xx는 재시도 후 중단, 4xx는 기록 후 계속. 서버가 없는데 계속 보내는 것은 의미가 없고, 한 행의 입력 오류로 전체 재생을 멈출 필요는 없다.

## D-011. 판정 인터페이스 세부 (2026-10-01)

- **응답시간 측정 위치**: 호출하는 쪽 (선택지: 호출하는 쪽 / 엔진 내부).
  엔진은 판정만 반환하고, 엔진을 호출하는 서비스가 `decide()` 전후 시간을 재서 `decision.latency_ms`에 저장한다.
  세 엔진을 같은 구간으로 재야 비교가 공정하고(원칙 3), 엔진마다 측정 코드를 반복하지 않는다.
  그래서 가이드 4.2와 달리 `DecisionResult`에는 `latencyMs`가 없다.
- **category**: 대표 유형 1개 (선택지: 대표 1개 / 복수 저장). D-008의 미정 항목을 이것으로 정한다.
  룰 엔진은 여러 조건이 동시에 맞으면 심각도가 가장 높은 유형을 고르고, 같으면 HDF > PWF > OSF > TWF 순으로 정한다.
  ML 다중분류 출력과 형태가 같아 엔진 간 비교와 집계 SQL이 단순하다.
  트레이드오프: 동시에 맞은 나머지 조건은 저장되지 않는다 (전체 데이터에서 여러 유형이 동시에 발생한 고장은 24행).
- **룰 엔진 confidence**: 1.0 고정 (선택지: 고정 / 유형별 차등). 룰은 결정적이라 확률 개념이 없고, 임의의 차등 값은 근거가 없다.
  confidence는 ML·Jev 엔진에서만 의미가 있다.
- **불변식**: `DecisionResult` 생성 시 severity 0~100, confidence 0~1(DB CHECK 제약과 동일), `anomaly == (category != NORMAL)`을 검사한다.
  잘못된 값은 DB에 닿기 전에 엔진 쪽 버그로 드러난다.

## D-012. 룰 엔진 설정 위치: 임계값은 코드, 심각도는 설정 (2026-10-01)

- **선택지**: 임계값 코드 + 심각도 yml / 모두 코드 상수 / 모두 yml
- **결정**: 고장 조건 임계값(8.6 K, 1380 rpm, 3500/9000 W, OSF 11000/12000/13000, TWF 200분)은 `RuleEngine`의 상수,
  심각도(90/40)는 `application.yml`의 `engine.rule.failure-severity` / `warning-severity` (`RuleEngineProperties`)
- **이유**: 임계값은 데이터셋이 정의한 물리 조건이라 바뀔 일이 없고, 설정으로 열어두면 잘못 바꿔 벤치마크가 오염될 수 있다.
  심각도는 Phase 4에서 알람 임계값과 함께 조정할 값이라(D-004 미정) 재빌드 없이 바꿀 수 있게 한다.
- **검증**: 심각도는 0 초과 100 이하만 허용하고, 벗어나면 애플리케이션 시작 시점에 실패한다 (`@Validated`).
  0을 막는 이유: 이상 판정인데 심각도 0이면 알람 임계값과 의미가 어긋난다.
- **이식 검증**: `RuleEngineTest`가 전체 10,000행을 Java 엔진으로 판정해 노트북의 혼동행렬과 정확히 일치하는지 확인한다
  (경고 포함 TP 330 / FP 678 / FN 9, 알람만 TP 287 / FP 0 / FN 52).

## D-013. 수집과 판정의 연결: 동기, 트랜잭션 분리 (2026-10-01)

- **처리 방식**: 같은 요청 안에서 동기로 처리하되, 측정값 저장과 판정 저장을 다른 트랜잭션으로 나눈다
  (선택지: 동기·트랜잭션 분리 / 비동기 이벤트 / 동기·단일 트랜잭션).
  측정값을 먼저 커밋한 뒤 `DecisionService`가 엔진마다 판정하고, 판정을 하나씩 저장한다 (`ReadingService.ingest`에는 `@Transactional`이 없다).
  - 단일 트랜잭션이면 엔진이 실패할 때 측정값까지 롤백되고, 외부 HTTP 호출 동안 DB 커넥션을 잡고 있게 된다.
  - 비동기는 수집 응답이 빠르지만 스레드풀·테스트가 복잡해지고, 서버가 죽으면 판정이 유실되며, 응답에 판정을 담을 수 없다.
  - 트레이드오프: 느린 엔진(ML·Jev)의 응답시간이 수집 API 응답시간에 더해진다 → 엔진별 타임아웃으로 상한을 둔다 (MlEngine에서 정함).
- **엔진 실패**: 해당 엔진의 판정만 저장하지 않고 경고 로그를 남긴다. 측정값과 다른 엔진의 판정은 남고, 요청은 201로 끝난다.
  실패 자체를 DB에 기록할지(실패율도 비교 지표)는 실제로 실패할 수 있는 MlEngine을 만들 때 정한다.
- **실행 엔진**: 지금은 등록된 모든 `DecisionEngine` Bean을 이름 순서로 실행한다. 설정값으로 엔진을 고르는 기능은 Phase 3 후반에 추가한다.
- **응답**: `POST /api/readings`와 `GET /api/readings/{id}` 응답에 `decisions` 배열을 추가했다.
- **검증**: `EngineFailureIntegrationTest`가 항상 실패하는 엔진을 끼워 넣고, 실제 DB에 측정값과 룰 판정이 커밋되는지 확인한다.

## D-014. 응답시간 단위: 마이크로초 (2026-10-01)

- **선택지**: 마이크로초로 변경 / 밀리초 유지하고 "1ms 미만"으로 표기
- **결정**: `decision.latency_ms` → `latency_us` (Flyway `V3`, 기존 값은 ×1000), 측정은 `TimeUnit.NANOSECONDS.toMicros`
- **이유**: 룰 엔진은 1ms 안에 끝나 밀리초로는 항상 0이 저장됐다. 응답시간 비교가 이 프로젝트의 핵심 지표라
  "0ms"가 아니라 실제 값으로 엔진 간 차이를 보여야 한다. 로컬 측정: 룰 엔진 200건 평균 13µs (첫 요청들은 JIT 워밍업으로 수백 µs).
- **트레이드오프**: 가이드 4.2·4.3의 `latencyMs` / `latency_ms` 표기와 달라진다. 이 항목과 D-011이 기준이다.

## D-015. ML 엔진 모델 (2026-10-01)

- **모델**: scikit-learn `HistGradientBoostingClassifier` (선택지: HistGradientBoosting / LightGBM / 여러 모델 비교).
  LightGBM과 같은 히스토그램 기반 부스팅이면서 추가 의존성(libomp 등)이 없고 class_weight를 지원한다. 1만 행 규모에서 성능 차이는 거의 없다.
- **예측 대상**: 다중분류 1개 모델, 클래스 NORMAL / HDF / PWF / OSF / TWF (선택지: 다중분류 / 이진분류 / 2단계).
  룰 엔진과 같은 형태(이상 여부, 심각도, 유형)로 답하기 위함. 라벨은 `machineFailure = 0`이면 NORMAL,
  고장이면 유형 라벨을 D-011 우선순위로 하나 고른다. 유형 라벨이 없는 고장(train 7행)은 학습에서만 제외하고 평가에는 남긴다.
  판정: 고장 확률 `p = 1 − P(NORMAL)`, `anomaly = p ≥ 임계값`, `severity = 100 × p`, `category` = 고장 클래스 중 확률 최대.
- **하이퍼파라미터**: `learning_rate=0.1`, `l2_regularization=1.0`. 기본값(규제 없음)은 파생변수를 넣으면 학습이 발산했다
  (학습 데이터 log loss 1.3~4.8, fold별 AP 0.03~0.12). 발산한 채로 비교했다면 "불균형 처리를 안 하면 망가진다"는 틀린 결론이 나왔다.
- **피처 / 불균형 처리**: 원본 + 파생변수, `class_weight="balanced"` (비교: 피처 2종 × 없음 / class_weight / SMOTE, train 5-fold OOF).
  - 파생변수: AP 0.82~0.83 → 0.90~0.91. fold 표준편차(0.02~0.03)보다 훨씬 큰 차이
  - 불균형 처리: 같은 피처 안에서 AP 차이 0.015 이하로 fold 표준편차보다 작다. class_weight가 AP·F1 최고이고, SMOTE와 달리 데이터를 바꾸지 않으며 추가 라이브러리도 필요 없다
- **임계값**: train OOF에서 F1 최대가 되는 고장 확률 0.954. class_weight 때문에 확률이 보정되어 있지 않아 임계값이 높다.
  `severity`를 룰 엔진 심각도와 같은 척도로 보면 안 된다 → Phase 4 알람 임계값에서 엔진별로 다시 본다.
- **test 결과 (한 번만 평가)**: 정밀도 1.000 / 재현율 0.838 / F1 0.912. **룰 B(알람만)와 2,000행 모두 같은 판정**이다.
  합성 데이터의 공개된 고장 조건을 그대로 학습했기 때문이다. TWF는 ML도 못 잡는다 (교차검증·test에서 TWF로 고장 판정한 적 없음).
- **모델 파일**: `scripts/train.py`가 `ml-server/models/model.joblib`(약 0.9 MB)과 `model_meta.json`(피처 순서, 클래스, 임계값, CV 지표, sklearn 버전)을 만들고 커밋한다
  (선택지: 스크립트 생성 + 커밋 / 노트북에서 저장 / 빌드 시 학습). 같은 입력이면 같은 파일이 나온다 (MD5 일치 확인).
  피처·라벨 정의는 `app/features.py` 한 곳에 두고 노트북·학습 스크립트·추론 서버가 같이 쓴다.
- **근거**: `ml-server/notebooks/04_ml_training.ipynb`
