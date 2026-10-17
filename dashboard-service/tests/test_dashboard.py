from datetime import date
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from threading import Thread
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, Mock, patch

from fastapi import HTTPException
from fastapi.testclient import TestClient

from dashboard.api import create_app, Login, login_to_msme
from dashboard.report import build_report
from recommendation.metrics import MetricTools, TOOL_DESCRIPTIONS, shift_month


def dataset():
    end = date(2026, 9, 1)
    return {"profile": {"msme_id": 29, "business_name": "Example Manufacturing", "sector": "Manufacturing",
                        "is_active": "Y", "sanctioned_credit_limit": Decimal('1000000')},
            "snapshots": [{"snapshot_id": i+1, "reporting_period": shift_month(end, i-47),
                           "revenue": Decimal(200000+i*1000), "total_receivables": Decimal('120000'),
                           "overdue_receivables": Decimal('30000'), "credit_used": Decimal('450000')}
                          for i in range(48)], "assessments": [], "indicators": []}


class ReportTests(unittest.TestCase):
    def test_all_metrics_match_recommendation_tools_exactly(self):
        data=dataset();end=date(2026,9,1);report=build_report(data,end);tools=MetricTools(data,end)
        for name in TOOL_DESCRIPTIONS:
            self.assertEqual(report['evidence'][name],tools.execute(name))
        self.assertEqual(len(report['series']),48)
        self.assertEqual(len(report['series'][-1]['metrics']),21)
        for group in TOOL_DESCRIPTIONS:
            if group != 'business_context':
                for key,item in tools.execute(group).items():
                    self.assertEqual(report['series'][-1]['metrics'][group+'.'+key],item)

    def test_missing_month_is_gap_not_zero(self):
        data=dataset();del data['snapshots'][-2]
        report=build_report(data,date(2026,9,1))
        self.assertIsNone(report['series'][-2]['snapshot'])
        self.assertFalse(report['series'][-2]['available'])
        self.assertIsNone(report['evidence']['revenue_metrics']['revenue_mom_growth_pct']['value'])

    def test_missing_selected_month_is_error(self):
        data=dataset();data['snapshots'].pop()
        with self.assertRaises(LookupError):build_report(data,date(2026,9,1))

    def test_zero_receivables_kept_unavailable(self):
        data=dataset();data['snapshots'][-1].update(total_receivables=Decimal(0),overdue_receivables=Decimal(0))
        report=build_report(data,date(2026,9,1))
        self.assertIsNone(report['evidence']['liquidity_metrics']['overdue_ratio_pct']['value'])

    def test_early_periods_do_not_include_later_snapshots(self):
        report=build_report(dataset(),date(2026,9,1))
        self.assertIsNone(report['series'][0]['metrics']['revenue_metrics.revenue_yoy_growth_pct']['value'])
        self.assertIsNotNone(report['series'][12]['metrics']['revenue_metrics.revenue_yoy_growth_pct']['value'])

    def test_duplicate_month_rejected(self):
        data=dataset();data['snapshots'].append(data['snapshots'][-1])
        with self.assertRaises(ValueError):build_report(data,date(2026,9,1))


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.verifier=Mock();self.verifier.verify.return_value=(7,'RM')
        self.access=Mock();self.repository=Mock();self.repository.load.return_value=dataset()
        self.app=create_app(settings=SimpleNamespace(),verifier=self.verifier,access=self.access,repository=self.repository,discovery=False)
        self.client=TestClient(self.app);self.client.__enter__()

    def tearDown(self):self.client.__exit__(None,None,None)

    def get(self,query='2026-09',id=29):
        return self.client.get(f'/api/dashboard/msmes/{id}?as_of={query}',headers={'Authorization':'Bearer test'})

    def test_dashboard_endpoint(self):
        response=self.get();self.assertEqual(response.status_code,200)
        self.assertEqual(response.json()['msme_id'],29)
        self.assertEqual(response.json()['credit_limit'],1000000)
        self.access.authorize.assert_called_once_with(7,'RM',29)
        self.assertEqual(response.headers['cache-control'],'no-store')

    def test_static_page_public_and_no_external_assets(self):
        response=self.client.get('/');self.assertEqual(response.status_code,200)
        self.assertIn('Financial Observatory',response.text)
        self.assertIn("default-src 'self'",response.headers['content-security-policy'])
        self.assertEqual(self.client.get('/static/app.js').status_code,200)
        self.assertEqual(self.client.get('/docs').status_code,404)
        self.repository.load.assert_not_called()

    def test_missing_token_rejected_before_database(self):
        response=self.client.get('/api/dashboard/msmes/29?as_of=2026-09')
        self.assertEqual(response.status_code,401);self.repository.load.assert_not_called()

    def test_invalid_token_rejected(self):
        self.verifier.verify.side_effect=HTTPException(401,'Invalid token')
        self.assertEqual(self.get().status_code,401);self.access.authorize.assert_not_called()

    def test_assignment_denied_before_financial_load(self):
        self.access.authorize.side_effect=HTTPException(403,'Not assigned')
        self.assertEqual(self.get().status_code,403);self.repository.load.assert_not_called()

    def test_invalid_period_and_id(self):
        for period in ('2026-13','2026-09-01','9999-01'):
            self.assertEqual(self.get(period).status_code,422)
        self.assertEqual(self.get(id=0).status_code,422)
        self.repository.load.assert_not_called()

    def test_missing_month_response(self):
        self.repository.load.return_value['snapshots'].pop()
        self.assertEqual(self.get().status_code,404)

    def test_database_error_is_redacted(self):
        self.repository.load.side_effect=RuntimeError('private credentials SQL')
        response=self.get();self.assertEqual(response.status_code,503)
        self.assertNotIn('private',response.text)

    def test_invalid_records_response(self):
        self.repository.load.return_value['snapshots'][-1]['revenue']=-1
        self.assertEqual(self.get().status_code,422)

    def test_login_uses_existing_service_and_verifies_returned_token(self):
        with patch('dashboard.api.login_to_msme',return_value={'access_token':'signed-token'}) as login:
            response=self.client.post('/api/dashboard/login',json={'email':'a@b.test','password':'example'})
            self.assertEqual(response.status_code,200)
            self.verifier.verify.assert_called_once_with('signed-token')
            self.assertEqual(login.call_args.args[0].password,'example')

    def test_login_does_not_accept_remote_url(self):
        with self.assertRaises(HTTPException):
            login_to_msme(Login(email='a@b.test',password='example'),'http://remote.invalid/login')


