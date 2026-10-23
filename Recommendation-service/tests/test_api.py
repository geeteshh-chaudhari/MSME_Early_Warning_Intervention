import base64
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from datetime import date, datetime, timedelta, timezone
import os
from threading import Thread
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, Mock, patch

from fastapi import HTTPException
from fastapi.testclient import TestClient
import jwt

from recommendation.api import create_app
from recommendation.api_security import OracleAccess, TokenVerifier
from test_agent import dataset

KEY = b"test-only-key-not-a-real-secret-32bytes"
ISSUER = "msme-early-warning"


def token(**overrides):
    now = datetime.now(timezone.utc)
    claims = dict(sub="7", role="RM", iss=ISSUER, iat=now, exp=now + timedelta(minutes=5))
    claims.update(overrides)
    return jwt.encode(claims, KEY, algorithm="HS256")


class ApiTests(unittest.TestCase):
    def test_public_openapi_documents_auth_and_does_not_read_financial_data(self):
        response = self.client.get("/openapi.json")
        self.assertEqual(response.status_code, 200)
        schema = response.json()
        self.assertEqual(schema["servers"][0]["url"], "/")
        self.assertNotIn("/health", schema["paths"])
        operation = schema["paths"]["/api/recommendations"]["post"]
        self.assertEqual(operation["security"], [{"bearerAuth": []}])
        self.assertNotIn("429", operation["responses"])
        self.assertIn("evidence", operation["responses"]["200"]["content"]["application/json"]["schema"]["properties"])
        self.access.authorize.assert_not_called()
        self.repo.load.assert_not_called()
        self.assertEqual(self.client.get("/docs").status_code, 404)

    def setUp(self):
        self.access = Mock()
        self.repo = Mock()
        self.repo.load.return_value = dataset()
        self.repo.save_recommendation_for_warning.return_value = True
        self.repo.recommendation_for_warning.return_value = {"warning_id": 10, "warning_status": "OPEN", "payload": None}
        self.app = create_app(settings=SimpleNamespace(), verifier=TokenVerifier(KEY, ISSUER),
                              access=self.access, repository=self.repo, discovery=False)
        self.client = TestClient(self.app, raise_server_exceptions=False)
        self.client.__enter__()
        self.body = {"warning_id": 10, "msme_id": 1, "as_of": "2026-09", "metrics_only": True}

    def tearDown(self):
        self.client.__exit__(None, None, None)

    def post(self, value=None, bearer=None):
        return self.client.post("/api/recommendations", json=value or self.body,
                                headers={"Authorization": "Bearer " + (bearer or token())})

    def test_metrics_reuses_cli_tools_and_json_serialization(self):
        result = self.post()
        self.assertEqual(result.status_code, 200, result.text)
        self.assertEqual(result.json()["msme_id"], 1)
        self.assertEqual(result.json()["as_of_month"], "2026-09-01")
        self.assertEqual(result.headers["cache-control"], "no-store")
        self.access.authorize.assert_called_once_with(7, "RM", 1)
        self.repo.load.assert_called_once_with(1, date(2026, 9, 1))
        self.repo.save_recommendation_for_warning.assert_not_called()

    def test_recommendation_invokes_existing_agent(self):
        with patch("recommendation.api.RecommendationAgent") as agent, patch("recommendation.api.OllamaClient"):
            agent.return_value.run.return_value = {
                "status": "generated", "evidence": {},
                "recommendation": {"summary": "Stable trend", "actions": []}}
            result = self.post(dict(self.body, metrics_only=False, question="Review collections"))
            self.assertEqual(result.status_code, 200)
            agent.return_value.run.assert_called_once_with("Review collections")
            self.repo.save_recommendation_for_warning.assert_called_once()
            warning_id, msme_id, period, payload = self.repo.save_recommendation_for_warning.call_args.args
            self.assertEqual(warning_id, 10)
            self.assertEqual((msme_id, period), (1, date(2026, 9, 1)))
            self.assertEqual(json.loads(payload), result.json())

    def test_saved_json_is_returned_without_model_history_reads_or_writes(self):
        saved = {"status": "generated", "evidence": {"original": {}}, "model": "original-model",
                 "recommendation": {"summary": "Previously saved advice", "actions": []}}
        self.repo.recommendation_for_warning.return_value["payload"] = json.dumps(saved)
        with patch("recommendation.api.RecommendationAgent") as agent, patch("recommendation.api.OllamaClient") as llm:
            response = self.post(dict(self.body, metrics_only=False))
        self.assertEqual(response.status_code, 200)
        result = response.json()
        self.assertEqual(result["recommendation"], saved["recommendation"])
        self.assertEqual(result["evidence"], saved["evidence"])
        self.assertEqual(result["recommendation_source"], "saved")
        self.assertEqual(result["warning_id"], 10)
        self.repo.recommendation_for_warning.assert_called_once_with(10, 1, date(2026, 9, 1))
        self.repo.load.assert_not_called()
        self.repo.save_recommendation_for_warning.assert_not_called()
        agent.assert_not_called()
        llm.assert_not_called()

    def test_legacy_recommendations_are_displayed_without_generation(self):
        for payload in ('{"summary":"Existing advice","actions":[]}', 'Existing advice'):
            with self.subTest(payload=payload), patch("recommendation.api.RecommendationAgent") as agent:
                self.repo.recommendation_for_warning.return_value["payload"] = payload
                response = self.post(dict(self.body, metrics_only=False))
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json()["recommendation"]["summary"], "Existing advice")
                agent.assert_not_called()
        self.repo.save_recommendation_for_warning.assert_not_called()

    def test_empty_values_generate_then_save(self):
        for payload in (None, "", "  "):
            with self.subTest(payload=payload), patch("recommendation.api.RecommendationAgent") as agent, patch("recommendation.api.OllamaClient"):
                self.repo.recommendation_for_warning.return_value["payload"] = payload
                agent.return_value.run.return_value = {"status": "generated", "recommendation": {
                    "summary": "New advice", "actions": []}}
                self.repo.save_recommendation_for_warning.reset_mock()
                response = self.post(dict(self.body, metrics_only=False))
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json()["recommendation_source"], "generated")
                agent.return_value.run.assert_called_once()
                self.repo.save_recommendation_for_warning.assert_called_once()

    def test_nonempty_placeholders_and_malformed_history_are_never_replaced(self):
        for payload in ('{"status":"pending","actions":[]}',
                        '{"status":"unavailable","actions":[]}', '{}', '{invalid json'):
            with self.subTest(payload=payload), patch("recommendation.api.RecommendationAgent") as agent:
                self.repo.recommendation_for_warning.return_value["payload"] = payload
                response = self.post(dict(self.body, metrics_only=False))
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json()["stored_payload"], payload)
                agent.assert_not_called()
        self.repo.save_recommendation_for_warning.assert_not_called()

    def test_warning_id_is_required_for_generation(self):
        body = dict(self.body, metrics_only=False)
        body.pop("warning_id")
        self.assertEqual(self.post(body).status_code, 400)
        self.repo.load.assert_not_called()

    def test_admin_can_retry_unavailable_marker_for_exact_warning(self):
        self.repo.recommendation_for_warning.return_value["payload"] = "reccommendation service not available"
        with patch("recommendation.api.RecommendationAgent") as agent, patch("recommendation.api.OllamaClient"):
            agent.return_value.run.return_value = {"status": "generated", "recommendation": {"summary": "Saved advice", "actions": []}}
            response = self.post(dict(self.body, metrics_only=False), bearer=token(role="RISK_ADMIN"))
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json()["warning_id"], 10)
            self.repo.save_recommendation_for_warning.assert_called_once()
            self.access.authorize.assert_called_once_with(7, "RISK_ADMIN", 1)

    def test_missing_or_closed_empty_warning_stops_before_model(self):
        for warning in (None, {"warning_id": 10, "warning_status": "CLOSED", "payload": None},
                        {"warning_id": 10, "warning_status": "CLOSED_REVIEWED", "payload": None}):
            with self.subTest(warning=warning), patch("recommendation.api.RecommendationAgent") as agent:
                self.repo.recommendation_for_warning.return_value = warning
                self.assertEqual(self.post(dict(self.body, metrics_only=False)).status_code, 404 if warning is None else 409)
                agent.assert_not_called()
        self.repo.load.assert_not_called()
        self.repo.save_recommendation_for_warning.assert_not_called()

    def test_closed_warning_cannot_be_selected_for_generation(self):
        self.repo.recommendation_for_warning.return_value.update(warning_status="CLOSED", payload="Existing advice")
        self.assertEqual(self.post(dict(self.body, metrics_only=False)).status_code, 409)
        self.repo.save_recommendation_for_warning.assert_not_called()

    def test_lookup_failure_does_not_generate_and_lock_is_released(self):
        self.repo.recommendation_for_warning.side_effect = RuntimeError("private SQL credentials")
        with patch("recommendation.api.RecommendationAgent") as agent:
            response = self.post(dict(self.body, metrics_only=False))
            self.assertEqual(response.status_code, 503)
            self.assertNotIn("private", response.text)
            agent.assert_not_called()
        self.assertEqual(self.post().status_code, 200)

    def test_save_failure_is_reported_and_releases_lock(self):
        for error, status in ((ValueError("save failed"), 503),
                              (RuntimeError("private SQL credentials"), 503)):
            with self.subTest(status=status), patch("recommendation.api.RecommendationAgent") as agent, patch("recommendation.api.OllamaClient"):
                agent.return_value.run.return_value = {
                    "status": "generated", "recommendation": {"summary": "Stable trend", "actions": []}}
                self.repo.save_recommendation_for_warning.side_effect = error
                response = self.post(dict(self.body, metrics_only=False))
                self.assertEqual(response.status_code, status)
                self.assertNotIn("private", response.text)
                self.assertEqual(self.post().status_code, 200)

    def test_insufficient_data_does_not_overwrite_saved_recommendation(self):
        with patch("recommendation.api.RecommendationAgent") as agent, patch("recommendation.api.OllamaClient"):
            agent.return_value.run.return_value = {"status": "insufficient_data", "evidence": {}}
            self.assertEqual(self.post(dict(self.body, metrics_only=False)).status_code, 200)
        self.repo.save_recommendation_for_warning.assert_called_once_with(
            10, 1, date(2026, 9, 1), "reccommendation service not available")

    def test_missing_token_rejected_before_database(self):
        result = self.client.post("/api/recommendations", json=self.body)
        self.assertEqual(result.status_code, 401)
        self.assertEqual(result.headers["www-authenticate"], "Bearer")
        self.access.authorize.assert_not_called()
        self.repo.load.assert_not_called()

    def test_admin_and_representative_rejected_before_database_or_model(self):
        for role in ("MSME_REP",):
            with self.subTest(role=role):
                self.assertEqual(self.post(bearer=token(role=role)).status_code, 403)
        self.access.authorize.assert_not_called()
        self.repo.load.assert_not_called()

    def test_expired_wrong_issuer_wrong_key_and_malformed_tokens(self):
        tokens = [token(exp=datetime.now(timezone.utc)-timedelta(minutes=1)),
                  token(iss="other"), token(sub="bad"), "garbage",
                  jwt.encode(dict(sub="7", role="RM", iss=ISSUER, iat=1, exp=9999999999),
                             b"wrong-key"*4, algorithm="HS256"),
                  token(iat=datetime.now(timezone.utc)+timedelta(days=1))]
        for bearer in tokens:
            with self.subTest(bearer=bearer[:10]):
                self.assertEqual(self.post(bearer=bearer).status_code, 401)
        self.access.authorize.assert_not_called()

    def test_missing_required_claim(self):
        for claim in ("exp", "iat", "iss", "sub", "role"):
            claims = jwt.decode(token(), KEY, algorithms=["HS256"])
            del claims[claim]
            self.assertEqual(self.post(bearer=jwt.encode(claims, KEY, algorithm="HS256")).status_code, 401)

    def test_access_denied_never_loads_financial_history(self):
        self.access.authorize.side_effect = HTTPException(403, "Not assigned")
        self.assertEqual(self.post().status_code, 403)
        self.repo.load.assert_not_called()
        self.repo.save_recommendation_for_warning.assert_not_called()

    def test_invalid_input(self):
        invalid = [dict(self.body, msme_id=0), dict(self.body, msme_id="1"),
                   dict(self.body, as_of="2026-13"), dict(self.body, as_of="2026-09-01"),
                   dict(self.body, as_of="9999-01"), dict(self.body, extra="ignore auth"),
                   dict(self.body, question="x"*2001)]
        for body in invalid:
            self.assertEqual(self.post(body).status_code, 400)
        self.repo.load.assert_not_called()

    def test_missing_requested_snapshot(self):
        self.repo.load.return_value["snapshots"].pop()
        self.assertEqual(self.post().status_code, 404)

    def test_model_validation_failure_and_lock_released(self):
        with patch("recommendation.api.RecommendationAgent") as agent, patch("recommendation.api.OllamaClient"):
            agent.return_value.run.side_effect = ValueError("private model content")
            result = self.post(dict(self.body, metrics_only=False))
            self.assertEqual(result.status_code, 502)
            self.assertIn("failed validation", result.json()["detail"])
            self.assertNotIn("private", result.text)
            self.repo.save_recommendation_for_warning.assert_called_once_with(
                10, 1, date(2026, 9, 1), "reccommendation service not available")
        self.assertEqual(self.post().status_code, 200)

    def test_model_runtime_failure_is_distinguished_from_validation_failure(self):
        with patch("recommendation.api.RecommendationAgent") as agent, patch("recommendation.api.OllamaClient"):
            agent.return_value.run.side_effect = RuntimeError("Local Ollama connection failed or timed out.")
            result = self.post(dict(self.body, metrics_only=False))
            self.assertEqual(result.status_code, 502)
            self.assertIn("did not complete successfully", result.json()["detail"])
            self.assertNotIn("failed validation", result.json()["detail"])
            self.repo.save_recommendation_for_warning.assert_called_once_with(
                10, 1, date(2026, 9, 1), "reccommendation service not available")
        self.assertEqual(self.post().status_code, 200)

    def test_metrics_do_not_wait_for_model_lock(self):
        self.app.state.work_lock.acquire()
        try:
            result = self.post()
            self.assertEqual(result.status_code, 200)
            self.repo.load.assert_called_once()
        finally:
            self.app.state.work_lock.release()

    def test_dependency_errors_redacted(self):
        self.access.authorize.side_effect = Exception("secret password connection SQL")
        result = self.post()
        self.assertEqual(result.status_code, 503)
        self.assertNotIn("secret", result.text)

    def test_health_and_schema_are_public_but_local_ui_is_disabled(self):
        self.assertEqual(self.client.get("/health").status_code, 200)
        for path in ("/docs", "/redoc"):
            self.assertEqual(self.client.get(path).status_code, 404)


