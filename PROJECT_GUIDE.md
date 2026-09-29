# factory-anomaly — 제조 설비 실시간 이상탐지 시스템

> 이 문서는 프로젝트의 기준 문서다. 레포 루트에 두고, AI 코딩 도구(Claude Code, Cursor 등)를 쓸 때는 매 세션 시작 시 이 파일을 먼저 읽히게 한다.
> Claude Code를 쓴다면 이 파일 내용을 레포 루트의 `CLAUDE.md`로 복사하면 자동으로 읽힌다.

---

## 0. 시작 프롬프트 (AI 코딩 도구에 첫 메시지로 붙여넣기)

```
레포 루트의 PROJECT_GUIDE.md를 먼저 끝까지 읽어줘.
이 프로젝트는 제조 설비 센서 데이터를 실시간으로 받아 이상을 판정하는
Spring Boot 백엔드 포트폴리오 프로젝트야.

지금은 "5. 단계별 작업 계획"의 Phase 1을 진행할 거야.
작업 규칙:
- 한 번에 체크리스트 한 항목씩 진행하고, 끝나면 무엇을 했는지 요약한 뒤 다음 항목으로 넘어가기 전에 나한테 확인받아.
- 코드를 작성하기 전에 어떤 파일을 만들거나 수정할지 먼저 알려줘.
- 설계 결정이 필요한 부분(라이브러리 선택, 구조 변경 등)은 임의로 정하지 말고 선택지와 장단점을 알려줘.
- "7. 지켜야 할 원칙"을 항상 따라줘.
- 커밋 단위가 될 만한 지점에서 커밋 메시지를 제안해줘.

먼저 현재 레포 상태를 확인하고, Phase 1 체크리스트 중 어디서부터 시작하면 될지 알려줘.
```

다음 Phase로 넘어갈 때는 첫 줄의 Phase 번호만 바꿔서 쓰면 된다.

---

## 1. 프로젝트 정의

**한 줄 요약**: 센서 스트림을 받아 실시간으로 설비 이상을 판정하고, 판정 엔진(룰 / ML / System One 모델)을 교체해 같은 조건에서 비교할 수 있는 Spring Boot 기반 백엔드 시스템.

**보여주고 싶은 역량**
- 백엔드 설계: 수집 API, Strategy 패턴 기반 엔진 교체 구조, DB 설계
- 제조 도메인 이해: 물리적 의미가 있는 파생변수 설계 (기계공학 배경 활용)
- 기술 판단력: 세 가지 판정 방식을 정확도, 응답시간, 비용으로 비교하고 상황별 선택 기준 제시

**타깃**: 백엔드 개발 직무, 제조업 IT/전산 직무 공통

---

## 2. 기술 스택

| 영역 | 기술 |
|---|---|
| 백엔드 | Java 17, Spring Boot 4.1.x, Spring Data JPA, Gradle (Groovy DSL) |
| DB | SQL Server 2022 (Docker) ※ 맥 M칩은 Docker Desktop의 Rosetta 에뮬레이션 사용 (`docs/decisions.md` D-002) |
| ML 서버 | Python 3.11, FastAPI, scikit-learn / LightGBM |
| 시뮬레이터 | Python (requests) |
| 대시보드 | **미정** — Thymeleaf / React / Grafana 중 Phase 4 시작 전 결정 |
| 인프라 | Docker Compose |
| 외부 API | Jev (TypeSafe AI) — early access 키 확보 시에만 연동 |

---

## 3. 데이터

**AI4I 2020 Predictive Maintenance Dataset** (UCI ML Repository, CC BY 4.0)
- 출처: https://archive.ics.uci.edu/dataset/601
- 10,000행, 제품 타입 L/M/H, 센서값 5종, 고장 라벨 6종
- 고장 비율 약 3.4% (심한 불균형)

**컬럼**

| 원본 컬럼 | 코드 내 이름 | 단위 |
|---|---|---|
| Type | productType | L/M/H |
| Air temperature [K] | airTemp | K |
| Process temperature [K] | processTemp | K |
| Rotational speed [rpm] | rotSpeed | rpm |
| Torque [Nm] | torque | Nm |
| Tool wear [min] | toolWear | min |
| Machine failure | machineFailure | 0/1 |
| TWF / HDF / PWF / OSF / RNF | 고장 유형별 라벨 | 0/1 |

**공개된 고장 발생 조건** (룰 엔진의 근거)
- **HDF (열방출)**: 온도차(공정−대기) < 8.6 K 이고 회전속도 < 1380 rpm
- **PWF (전력)**: 전력 < 3500 W 또는 > 9000 W
- **OSF (과부하)**: 공구마모 × 토크 > 11,000 (L) / 12,000 (M) / 13,000 (H) min·Nm
- **TWF (공구마모)**: 공구마모 200~240분 구간에서 무작위 발생
- **RNF (무작위)**: 약 0.1% 확률로 무작위 발생 → 어떤 엔진도 예측 불가능한 것이 정상

