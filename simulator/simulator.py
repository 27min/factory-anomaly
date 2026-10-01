"""AI4I CSV 행을 설비 N대가 일정 간격으로 보내는 센서 스트림처럼 재생한다.

- 행 i는 설비 EQ-{(i % N) + 1}에 배분한다 (라운드 로빈).
- 한 틱(--interval초)마다 설비 N대가 1건씩, 총 N건을 보낸다.
- 정답 라벨과 원본 UDI를 함께 보낸다 (시뮬레이션·벤치마크 전용 필드, D-009).

실패 처리
- 서버 연결 실패 / 타임아웃 / 5xx: 재시도 후에도 실패하면 중단한다 (서버가 없는 상태에서 계속 보내는 것은 의미가 없다).
- 4xx (검증 실패, 미등록 설비): 로그를 남기고 다음 행으로 넘어간다.
"""
from __future__ import annotations

import argparse
import csv
import logging
import os
import sys
import time
from dataclasses import dataclass
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CSV = ROOT / "data" / "ai4i2020.csv"
DEFAULT_SPLIT = ROOT / "data" / "split.csv"

log = logging.getLogger("simulator")


class ServerUnavailable(Exception):
    pass


@dataclass
class Stats:
    created: int = 0
    rejected: int = 0

    @property
    def sent(self) -> int:
        return self.created + self.rejected


def load_rows(csv_path: Path, split: str, split_path: Path, limit: int | None) -> list[dict]:
    # 원본 파일에 UTF-8 BOM이 있으므로 utf-8-sig로 읽는다
    with csv_path.open(encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))

    if split != "all":
        with split_path.open(encoding="utf-8", newline="") as f:
            wanted = {r["udi"] for r in csv.DictReader(f) if r["split"] == split}
        rows = [r for r in rows if r["UDI"] in wanted]

    return rows[:limit] if limit else rows


def to_payload(row: dict, equipment_code: str) -> dict:
    flag = lambda col: row[col] == "1"
    return {
        "equipmentCode": equipment_code,
        "productType": row["Type"],
        "airTemp": float(row["Air temperature [K]"]),
        "processTemp": float(row["Process temperature [K]"]),
        "rotSpeed": int(row["Rotational speed [rpm]"]),
        "torque": float(row["Torque [Nm]"]),
        "toolWear": int(row["Tool wear [min]"]),
        "labels": {
            "machineFailure": flag("Machine failure"),
            "twf": flag("TWF"),
            "hdf": flag("HDF"),
            "pwf": flag("PWF"),
            "osf": flag("OSF"),
            "rnf": flag("RNF"),
        },
        "sourceUdi": int(row["UDI"]),
    }


def send(session: requests.Session, url: str, payload: dict, timeout: float, max_retries: int) -> requests.Response:
    reason = ""
    for attempt in range(1, max_retries + 1):
        try:
            response = session.post(url, json=payload, timeout=timeout)
            if response.status_code < 500:
                return response
            reason = f"HTTP {response.status_code}"
        except (requests.ConnectionError, requests.Timeout) as e:
            reason = type(e).__name__

        if attempt < max_retries:
            wait = 2 ** (attempt - 1)
            log.warning("UDI %s 전송 실패 (%s), %d초 후 재시도 %d/%d",
                        payload["sourceUdi"], reason, wait, attempt, max_retries - 1)
            time.sleep(wait)

    raise ServerUnavailable(f"UDI {payload['sourceUdi']}: {max_retries}회 시도 모두 실패 ({reason})")


def run(args: argparse.Namespace) -> int:
    rows = load_rows(args.csv, args.split, args.split_file, args.limit)
    url = args.base_url.rstrip("/") + "/api/readings"
    n = args.equipment
    log.info("%d행 재생 시작 → %s (설비 %d대, 간격 %.2f초, split=%s)", len(rows), url, n, args.interval, args.split)

    stats = Stats()
    started = time.monotonic()

    try:
        with requests.Session() as session:
            for tick_start in range(0, len(rows), n):
                tick_began = time.monotonic()

                for i in range(tick_start, min(tick_start + n, len(rows))):
                    code = f"EQ-{(i % n) + 1:02d}"
                    payload = to_payload(rows[i], code)
                    response = send(session, url, payload, args.timeout, args.max_retries)

                    if response.status_code == 201:
                        stats.created += 1
                    else:
                        stats.rejected += 1
                        log.warning("UDI %s 거부됨 (HTTP %d): %s",
                                    payload["sourceUdi"], response.status_code, response.text[:200])

                if stats.sent % args.progress_every < n:
                    log.info("진행 %d / %d (성공 %d, 거부 %d)", stats.sent, len(rows), stats.created, stats.rejected)

                remaining = args.interval - (time.monotonic() - tick_began)
                if remaining > 0:
                    time.sleep(remaining)
    except ServerUnavailable as e:
        log.error("서버에 연결할 수 없어 중단합니다: %s", e)
        return summarize(stats, started, exit_code=1)
    except KeyboardInterrupt:
        log.info("사용자가 중단했습니다.")
        return summarize(stats, started, exit_code=130)

    return summarize(stats, started, exit_code=0 if stats.rejected == 0 else 2)


def summarize(stats: Stats, started: float, exit_code: int) -> int:
    elapsed = time.monotonic() - started
    rate = stats.sent / elapsed if elapsed > 0 else 0
    log.info("완료: 전송 %d건 (성공 %d, 거부 %d), %.1f초, %.1f건/초",
             stats.sent, stats.created, stats.rejected, elapsed, rate)
    return exit_code


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="AI4I 데이터를 센서 스트림처럼 재생해 backend로 전송한다.")
    p.add_argument("--base-url", default=os.getenv("SIMULATOR_BASE_URL", "http://localhost:8080"))
    p.add_argument("--csv", type=Path, default=DEFAULT_CSV)
    p.add_argument("--split", choices=["all", "train", "test"], default="all",
                   help="재생할 행 (data/split.csv 기준, 기본: 전체)")
    p.add_argument("--split-file", type=Path, default=DEFAULT_SPLIT)
    p.add_argument("--limit", type=int, help="앞에서부터 N행만 재생")
    p.add_argument("--equipment", type=int, default=5, help="설비 수 (기본: 5)")
    p.add_argument("--interval", type=float, default=1.0, help="틱 간격(초). 0이면 최대 속도 (기본: 1.0)")
    p.add_argument("--timeout", type=float, default=5.0, help="요청 타임아웃(초)")
    p.add_argument("--max-retries", type=int, default=3, help="서버 오류 시 최대 시도 횟수")
    p.add_argument("--progress-every", type=int, default=500, help="진행 로그 출력 간격(건)")
    args = p.parse_args(argv)
    for name in ("equipment", "max_retries", "progress_every"):
        if getattr(args, name) < 1:
            p.error(f"--{name.replace('_', '-')}는 1 이상이어야 합니다")
    if args.interval < 0:
        p.error("--interval은 0 이상이어야 합니다")
    return args


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-7s %(message)s", datefmt="%H:%M:%S")
    sys.exit(run(parse_args()))
