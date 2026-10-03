"""API 요청/응답 형식. 필드 이름은 backend와 같은 camelCase를 쓴다."""
from typing import Literal

from pydantic import BaseModel, Field


class PredictRequest(BaseModel):
    """원본 센서값. 파생변수는 서버가 계산한다 (D-016).
    검증 범위는 backend 수집 API와 같이 물리적으로 불가능한 값만 막는다 (D-009).
    """
    productType: Literal["L", "M", "H"]
    airTemp: float = Field(ge=200, le=500, description="K")
    processTemp: float = Field(ge=200, le=500, description="K")
    rotSpeed: int = Field(ge=0, description="rpm")
    torque: float = Field(ge=0, description="Nm")
    toolWear: int = Field(ge=0, description="min")


class PredictResponse(BaseModel):
    """backend DecisionResult와 같은 형태 + 디버깅용 클래스별 확률과 모델 식별자."""
    anomaly: bool
    severity: float = Field(ge=0, le=100)
    category: Literal["NORMAL", "HDF", "PWF", "OSF", "TWF"]
    confidence: float = Field(ge=0, le=1)
    probabilities: dict[str, float]
    modelId: str


class HealthResponse(BaseModel):
    status: Literal["ok"]
    modelId: str
    threshold: float
    features: list[str]
