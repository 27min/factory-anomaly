"""ML 엔진 추론 서버.

실행 (ml-server/ 에서): .venv/bin/uvicorn app.main:app --port 8000
"""
import os
from contextlib import asynccontextmanager
from pathlib import Path

import pandas as pd
from fastapi import FastAPI, Request

from app.predictor import DEFAULT_MODEL_DIR, Predictor
from app.schemas import HealthResponse, PredictRequest, PredictResponse


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 모델은 시작할 때 한 번만 읽는다. 파일이 없거나 깨졌으면 서버가 뜨지 않는다
    app.state.predictor = Predictor(Path(os.environ.get("MODEL_DIR", DEFAULT_MODEL_DIR)))
    yield


app = FastAPI(title="factory-anomaly ml-server", lifespan=lifespan)


@app.post("/predict", response_model=PredictResponse)
def predict(body: PredictRequest, request: Request) -> PredictResponse:
    predictor: Predictor = request.app.state.predictor
    p = predictor.predict_many(pd.DataFrame([body.model_dump()]))[0]
    return PredictResponse(
        anomaly=p.anomaly, severity=p.severity, category=p.category, confidence=p.confidence,
        probabilities=p.probabilities, modelId=predictor.model_id,
    )


@app.get("/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    predictor: Predictor = request.app.state.predictor
    return HealthResponse(status="ok", modelId=predictor.model_id,
                          threshold=predictor.threshold, features=predictor.features)
