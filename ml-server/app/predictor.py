"""학습된 모델을 읽어 측정값을 판정한다.

판정 규칙 (D-015, D-016)
- 고장 확률 p = 1 − P(NORMAL)
- anomaly = p ≥ 임계값 (model_meta.json, train OOF에서 F1 최대)
- severity = 100 × p
- category = anomaly면 고장 클래스 중 확률 최대, 아니면 NORMAL
- confidence = 이상 여부 판정에 대한 확신도: anomaly면 p, 아니면 1 − p

예측은 OpenMP 스레드 1개로 한다. 요청마다 1건씩 판정하는데 코어 수만큼 스레드를 깨우면
그 비용이 예측보다 커진다 (로컬 측정: 8스레드 약 22ms → 1스레드 약 2.5ms, D-016).
"""
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from threadpoolctl import ThreadpoolController

from app.features import CLASSES, NORMAL, RAW_FEATURES, add_derived, to_matrix

DEFAULT_MODEL_DIR = Path(__file__).resolve().parents[1] / "models"


@dataclass(frozen=True)
class Prediction:
    anomaly: bool
    severity: float
    category: str
    confidence: float
    probabilities: dict[str, float]


class Predictor:

    def __init__(self, model_dir: Path = DEFAULT_MODEL_DIR):
        model_path = model_dir / "model.joblib"
        self.meta = json.loads((model_dir / "model_meta.json").read_text())
        self.model = joblib.load(model_path)
        # 응답에 실어 어떤 모델 파일이 판정했는지 추적한다
        self.model_id = hashlib.sha256(model_path.read_bytes()).hexdigest()[:12]
        self.threshold = float(self.meta["threshold"])
        self.features = self.meta["features"]
        if self.meta["classes"] != CLASSES:
            raise ValueError(f"model classes {self.meta['classes']} != {CLASSES}")
        self._order = [list(self.model.classes_).index(c) for c in CLASSES]
        self._threadpools = ThreadpoolController()

    def predict_many(self, raw: pd.DataFrame) -> list[Prediction]:
        """원본 센서값(RAW_FEATURES 컬럼)으로 판정한다. 파생변수는 학습과 같은 코드로 여기서 계산한다."""
        x = to_matrix(add_derived(pd.DataFrame(raw[RAW_FEATURES])), self.features)
        with self._threadpools.limit(limits=1, user_api="openmp"):
            proba = self.model.predict_proba(x)[:, self._order]
        return [self._decide(row) for row in proba]

    def _decide(self, proba: np.ndarray) -> Prediction:
        p_failure = float(np.clip(1 - proba[CLASSES.index(NORMAL)], 0, 1))
        anomaly = p_failure >= self.threshold
        category = CLASSES[1 + int(np.argmax(proba[1:]))] if anomaly else NORMAL
        return Prediction(
            anomaly=anomaly,
            severity=100 * p_failure,
            category=category,
            confidence=p_failure if anomaly else 1 - p_failure,
            probabilities={c: float(p) for c, p in zip(CLASSES, proba)},
        )
