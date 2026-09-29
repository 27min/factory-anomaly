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
