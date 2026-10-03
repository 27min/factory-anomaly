"""ML 엔진의 입력 피처와 학습 라벨 정의.

학습(notebooks/04_ml_training.ipynb, scripts/train.py)과 추론(FastAPI 서버)이 같은 코드를 쓰도록 한 곳에 둔다.
파생변수 계산식은 backend StateBuilder와 같다.
"""
from pathlib import Path

import numpy as np
import pandas as pd

DATA_DIR = Path(__file__).resolve().parents[2] / "data"

COLUMNS = {
    "UDI": "udi", "Product ID": "productId", "Type": "productType",
    "Air temperature [K]": "airTemp", "Process temperature [K]": "processTemp",
    "Rotational speed [rpm]": "rotSpeed", "Torque [Nm]": "torque",
    "Tool wear [min]": "toolWear", "Machine failure": "machineFailure",
}

# 제품 등급은 L < M < H 순서가 있으므로 정수로 둔다
PRODUCT_TYPE_CODE = {"L": 0, "M": 1, "H": 2}

RAW_FEATURES = ["productType", "airTemp", "processTemp", "rotSpeed", "torque", "toolWear"]
DERIVED_FEATURES = ["tempDiff", "power", "wearTorque"]
FEATURE_SETS = {
    "raw": RAW_FEATURES,
    "raw+derived": RAW_FEATURES + DERIVED_FEATURES,
}

# 다중분류 클래스. 여러 유형이 동시에 발생하면 룰 엔진과 같은 우선순위로 대표 유형을 고른다 (D-011)
NORMAL = "NORMAL"
FAILURE_PRIORITY = ["HDF", "PWF", "OSF", "TWF"]
CLASSES = [NORMAL] + FAILURE_PRIORITY


def load_dataset() -> pd.DataFrame:
    """원본 CSV + split + 파생변수 + 학습 라벨."""
    df = pd.read_csv(DATA_DIR / "ai4i2020.csv", encoding="utf-8-sig").rename(columns=COLUMNS)
    split = pd.read_csv(DATA_DIR / "split.csv")
    assert (df["udi"].values == split["udi"].values).all()
    df["split"] = split["split"].values
    df = add_derived(df)
    df["label"] = df.apply(label_of, axis=1)
    return df


def add_derived(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["tempDiff"] = df["processTemp"] - df["airTemp"]
    df["power"] = df["torque"] * df["rotSpeed"] * 2 * np.pi / 60
    df["wearTorque"] = df["toolWear"] * df["torque"]
    return df


def label_of(row) -> str | None:
    """machineFailure = 0이면 NORMAL (RNF 단독 포함, D-003).
    고장이면 대표 유형. 유형 라벨이 없는 고장은 None → 학습에서만 제외하고 평가에는 고장으로 남긴다.
    """
    if row["machineFailure"] == 0:
        return NORMAL
    for failure_type in FAILURE_PRIORITY:
        if row[failure_type] == 1:
            return failure_type
    return None


def to_matrix(df: pd.DataFrame, features: list[str]) -> np.ndarray:
    x = df[features].copy()
    if "productType" in features:
        x["productType"] = x["productType"].map(PRODUCT_TYPE_CODE)
    return x.to_numpy(dtype=float)
