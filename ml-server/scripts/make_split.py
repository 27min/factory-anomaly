"""AI4I 데이터를 train/test로 고정 분할해 data/split.csv에 저장한다.

- machineFailure 비율을 유지하는 층화 분할 (test 20%, seed 42)
- 모든 엔진은 이 파일의 test 행으로 비교한다. ML은 test 행을 학습에 쓰지 않는다.

실행: ml-server/.venv/bin/python ml-server/scripts/make_split.py
"""
from pathlib import Path

import pandas as pd
from sklearn.model_selection import train_test_split

TEST_SIZE = 0.2
SEED = 42

DATA_DIR = Path(__file__).resolve().parents[2] / "data"


def main() -> None:
    df = pd.read_csv(DATA_DIR / "ai4i2020.csv", encoding="utf-8-sig")
    train_idx, test_idx = train_test_split(
        df.index, test_size=TEST_SIZE, random_state=SEED, stratify=df["Machine failure"]
    )
    split = pd.DataFrame({"udi": df["UDI"], "split": "train"})
    split.loc[test_idx, "split"] = "test"
    split.to_csv(DATA_DIR / "split.csv", index=False)

    summary = df.groupby(split["split"])["Machine failure"].agg(["size", "sum", "mean"])
    print(summary)


if __name__ == "__main__":
    main()
