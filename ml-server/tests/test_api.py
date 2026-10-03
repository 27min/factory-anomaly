import pytest
from fastapi.testclient import TestClient

from app.main import app

# AI4I UDI 70: PWF + OSF 동시 고장
UDI_70 = {"productType": "L", "airTemp": 298.9, "processTemp": 309.0,
          "rotSpeed": 1410, "torque": 65.7, "toolWear": 191}


@pytest.fixture(scope="module")
def client():
    with TestClient(app) as c:  # with: lifespan이 실행되어 모델을 읽는다
        yield c


def test_predict_returns_decision(client):
    res = client.post("/predict", json=UDI_70)

    assert res.status_code == 200
    body = res.json()
    assert body["anomaly"] is True
    assert body["category"] == "PWF"
    assert 0 <= body["severity"] <= 100
    assert 0 <= body["confidence"] <= 1
    assert set(body["probabilities"]) == {"NORMAL", "HDF", "PWF", "OSF", "TWF"}
    assert len(body["modelId"]) == 12


def test_health_reports_model(client):
    body = client.get("/health").json()

    assert body["status"] == "ok"
    assert body["modelId"] == client.post("/predict", json=UDI_70).json()["modelId"]
    assert 0 < body["threshold"] < 1
    assert "power" in body["features"]


@pytest.mark.parametrize("field, value", [
    ("productType", "X"),
    ("airTemp", 199.9),
    ("processTemp", 500.1),
    ("rotSpeed", -1),
    ("torque", -0.1),
    ("toolWear", -5),
])
def test_rejects_physically_impossible_values(client, field, value):
    res = client.post("/predict", json={**UDI_70, field: value})

    assert res.status_code == 422
    assert res.json()["detail"][0]["loc"] == ["body", field]


def test_rejects_missing_field(client):
    body = {k: v for k, v in UDI_70.items() if k != "torque"}

    res = client.post("/predict", json=body)

    assert res.status_code == 422
    assert res.json()["detail"][0]["loc"] == ["body", "torque"]
