import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.ensemble import RandomForestRegressor, IsolationForest
from sklearn.metrics import mean_absolute_error
from .models import RecommendationRequest, SeriesRequest


def insufficient(reason: str):
    return {'status': 'insufficient_data', 'reason': reason}


def recommend(request: RecommendationRequest):
    products = request.products
    ids = [p.id for p in products]
    if len(ids) != len(set(ids)):
        raise ValueError('Product IDs must be unique')
    if request.productId not in ids:
        return insufficient('The requested product is not in the active catalog.')
    if len(products) < 2:
        return insufficient('Add at least two products to compare their content.')
    texts = [f'{p.name} {p.description} {p.category}' for p in products]
    try:
        matrix = TfidfVectorizer(stop_words='english', max_features=20000).fit_transform(texts)
    except ValueError:
        return insufficient('The catalog does not contain enough descriptive text.')
    index = ids.index(request.productId)
    scores = cosine_similarity(matrix[index], matrix).ravel()
    ranking = sorted((i for i in range(len(ids)) if i != index and scores[i] > 0), key=lambda i: (-scores[i], ids[i]))[:request.limit]
    return {'status': 'ready', 'method': 'TF-IDF cosine similarity', 'trainingRows': len(products),
            'recommendations': [{'id': ids[i], 'name': products[i].name, 'score': round(float(scores[i]), 6)} for i in ranking]}


def daily_series(request: SeriesRequest, field: str):
    if not request.observations:
        return pd.Series(dtype=float)
    series = pd.Series({pd.Timestamp(o.date): getattr(o, field) for o in request.observations}).sort_index()
    # The backend supplies all paid sales for the interval. Missing dates are zero-sales days.
    return series.reindex(pd.date_range(series.index.min(), request.asOf, freq='D'), fill_value=0.0).astype(float)


def features(values: list[float], day: pd.Timestamp):
    return [values[-1], values[-7], float(np.mean(values[-7:])), float(np.mean(values[-14:])), day.dayofweek]


def forecast(request: SeriesRequest):
    series = daily_series(request, 'units')
    if len(series) < 35 or (series > 0).sum() < 7:
        return insufficient('Forecasting needs at least 35 completed days and 7 days with paid sales.')
    values = series.tolist()
    x = np.array([features(values[:i], series.index[i]) for i in range(14, len(values))])
    y = np.array(values[14:])
    holdout = 7
    model = RandomForestRegressor(n_estimators=120, min_samples_leaf=2, random_state=42, n_jobs=1)
    model.fit(x[:-holdout], y[:-holdout])
    mae = float(mean_absolute_error(y[-holdout:], model.predict(x[-holdout:])))
    model.fit(x, y)
    predictions = []
    for offset in range(1, request.horizon + 1):
        day = pd.Timestamp(request.asOf) + pd.Timedelta(days=offset)
        prediction = max(0.0, float(model.predict([features(values, day)])[0]))
        values.append(prediction)
        predictions.append({'date': day.date().isoformat(), 'units': round(prediction, 3)})
    return {'status': 'ready', 'method': 'Random Forest with lag and calendar features',
            'trainingRows': len(x), 'validationMae': round(mae, 4),
            'validation': 'Last 7 days, one-step chronological holdout; not multi-step accuracy',
            'asOf': request.asOf.isoformat(), 'forecast': predictions}


def anomalies(request: SeriesRequest):
    series = daily_series(request, 'revenue')
    if len(series) < 30 or (series > 0).sum() < 7:
        return insufficient('Anomaly detection needs 30 completed days and 7 days with paid revenue.')
    if series.nunique() == 1:
        return {'status': 'ready', 'method': 'Constant-series check', 'trainingRows': len(series), 'anomalies': []}
    x = np.column_stack([np.log1p(series.to_numpy()), series.index.dayofweek.to_numpy()])
    model = IsolationForest(n_estimators=150, contamination='auto', random_state=42, n_jobs=1)
    labels = model.fit_predict(x)
    scores = -model.score_samples(x)
    return {'status': 'ready', 'method': 'Isolation Forest on log revenue and weekday', 'trainingRows': len(series),
            'anomalies': [{'date': day.date().isoformat(), 'revenue': float(series.iloc[i]), 'score': round(float(scores[i]), 6)}
                          for i, day in enumerate(series.index) if labels[i] == -1]}
