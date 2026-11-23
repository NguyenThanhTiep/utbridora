from fastapi.testclient import TestClient
from app.main import create_app
from test_api import request, HEADERS


def test_advisory_template_echo_keeps_query_passage_protocol(settings, engine):
    data = request()
    data['templateVersion'] = 'school-advisory-text-v1'
    data['query'] = 'Nhu cầu thực tập phát triển API Java'
    data['passages'][0]['text'] = 'Dự án xây API và truy vấn cơ sở dữ liệu'
    with TestClient(create_app(settings, loader=lambda _: engine)) as client:
        response = client.post('/internal/v1/similarity', json=data, headers=HEADERS)
    assert response.status_code == 200
    assert response.json()['templateVersion'] == data['templateVersion']
    assert response.json()['results'][0]['status'] == 'READY'


def test_unsupported_template_is_rejected_without_echoing_input(settings, engine):
    data = request()
    data['templateVersion'] = 'unreviewed-template-sensitive'
    with TestClient(create_app(settings, loader=lambda _: engine)) as client:
        response = client.post('/internal/v1/similarity', json=data, headers=HEADERS)
    assert response.status_code == 422
    assert 'unreviewed-template-sensitive' not in response.text


def test_emoji_codepoint_limit_works_with_advisory_template(settings, engine):
    data = request()
    data['templateVersion'] = 'school-advisory-text-v1'
    data['passages'][0]['text'] = '😀' * 8193
    with TestClient(create_app(settings, loader=lambda _: engine)) as client:
        response = client.post('/internal/v1/similarity', json=data, headers=HEADERS)
    assert response.status_code == 422
