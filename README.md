# factory-anomaly

제조 설비 센서 스트림을 실시간으로 받아 이상을 판정하고, 판정 엔진(룰 / ML / 외부 API)을 교체해 같은 조건에서 비교하는 Spring Boot 백엔드 시스템.

> 🚧 작업 중입니다. 진행 계획은 [PROJECT_GUIDE.md](PROJECT_GUIDE.md)를 참고하세요.

## 구조

| 폴더 | 내용 |
|---|---|
| `backend/` | Spring Boot 수집 API, 판정 엔진 |
| `ml-server/` | FastAPI 추론 서버, EDA/학습 노트북 |
| `simulator/` | CSV를 센서 스트림처럼 재생하는 전송기 |
| `data/` | AI4I 2020 데이터셋 |
| `docs/` | 아키텍처, 설계 결정, 벤치마크 결과 |

## 데이터

**AI4I 2020 Predictive Maintenance Dataset**
Matzka, S. (2020). UCI Machine Learning Repository. https://doi.org/10.24432/C5HS5C
License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)

## 한계점

- 합성 데이터라 고장 조건이 수식으로 정해져 있어, 룰 엔진이 비현실적으로 좋은 성능을 낼 수 있음
- 설비별 시계열 데이터가 아니며, 시뮬레이터는 행 단위 데이터를 스트림처럼 재생함
- RNF(무작위 고장)는 원리상 어떤 엔진으로도 예측 불가
