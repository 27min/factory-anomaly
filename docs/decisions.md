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
