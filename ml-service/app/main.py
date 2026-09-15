import os
import secrets
from fastapi import FastAPI, Depends, Header, HTTPException
from .models import RecommendationRequest, DemandRequest, RevenueRequest
from .intelligence import recommend, forecast, anomalies

app = FastAPI(title='Commerce Intelligence ML', version='0.1.0', docs_url=None, redoc_url=None)


def authorize(x_service_token: str = Header(default='')):
    expected = os.environ.get('ML_SERVICE_TOKEN', '').encode('utf-8')
    if len(expected) < 32:
        raise HTTPException(503, 'Service authentication is not configured')
    if not secrets.compare_digest(x_service_token.encode('utf-8'), expected):
        raise HTTPException(401, 'Invalid service token')


@app.get('/health')
def health():
    return {'status': 'ok'}


@app.post('/recommendations', dependencies=[Depends(authorize)])
def recommendations(request: RecommendationRequest):
    try:
        return recommend(request)
    except ValueError as error:
        raise HTTPException(422, str(error)) from error


@app.post('/forecast', dependencies=[Depends(authorize)])
def demand(request: DemandRequest):
    return forecast(request)


@app.post('/anomalies', dependencies=[Depends(authorize)])
def revenue_anomalies(request: RevenueRequest):
    return anomalies(request)
