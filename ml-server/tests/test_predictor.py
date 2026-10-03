"""판정 규칙과 저장된 모델의 성능이 노트북(04_ml_training.ipynb)과 같은지 확인한다."""
import numpy as np
import pytest

from app.features import CLASSES, NORMAL, load_dataset
from app.predictor import Predictor


@pytest.fixture(scope="module")
def predictor() -> Predictor:
    return Predictor()


@pytest.fixture(scope="module")
def dataset():
    return load_dataset()


def predict_udi(predictor, dataset, udi):
    return predictor.predict_many(dataset[dataset["udi"] == udi])[0]


@pytest.mark.parametrize("udi, category", [
    (1, NORMAL),
    (70, "PWF"),    # PWF + OSF 동시 고장 → 대표 유형 PWF (D-011)
    (3237, "HDF"),  # tempDiff 8.5999…, 부동소수점 경계의 HDF
])
def test_representative_rows(predictor, dataset, udi, category):
    assert predict_udi(predictor, dataset, udi).category == category


def test_test_split_matches_notebook(predictor, dataset):
    """test 2,000행 혼동행렬이 노트북 6절과 같다: TP 57 / FP 0 / FN 11."""
    test = dataset[dataset["split"] == "test"]
    predicted = np.array([p.anomaly for p in predictor.predict_many(test)])
    actual = test["machineFailure"].to_numpy() == 1
    assert (predicted & actual).sum() == 57
    assert (predicted & ~actual).sum() == 0
    assert (~predicted & actual).sum() == 11


def test_decision_invariants(predictor, dataset):
    """backend DecisionResult의 불변식을 만족한다 (D-011)."""
    for p in predictor.predict_many(dataset.sample(500, random_state=0)):
        assert p.anomaly == (p.category != NORMAL)
        assert 0 <= p.severity <= 100
        assert 0 <= p.confidence <= 1
        assert set(p.probabilities) == set(CLASSES)
        assert sum(p.probabilities.values()) == pytest.approx(1)
        p_failure = p.severity / 100
        assert p.anomaly == (p_failure >= predictor.threshold)
        assert p.confidence == pytest.approx(p_failure if p.anomaly else 1 - p_failure)


def test_derived_features_are_computed_from_raw(predictor, dataset):
    """파생변수 컬럼이 들어와도 무시하고 원본값으로 다시 계산한다."""
    row = dataset[dataset["udi"] == 70].copy()
    expected = predictor.predict_many(row)[0]
    row[["tempDiff", "power", "wearTorque"]] = 0
    assert predictor.predict_many(row)[0] == expected