**데이터의 한계 (README에 반드시 명시)**
- 합성 데이터라 고장 조건이 수식으로 정해져 있음 → 룰 엔진이 비현실적으로 잘 나올 수 있음
- 설비별 시계열이 아님 → 시뮬레이터는 행을 "스트림처럼 재생"하는 것

---

## 4. 아키텍처

```
[simulator]  CSV 행을 설비 N대가 일정 간격으로 보내는 것처럼 재생
     │ POST /api/readings
     ▼
[backend : Spring Boot]
     ├─ ReadingController      수집 API
     ├─ StateBuilder           파생변수 계산
     ├─ DecisionEngine (인터페이스)
     │    ├─ RuleEngine        공개된 고장 조건 기반
     │    ├─ MlEngine          ml-server 호출
     │    └─ JevEngine         Jev API 호출 (키 확보 시)
     ├─ AlertService           심각도 임계 초과 시 알람 생성
     └─ 판정 결과 + 응답시간 저장
     ▼
[MS-SQL]  ──►  [dashboard]
```

### 4.1 파생변수 (StateBuilder)

| 이름 | 계산식 | 물리적 의미 / 연결되는 고장 |
|---|---|---|
| tempDiff | processTemp − airTemp | 열이 빠져나갈 여유 → HDF |
| power | torque × rotSpeed × 2π / 60 (W) | 공정에 필요한 동력 → PWF |
| wearTorque | toolWear × torque | 마모된 공구에 걸리는 부하 → OSF |

면접 대비: 각 변수를 왜 만들었는지 물리적으로 설명할 수 있어야 한다.

### 4.2 판정 인터페이스

Jev의 질문 3종(예/아니오, 점수, 분류) 형태를 공통 응답으로 정의하고, 모든 엔진이 같은 형태로 답한다.

```java
public interface DecisionEngine {
    String name();                         // "rule", "ml", "jev"
    DecisionResult decide(SensorState state);
}

public record DecisionResult(
    boolean anomaly,        // 예/아니오
    double severity,        // 0~100 점수
    FailureType category,   // NORMAL, TWF, HDF, PWF, OSF, UNKNOWN
    double confidence,      // 0~1
    long latencyMs          // 판정에 걸린 시간
) {}
```

- 사용할 엔진은 `application.yml`의 설정값으로 선택
- 벤치마크 모드에서는 세 엔진을 모두 실행해 각각 저장

### 4.3 DB 스키마 (초안)

```
equipment       (id, product_type, created_at)
sensor_reading  (id, equipment_id, air_temp, process_temp, rot_speed, torque, tool_wear,
                 temp_diff, power, wear_torque, actual_failure, actual_failure_type, received_at)
decision        (id, reading_id, engine, anomaly, severity, category, confidence, latency_ms, decided_at)
alert           (id, decision_id, equipment_id, severity, status, created_at)
```

`actual_failure`를 저장하는 이유: 벤치마크 시 정답과 비교하기 위함 (실제 현장이라면 사후에 채워지는 값).

### 4.4 레포 구조

```
factory-anomaly/
├─ backend/            Spring Boot
├─ ml-server/          FastAPI + notebooks/ (EDA, 학습)
├─ simulator/          센서 재생기
├─ data/               ai4i2020.csv (용량 작으니 커밋, 출처 명시)
├─ docs/               아키텍처 그림, 벤치마크 결과
├─ docker-compose.yml
├─ PROJECT_GUIDE.md    (이 문서)
└─ README.md
```

---

## 5. 단계별 작업 계획

### Phase 1 — 환경 세팅 + 데이터 분석 (1주차)
- [ ] 노트북에 JDK 17, IntelliJ, Docker Desktop, Python 3.11 설치 확인
- [ ] GitHub 레포 생성, 위 폴더 구조 만들기, .gitignore 작성
- [ ] `data/`에 AI4I CSV 배치
- [ ] `ml-server/notebooks/01_eda.ipynb`: 분포, 결측치, 고장 비율, 제품 타입별 차이 확인
- [ ] 파생변수 3종 계산 후 고장 유형별로 분리되는지 시각화로 검증
- [ ] 룰 엔진 로직을 노트북에서 먼저 구현하고 정밀도/재현율 측정
- **완료 기준**: 파생변수가 고장 유형과 연결된다는 근거 그래프 + 룰 엔진 1차 성능 수치

