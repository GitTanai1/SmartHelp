import http from 'k6/http';
import { check, sleep } from 'k6';

// Safe-by-default read-only API scenario. Point it only at a disposable or
// approved environment; this script does not create tickets or start agents.
const baseUrl = (__ENV.SMARTHELP_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '');
const token = __ENV.SMARTHELP_BEARER_TOKEN;

export const options = {
  scenarios: {
    ticket_reads: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: Number(__ENV.SMARTHELP_LOAD_VUS || 5) },
        { duration: '60s', target: Number(__ENV.SMARTHELP_LOAD_VUS || 5) },
        { duration: '15s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

const params = {
  headers: token ? { Authorization: `Bearer ${token}` } : {},
  tags: { operation: 'ticket_list' },
};

export default function () {
  const health = http.get(baseUrl.replace(/\/api\/v1$/, '') + '/actuator/health/readiness', params);
  check(health, { 'readiness is up': (response) => response.status === 200 });

  const tickets = http.get(`${baseUrl}/tickets`, params);
  check(tickets, {
    'ticket list returns 200': (response) => response.status === 200,
    'ticket list is JSON': (response) => (response.headers['Content-Type'] || '').includes('application/json'),
  });
  sleep(1);
}