class AuthorizationTests(unittest.TestCase):
    def test_role_and_assignment_matrix(self):
        for role, user_msme, assigned_rm, allowed in (
            ("RISK_ADMIN", None, 99, True), ("RM", None, 7, True),
            ("RM", None, 99, False), ("MSME_REP", 1, 99, False),
            ("MSME_REP", 2, 7, False), ("OTHER", 1, 7, False)):
            with self.subTest(role=role, allowed=allowed):
                args = ((role, user_msme, "ACTIVE"), (assigned_rm, "Y"), 7, role, 1)
                if allowed:
                    OracleAccess.enforce(*args)
                else:
                    with self.assertRaises(HTTPException) as error:
                        OracleAccess.enforce(*args)
                    self.assertEqual(error.exception.status_code, 403)

    def test_dashboard_representative_scope_is_opt_in_and_business_specific(self):
        OracleAccess.enforce(("MSME_REP", 1, "ACTIVE"), (7, "Y"), 9, "MSME_REP", 1, allow_representatives=True)
        with self.assertRaises(HTTPException) as error:
            OracleAccess.enforce(("MSME_REP", 1, "ACTIVE"), (7, "Y"), 9, "MSME_REP", 2, allow_representatives=True)
        self.assertEqual(error.exception.status_code, 403)
        with self.assertRaises(HTTPException):
            OracleAccess.enforce(("MSME_REP", 1, "ACTIVE"), (7, "Y"), 9, "MSME_REP", 1)

    def test_deactivated_missing_and_changed_role_users(self):
        for user in (None, ("RM", None, "INACTIVE"), ("RISK_ADMIN", None, "ACTIVE")):
            with self.assertRaises(HTTPException) as error:
                OracleAccess.enforce(user, (7, "Y"), 7, "RM", 1)
            self.assertEqual(error.exception.status_code, 401)

    def test_inactive_business_and_missing_business(self):
        for business in ((7, "N"), None):
            with self.assertRaises(HTTPException) as error:
                OracleAccess.enforce(("RM", None, "ACTIVE"), business, 7, "RM", 1)
            self.assertEqual(error.exception.status_code, 403)

    def test_secret_base64_decoded_and_missing_secret_fails_closed(self):
        with patch.dict(os.environ, {"JWT_SECRET": base64.b64encode(KEY).decode()}):
            verifier = TokenVerifier.from_environment()
            self.assertEqual(verifier.verify(token()), (7, "RM"))
        with patch.dict(os.environ, {"JWT_SECRET": ""}):
            with self.assertRaises(ValueError):
                TokenVerifier.from_environment()

    def test_access_queries_bound_and_read_only(self):
        cur = Mock()
        cur.__enter__ = Mock(return_value=cur)
        cur.__exit__ = Mock(return_value=False)
        cur.fetchone.side_effect = [("RM", None, "ACTIVE"), (7, "Y")]
        conn = Mock()
        conn.__enter__ = Mock(return_value=conn)
        conn.__exit__ = Mock(return_value=False)
        conn.cursor.return_value = cur
        with patch("oracledb.connect", return_value=conn):
            cfg = SimpleNamespace(user="u", password="p", dsn="d", call_timeout_ms=1000, profile_schema="PROFILE")
            OracleAccess(cfg, "AUTH").authorize(7, "RM", 1)
        queries = cur.execute.call_args_list
        self.assertEqual(queries[0].args[0], "SET TRANSACTION READ ONLY")
        self.assertIn("AUTH.APP_USER", queries[1].args[0])
        self.assertEqual(queries[1].args[1], {"user_id": 7})
        self.assertIn("PROFILE.MSME", queries[2].args[0])
        conn.rollback.assert_called_once()

    def test_discovery_start_and_shutdown(self):
        client = Mock(start=AsyncMock(), stop=AsyncMock())
        with patch("py_eureka_client.eureka_client.EurekaClient", return_value=client) as factory:
            app = create_app(settings=SimpleNamespace(), verifier=TokenVerifier(KEY, ISSUER),
                             access=Mock(), repository=Mock())
            with TestClient(app) as http:
                self.assertEqual(http.get("/health").status_code, 200)
            self.assertEqual(factory.call_args.kwargs["app_name"], "recommendation-service")
            self.assertEqual(factory.call_args.kwargs["instance_port"], 8085)
            client.start.assert_awaited_once()
            client.stop.assert_awaited_once()

    def test_actual_eureka_http_registration_and_deregistration(self):
        requests = []
        class Registry(BaseHTTPRequestHandler):
            def do_POST(self):
                payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                requests.append(("POST", self.path, payload))
                self.send_response(204)
                self.end_headers()

            def do_DELETE(self):
                requests.append(("DELETE", self.path, None))
                self.send_response(200)
                self.end_headers()

            def log_message(self, *args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Registry)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with patch.dict(os.environ, {
                "EUREKA_SERVER_URL": f"http://127.0.0.1:{server.server_port}/eureka/",
                "SERVICE_HOST": "localhost", "SERVICE_PORT": "8085"}):
                app = create_app(settings=SimpleNamespace(), verifier=TokenVerifier(KEY, ISSUER),
                                 access=Mock(), repository=Mock())
                with TestClient(app) as http:
                    self.assertEqual(http.get("/health").status_code, 200)
                registration = next(r for r in requests if r[0] == "POST")
                instance = registration[2]["instance"]
                self.assertEqual(instance["app"].upper(), "RECOMMENDATION-SERVICE")
                self.assertEqual(instance["port"]["$"], 8085)
                self.assertEqual(instance["healthCheckUrl"], "http://localhost:8085/health")
                self.assertTrue(any(r[0] == "DELETE" for r in requests))
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
