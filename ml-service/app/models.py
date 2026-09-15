from datetime import date as Date, timedelta
from pydantic import BaseModel, Field, model_validator, field_validator

# Whole days representable by the nanosecond DatetimeIndex used by the models.
MIN_MODEL_DATE = Date(1677, 9, 22)
MAX_MODEL_DATE = Date(2262, 4, 11)


class Product(BaseModel):
    id: str
    name: str = Field(min_length=1, max_length=200)
    description: str = Field(max_length=4000)
    category: str = Field(max_length=100)

class RecommendationRequest(BaseModel):
    productId: str
    products: list[Product] = Field(max_length=5000)
    limit: int = Field(default=5, ge=1, le=20)

class Observation(BaseModel):
    date: Date = Field(ge=MIN_MODEL_DATE, le=MAX_MODEL_DATE)
    units: float = Field(default=0, ge=0, allow_inf_nan=False)
    revenue: float = Field(default=0, ge=0, allow_inf_nan=False)

class SeriesRequest(BaseModel):
    observations: list[Observation] = Field(max_length=3660)
    asOf: Date = Field(ge=MIN_MODEL_DATE, le=MAX_MODEL_DATE)
    horizon: int = Field(default=7, ge=1, le=30)

    @field_validator('asOf', mode='before')
    @classmethod
    def accept_jackson_date(cls, value):
        # Jackson's JavaTimeModule can encode LocalDate as [year, month, day].
        # Accept that wire format as well as ISO dates, retaining date bounds.
        if isinstance(value, list):
            if len(value) != 3 or any(type(part) is not int for part in value):
                raise ValueError('Date array must contain integer year, month, day')
            try:
                return Date(*value)
            except (ValueError, OverflowError) as error:
                raise ValueError('Invalid calendar date') from error
        return value

    @model_validator(mode='after')
    def validate_dates(self):
        dates = [o.date for o in self.observations]
        if len(set(dates)) != len(dates):
            raise ValueError('Dates must be unique')
        if any(d > self.asOf for d in dates):
            raise ValueError('Observations must not be after asOf')
        if dates and (self.asOf - min(dates)).days > 3660:
            raise ValueError('Series cannot span more than 3660 days')
        return self


class DemandObservation(Observation):
    units: float = Field(ge=0, allow_inf_nan=False)


class RevenueObservation(Observation):
    revenue: float = Field(ge=0, allow_inf_nan=False)


class DemandRequest(SeriesRequest):
    observations: list[DemandObservation] = Field(max_length=3660)

    @model_validator(mode='after')
    def validate_forecast_end(self):
        if self.asOf + timedelta(days=self.horizon) > MAX_MODEL_DATE:
            raise ValueError('Forecast horizon exceeds the supported date range')
        return self


class RevenueRequest(SeriesRequest):
    observations: list[RevenueObservation] = Field(max_length=3660)
