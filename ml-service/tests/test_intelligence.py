from datetime import date, timedelta
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from app.main import app
from app.models import RecommendationRequest, SeriesRequest
from app.intelligence import recommend, forecast, anomalies


def series(days=60, spike=False):
    start = date(2025, 1, 1)
    return SeriesRequest(observations=[{'date': start + timedelta(days=i), 'units': 10 + i % 7,
         'revenue': 10000 if spike and i == days - 1 else 100 + i % 7} for i in range(days)], asOf=start + timedelta(days=days - 1))


def test_recommendations_use_content_and_exclude_self():
    result = recommend(RecommendationRequest(productId='1', products=[
        {'id':'1','name':'Headphones','description':'Wireless audio music','category':'Audio'},
        {'id':'2','name':'Speakers','description':'Wireless audio music','category':'Audio'},
        {'id':'3','name':'Desk','description':'Wooden office furniture','category':'Furniture'}]))
    assert result['recommendations'][0]['id'] == '2'
    assert all(p['id'] != '1' and 0 < p['score'] <= 1 for p in result['recommendations'])


def test_forecast_requires_history():
    assert forecast(series(12))['status'] == 'insufficient_data'


def test_forecast_trains_and_predicts_future_dates():
    result = forecast(series())
    assert result['status'] == 'ready'
    assert len(result['forecast']) == 7
    assert result['forecast'][0]['date'] == '2025-03-02'
    assert all(p['units'] >= 0 for p in result['forecast'])
    assert result['validationMae'] >= 0
    assert result == forecast(series())


def test_forecast_responds_to_observed_demand():
    low = series()
    high = low.model_copy(deep=True)
    for o in high.observations:
        o.units *= 5
    assert forecast(high)['forecast'][0]['units'] > forecast(low)['forecast'][0]['units'] * 4


def test_spike_is_flagged():
    result = anomalies(series(spike=True))
    assert result['status'] == 'ready'
    assert any(a['revenue'] == 10000 for a in result['anomalies'])


def test_empty_history_does_not_fabricate_predictions():
    request = SeriesRequest(observations=[], asOf=date(2025, 1, 1))
    assert forecast(request)['status'] == 'insufficient_data'
    assert anomalies(request)['status'] == 'insufficient_data'


def test_duplicate_dates_and_future_data_rejected():
    with pytest.raises(ValidationError):
        SeriesRequest(observations=[{'date':'2025-01-01'},{'date':'2025-01-01'}], asOf=date(2025, 1, 1))
    with pytest.raises(ValidationError):
        SeriesRequest(observations=[{'date':'2025-01-02'}], asOf=date(2025, 1, 1))


def test_service_requires_authentication(monkeypatch):
    monkeypatch.setenv('ML_SERVICE_TOKEN', 'x' * 32)
    client = TestClient(app)
    body = {'observations': [], 'asOf': '2025-01-01'}
    assert client.post('/forecast', json=body).status_code == 401
    response = client.post('/forecast', json=body, headers={'X-Service-Token':'x' * 32})
    assert response.status_code == 200
    assert response.json()['status'] == 'insufficient_data'


@pytest.mark.parametrize('endpoint,metric', [('/forecast', 'units'), ('/anomalies', 'revenue')])
def test_missing_metric_is_rejected_instead_of_assumed_zero(monkeypatch, endpoint, metric):
    monkeypatch.setenv('ML_SERVICE_TOKEN', 'x' * 32)
    client = TestClient(app)
    body = {'observations': [{'date': '2025-01-01'}], 'asOf': '2025-01-01'}
    headers = {'X-Service-Token': 'x' * 32}
    response = client.post(endpoint, json=body, headers=headers)
    assert response.status_code == 422
    body['observations'][0][metric] = 0
    assert client.post(endpoint, json=body, headers=headers).status_code == 200