class DiscoveryTests(unittest.TestCase):
    def app(self):
        return create_app(settings=SimpleNamespace(),verifier=Mock(),access=Mock(),repository=Mock())

    def test_custom_port_registration_and_shutdown(self):
        client=Mock(start=AsyncMock(),stop=AsyncMock())
        with patch.dict(os.environ,{'DASHBOARD_PORT':'8090','DASHBOARD_HOST':'localhost',
                                   'EUREKA_SERVER_URL':'http://localhost:8080/eureka/'}), \
             patch('py_eureka_client.eureka_client.EurekaClient',return_value=client) as factory:
            with TestClient(self.app()) as http:
                self.assertEqual(http.get('/health').status_code,200)
            self.assertEqual(factory.call_args.kwargs['app_name'],'dashboard-service')
            self.assertEqual(factory.call_args.kwargs['instance_port'],8090)
            self.assertFalse(factory.call_args.kwargs['should_discover'])
            client.start.assert_awaited_once();client.stop.assert_awaited_once()

    def test_failed_start_still_cleans_up_client(self):
        client=Mock(start=AsyncMock(side_effect=RuntimeError('test start failure')),stop=AsyncMock())
        with patch('py_eureka_client.eureka_client.EurekaClient',return_value=client):
            with self.assertRaises(RuntimeError):
                with TestClient(self.app()):pass
        client.stop.assert_awaited_once()

    def test_actual_registry_payload_and_deregistration(self):
        requests=[]
        class Registry(BaseHTTPRequestHandler):
            def do_POST(self):
                requests.append(('POST',self.path,json.loads(self.rfile.read(int(self.headers['Content-Length'])))))
                self.send_response(204);self.end_headers()
            def do_DELETE(self):
                requests.append(('DELETE',self.path,None))
                self.send_response(200);self.end_headers()
            def log_message(self,*args):pass
        server=ThreadingHTTPServer(('127.0.0.1',0),Registry)
        thread=Thread(target=server.serve_forever,daemon=True);thread.start()
        try:
            with patch.dict(os.environ,{'EUREKA_SERVER_URL':f'http://127.0.0.1:{server.server_port}/eureka/',
                                       'DASHBOARD_HOST':'localhost','DASHBOARD_PORT':'8086'}):
                with TestClient(self.app()) as http:
                    self.assertEqual(http.get('/health').json()['service'],'dashboard-service')
            registration=next(r for r in requests if r[0]=='POST')
            instance=registration[2]['instance']
            self.assertEqual(instance['app'].upper(),'DASHBOARD-SERVICE')
            self.assertEqual(instance['port']['$'],8086)
            self.assertEqual(instance['healthCheckUrl'],'http://localhost:8086/health')
            self.assertEqual(instance['homePageUrl'],'http://localhost:8086/')
            self.assertTrue(any(r[0]=='DELETE' for r in requests))
        finally:
            server.shutdown();server.server_close();thread.join(timeout=2)


if __name__=='__main__':unittest.main()
