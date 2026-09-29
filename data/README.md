# data

## ai4i2020.csv

- **데이터셋**: AI4I 2020 Predictive Maintenance Dataset
- **출처**: Matzka, S. (2020). UCI Machine Learning Repository. https://doi.org/10.24432/C5HS5C
- **원본 URL**: https://archive.ics.uci.edu/dataset/601
- **라이선스**: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
- **받은 날짜**: 2026-09-26 (원본은 수정하지 않음)
- **SHA-256**: `dc6630cd9b1f0f853922fad78a1b6436570d3f1ec863f1dd5c4340ac56bc8a8e`

## split.csv

- 모든 엔진을 같은 조건으로 비교하기 위한 고정 train/test 분할 (`docs/decisions.md` D-005)
- 컬럼: `udi`(원본 UDI), `split`(`train` / `test`)
- test 2,000행(고장 68건), train 8,000행(고장 271건). `machineFailure` 기준 층화, seed 42
- 재생성: `ml-server/.venv/bin/python ml-server/scripts/make_split.py` (다시 실행해도 같은 결과)

## 기본 정보 (다운로드 직후 확인)

- 10,000행, 14컬럼, 빈 값 없음
- 제품 타입: L 6,000 / M 2,997 / H 1,003
- Machine failure = 1: 339행 (3.39%)
- 유형별: TWF 46 / HDF 115 / PWF 95 / OSF 98 / RNF 19

## 읽을 때 주의

- **파일 앞에 UTF-8 BOM이 있다.** 그냥 읽으면 첫 컬럼명이 `﻿UDI`가 된다.
  Python은 `encoding="utf-8-sig"`, Java는 BOM을 제거하고 읽어야 한다.
- **라벨이 서로 완전히 일치하지 않는다.**

  | Machine failure | 유형 라벨 개수 | 행 수 | 의미 |
  |---|---|---|---|
  | 0 | 0 | 9,643 | 정상 |
  | 1 | 1 | 306 | 단일 유형 고장 |
  | 1 | 2~3 | 24 | 여러 유형 동시 고장 |
  | 1 | 0 | 9 | 고장인데 유형 라벨 없음 |
  | 0 | 1 | 18 | RNF=1인데 Machine failure=0 |
