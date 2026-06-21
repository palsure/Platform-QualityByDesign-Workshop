import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 5,
  duration: '20s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800']
  }
};

const API_BASE = __ENV.API_BASE || 'http://localhost:8080';

export default function () {
  const health = http.get(`${API_BASE}/actuator/health`);
  check(health, { 'health UP': (r) => r.status === 200 && r.body.includes('UP') });

  const platforms = http.get(`${API_BASE}/api/v1/platforms`);
  check(platforms, { 'platforms 200': (r) => r.status === 200 });

  sleep(0.2);
}