def test_non_ascii_service_token_is_unauthorized_not_server_error(monkeypatch):
    monkeypatch.setenv('ML_SERVICE_TOKEN', 'x' * 32)
    client = TestClient(app, raise_server_exceptions=False)
    response = client.post('/forecast', json={'observations': [], 'asOf': '2025-01-01'},
                           headers=[(b'X-Service-Token', b'\xc3\xa9')])
    assert response.status_code == 401


@pytest.mark.parametrize('endpoint,metric', [('/forecast', 'units'), ('/anomalies', 'revenue')])
@pytest.mark.parametrize('day', ['0001-01-01', '9999-12-31'])
def test_dates_outside_model_runtime_range_are_validation_errors(monkeypatch, endpoint, metric, day):
    monkeypatch.setenv('ML_SERVICE_TOKEN', 'x' * 32)
    client = TestClient(app, raise_server_exceptions=False)
    response = client.post(endpoint, json={
        'observations': [{'date': day, metric: 1}], 'asOf': day,
    }, headers={'X-Service-Token': 'x' * 32})
    assert response.status_code == 422


def test_forecast_horizon_cannot_overflow_supported_dates(monkeypatch):
    monkeypatch.setenv('ML_SERVICE_TOKEN', 'x' * 32)
    client = TestClient(app, raise_server_exceptions=False)
    start = date(2262, 2, 10)
    response = client.post('/forecast', json={
        'observations': [{'date': (start + timedelta(days=i)).isoformat(), 'units': 10 + i % 7}
                         for i in range(60)],
        'asOf': '2262-04-10', 'horizon': 7,
    }, headers={'X-Service-Token': 'x' * 32})
    assert response.status_code == 422


@pytest.mark.parametrize('day', ['1677-09-22', '2262-04-11'])
def test_supported_boundary_dates_remain_valid_for_anomalies(monkeypatch, day):
    monkeypatch.setenv('ML_SERVICE_TOKEN', 'x' * 32)
    response = TestClient(app).post('/anomalies', json={
        'observations': [{'date': day, 'revenue': 1}], 'asOf': day,
    }, headers={'X-Service-Token': 'x' * 32})
    assert response.status_code == 200
    assert response.json()['status'] == 'insufficient_data'


def test_forecast_can_end_on_last_supported_day(monkeypatch):
    monkeypatch.setenv('ML_SERVICE_TOKEN', 'x' * 32)
    start = date(2262, 2, 10)
    response = TestClient(app).post('/forecast', json={
        'observations': [{'date': (start + timedelta(days=i)).isoformat(), 'units': 10 + i % 7}
                         for i in range(60)],
        'asOf': '2262-04-10', 'horizon': 1,
    }, headers={'X-Service-Token': 'x' * 32})
    assert response.status_code == 200
    assert response.json()['forecast'][0]['date'] == '2262-04-11'


@pytest.mark.parametrize('endpoint', ['/forecast', '/anomalies'])
def test_jackson_local_date_array_is_supported(monkeypatch, endpoint):
    monkeypatch.setenv('ML_SERVICE_TOKEN', 'x' * 32)
    client = TestClient(app)
    headers = {'X-Service-Token': 'x' * 32}
    iso = client.post(endpoint, json={'observations': [], 'asOf': '2025-01-01'}, headers=headers)
    array = client.post(endpoint, json={'observations': [], 'asOf': [2025, 1, 1]}, headers=headers)
    assert array.status_code == 200
    assert array.json() == iso.json()


@pytest.mark.parametrize('day', [[2025, 2, 30], [2025, 1], [True, 1, 1], ['2025', 1, 1], [9999, 1, 1], [10**100, 1, 1]])
def test_invalid_jackson_dates_remain_validation_errors(monkeypatch, day):
    monkeypatch.setenv('ML_SERVICE_TOKEN', 'x' * 32)
    response = TestClient(app).post('/forecast', json={'observations': [], 'asOf': day},
                                   headers={'X-Service-Token': 'x' * 32})
    assert response.status_code == 422
