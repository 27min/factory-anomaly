"""ML 엔진 모델을 학습해 ml-server/models/에 저장한다.

- 설정(피처 조합, 불균형 처리, 하이퍼파라미터)은 notebooks/04_ml_training.ipynb의 교차검증 결과로 정했다.
- 학습과 임계값 결정에는 train 행만 쓴다. test 행은 평가에만 쓴다 (원칙 3, D-005).
- 실험 함수(make_model, oof_proba, best_f1_threshold)는 노트북도 import해서 쓴다 → 노트북과 같은 방식으로 학습된다.

실행: ml-server/.venv/bin/python ml-server/scripts/train.py
"""
import json
import sys
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import sklearn
from imblearn.over_sampling import SMOTE
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.metrics import average_precision_score, precision_recall_curve
from sklearn.model_selection import StratifiedKFold

ML_SERVER_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ML_SERVER_DIR))

from app.features import CLASSES, FEATURE_SETS, NORMAL, load_dataset, to_matrix  # noqa: E402

SEED = 42
N_FOLDS = 5

# 기본값(learning_rate=0.1, 규제 없음)은 파생변수를 넣으면 학습이 발산해 fold마다 결과가 크게 흔들렸다 (노트북 3절)
LEARNING_RATE = 0.1
L2_REGULARIZATION = 1.0

# 노트북 4절 교차검증 결과로 선택
FEATURE_SET = "raw+derived"
IMBALANCE = "class_weight"

MODEL_DIR = ML_SERVER_DIR / "models"


def make_model(imbalance: str) -> HistGradientBoostingClassifier:
    return HistGradientBoostingClassifier(
        random_state=SEED,
        class_weight="balanced" if imbalance == "class_weight" else None,
        learning_rate=LEARNING_RATE,
        l2_regularization=L2_REGULARIZATION,
    )


def fit(x: np.ndarray, y: np.ndarray, imbalance: str) -> HistGradientBoostingClassifier:
    if imbalance == "smote":
        x, y = SMOTE(random_state=SEED).fit_resample(x, y)
    return make_model(imbalance).fit(x, y)


def proba_in_class_order(model, x: np.ndarray) -> np.ndarray:
    """predict_proba 열 순서를 CLASSES 순서로 맞춘다."""
    order = [list(model.classes_).index(c) for c in CLASSES]
    return model.predict_proba(x)[:, order]


def oof_proba(train: pd.DataFrame, features: list[str], imbalance: str) -> np.ndarray:
    """train 행에 대한 out-of-fold 클래스 확률.

    fold는 machineFailure로 층화한다. 유형 라벨이 없는 고장(label=None)은 학습에서만 빼고 예측·평가에는 포함한다.
    SMOTE는 fold 안의 학습 부분에만 적용한다 (검증 fold로 합성 샘플이 새지 않도록).
    """
    x = to_matrix(train, features)
    labels = train["label"].to_numpy()
    proba = np.zeros((len(train), len(CLASSES)))
    folds = StratifiedKFold(N_FOLDS, shuffle=True, random_state=SEED)
    for fit_idx, val_idx in folds.split(x, train["machineFailure"]):
        fit_idx = fit_idx[pd.notna(labels[fit_idx])]
        model = fit(x[fit_idx], labels[fit_idx], imbalance)
        proba[val_idx] = proba_in_class_order(model, x[val_idx])
    return proba


def failure_proba(proba: np.ndarray) -> np.ndarray:
    return 1 - proba[:, CLASSES.index(NORMAL)]


def best_f1_threshold(y_true: np.ndarray, p_failure: np.ndarray) -> dict:
    """F1이 최대가 되는 고장 확률 임계값."""
    precision, recall, thresholds = precision_recall_curve(y_true, p_failure)
    precision, recall = precision[:-1], recall[:-1]  # 마지막 점은 임계값이 없다
    f1 = 2 * precision * recall / np.clip(precision + recall, 1e-12, None)
    i = int(np.argmax(f1))
    return {
        "threshold": float(thresholds[i]),
        "f1": float(f1[i]),
        "precision": float(precision[i]),
        "recall": float(recall[i]),
        "average_precision": float(average_precision_score(y_true, p_failure)),
    }


def main() -> None:
    df = load_dataset()
    train = df[df["split"] == "train"].reset_index(drop=True)
    features = FEATURE_SETS[FEATURE_SET]

    # 임계값은 train OOF 확률로 정한다
    cv = best_f1_threshold(train["machineFailure"].to_numpy(),
                           failure_proba(oof_proba(train, features, IMBALANCE)))

    labeled = train[train["label"].notna()]
    model = fit(to_matrix(labeled, features), labeled["label"].to_numpy(), IMBALANCE)

    MODEL_DIR.mkdir(exist_ok=True)
    joblib.dump(model, MODEL_DIR / "model.joblib")
    meta = {
        "model": "HistGradientBoostingClassifier",
        "hyperparams": {"learning_rate": LEARNING_RATE, "l2_regularization": L2_REGULARIZATION},
        "imbalance": IMBALANCE,
        "features": features,
        "classes": CLASSES,
        "threshold": cv["threshold"],
        "cv": {k: round(v, 4) for k, v in cv.items() if k != "threshold"},
        "train_rows": int(len(labeled)),
        "sklearn_version": sklearn.__version__,
        "seed": SEED,
    }
    (MODEL_DIR / "model_meta.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps(meta, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
