from typing import Annotated, Literal
from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field, field_validator

Key = Annotated[str, Field(pattern=r"^[0-9]{1,6}$")]
Text = Annotated[str, Field(max_length=8192)]


class ClosedModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class ErrorResponse(ClosedModel):
    errorCode: str


class RequestBase(ClosedModel):
    schemaVersion: Literal["semantic-v1"]
    requestId: str
    templateVersion: Literal["canonical-v1", "school-advisory-text-v1"]

    @field_validator("requestId")
    @classmethod
    def valid_uuid(cls, value):
        if str(UUID(value)) != value:
            raise ValueError("Invalid request identifier")
        return value


class TextInput(ClosedModel):
    key: Key
    role: Literal["query", "passage"]
    text: Text


class EmbeddingRequest(RequestBase):
    inputs: Annotated[list[TextInput], Field(min_length=1, max_length=16)]

    @field_validator("inputs")
    @classmethod
    def distinct_keys(cls, values):
        if len({v.key for v in values}) != len(values):
            raise ValueError("Duplicate input key")
        return values


class Passage(ClosedModel):
    key: Key
    text: Text


class SimilarityRequest(RequestBase):
    query: Text
    passages: Annotated[list[Passage], Field(min_length=1, max_length=16)]

    @field_validator("passages")
    @classmethod
    def distinct_keys(cls, values):
        if len({v.key for v in values}) != len(values):
            raise ValueError("Duplicate passage key")
        return values


class ResponseBase(ClosedModel):
    schemaVersion: Literal["semantic-v1"] = "semantic-v1"
    requestId: str
    templateVersion: Literal["canonical-v1", "school-advisory-text-v1"] = "canonical-v1"
    modelId: Literal["intfloat/multilingual-e5-small"] = "intfloat/multilingual-e5-small"
    modelRevision: str
    dimension: Literal[384] = 384
    aggregationVersion: Literal["token-weighted-mean-v1"] = "token-weighted-mean-v1"


class EmbeddingResult(ClosedModel):
    key: Key
    status: Literal["READY", "NO_DATA"]
    vector: Annotated[list[float], Field(min_length=384, max_length=384)] | None
    chunks: Annotated[int, Field(ge=0, le=4)]
    limited: bool


class EmbeddingResponse(ResponseBase):
    results: list[EmbeddingResult]


class SimilarityResult(ClosedModel):
    key: Key
    status: Literal["READY", "NO_DATA"]
    score: Annotated[float, Field(ge=-1, le=1, allow_inf_nan=False)] | None
    queryChunks: Annotated[int, Field(ge=0, le=4)]
    passageChunks: Annotated[int, Field(ge=0, le=4)]
    limited: bool


class SimilarityResponse(ResponseBase):
    results: list[SimilarityResult]