### Phase 2 — 백엔드 골격 (2주차)
- [ ] Spring Boot 프로젝트 생성 (Web, JPA, Validation, MS-SQL Driver)
- [ ] docker-compose로 MS-SQL 컨테이너 실행, 연결 확인
- [ ] 엔티티 4종 + Repository 작성
- [ ] `POST /api/readings` 수집 API + 입력값 검증
- [ ] StateBuilder 구현 + 단위 테스트 (파생변수 계산값 검증)
- [ ] 시뮬레이터: CSV를 읽어 설비 5대 × 1초 간격으로 전송
- **완료 기준**: 시뮬레이터를 켜면 DB에 측정값과 파생변수가 쌓임

### Phase 3 — 판정 엔진 (3주차)
- [ ] DecisionEngine 인터페이스, DecisionResult 정의
- [ ] RuleEngine 구현 + 단위 테스트 (각 고장 조건 경계값 테스트)
- [ ] ml-server: 학습 노트북 작성 (불균형 처리: class_weight 또는 SMOTE 비교), 모델 저장
- [ ] ml-server: FastAPI `POST /predict` 엔드포인트
- [ ] MlEngine 구현 (HTTP 호출, 타임아웃과 실패 시 처리 포함)
- [ ] JevEngine 뼈대만 작성 (키 없으면 비활성화)
- [ ] 판정 결과와 응답시간을 decision 테이블에 저장
- **완료 기준**: 설정값만 바꿔 엔진 전환 가능, 모든 판정이 DB에 기록됨

### Phase 4 — 알람 + 대시보드 (4주차)
- [ ] 대시보드 방식 결정 (Thymeleaf / React / Grafana)
- [ ] AlertService: 심각도 임계값 초과 시 alert 생성 (같은 설비 연속 알람 억제 로직 고려)
- [ ] 대시보드: 설비별 최근 상태, 알람 목록, 엔진별 판정 결과
- [ ] `docker compose up` 한 줄로 전체 실행되게 정리
- **완료 기준**: 처음 보는 사람이 README만 보고 실행 가능

### Phase 5 — 벤치마크 + 문서화 (5주차)
- [ ] 벤치마크 모드로 전체 데이터(또는 테스트셋) 재생
- [ ] SQL로 엔진별 정밀도, 재현율, F1, 평균/p95 응답시간 집계
- [ ] 결과표와 해석을 `docs/benchmark.md`에 정리 (상황별 엔진 선택 기준 포함)
- [ ] README 작성: 문제 정의, 아키텍처 그림, 실행 방법, 벤치마크, 한계점
- [ ] 30초 데모 GIF 녹화
- [ ] (선택) 기술 블로그 글 작성

---

## 6. 확장 아이디어 (시간 남으면)
- 계층형 판정: 룰로 확실한 건 먼저 거르고, 애매한 구간만 ML/Jev에 넘기기
- ML 학습 시 ML 쪽에만 유리하지 않도록 train/test 분리 후 같은 테스트셋으로 세 엔진 비교
- 알람을 이메일/슬랙 웹훅으로 발송
- Jev 키 확보 시: 세 엔진 비용 비교 (호출당 비용 추정)

---

## 7. 지켜야 할 원칙
1. **실행해보지 않은 결과는 적지 않는다.** Jev를 연동하지 못했으면 "연동 구조 준비 완료"로만 표기한다.
2. **데이터 한계를 숨기지 않는다.** 합성 데이터, 시계열 아님, RNF는 예측 불가라는 점을 README에 쓴다.
3. **공정한 비교.** 세 엔진은 같은 테스트셋, 같은 지표로 평가한다. ML은 테스트셋을 학습에 쓰지 않는다.
4. **테스트 먼저 챙길 곳**: StateBuilder 계산식, RuleEngine 경계값, 엔진 실패 시 동작.
5. **커밋은 작게, 메시지는 명확하게.** 커밋 이력도 포트폴리오의 일부다.
6. **설계 결정은 기록한다.** 중요한 선택(DB, 대시보드, 불균형 처리 등)은 `docs/decisions.md`에 이유와 함께 남긴다. 면접 답변 재료가 된다.

---

## 8. 면접 대비 질문 리스트 (작업하면서 답을 채워둘 것)
- 왜 Strategy 패턴으로 엔진을 분리했나?
- 파생변수는 왜 이 세 가지인가? 물리적 의미는?
- 고장이 3.4%뿐인 불균형 데이터를 어떻게 다뤘나? 정확도(accuracy)를 지표로 안 쓴 이유는?
- 룰 엔진 성능이 가장 좋게 나왔다면, 그럼 ML은 왜 필요한가?
- 외부 판정 API가 느리거나 죽으면 시스템은 어떻게 동작하나?
- 실제 공장에 적용한다면 무엇이 달라져야 하나?
