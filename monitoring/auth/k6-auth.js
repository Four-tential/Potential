// Auth 도메인 부하 테스트
//
// 시나리오: 회원가입 -> 로그인 -> 토큰 재발급 -> 로그아웃
//
// 실행:
//   K6_SCRIPT=auth/k6-auth.js STAGE=smoke docker compose --profile k6 run --rm k6 run -o experimental-prometheus-rw --tag testid=auth-smoke /scripts/auth/k6-auth.js
//   K6_SCRIPT=auth/k6-auth.js STAGE=load docker compose --profile k6 run --rm k6 run -o experimental-prometheus-rw --tag testid=auth-load /scripts/auth/k6-auth.js
//   K6_SCRIPT=auth/k6-auth.js STAGE=stress docker compose --profile k6 run --rm k6 run -o experimental-prometheus-rw --tag testid=auth-stress /scripts/auth/k6-auth.js
//   K6_SCRIPT=auth/k6-auth.js STAGE=spike docker compose --profile k6 run --rm k6 run -o experimental-prometheus-rw --tag testid=auth-spike /scripts/auth/k6-auth.js

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const PASSWORD = 'testTEST123!@#';

const STAGES = {
  smoke:  { stages: [{ duration: '60s', target: 10 }] },
  load:   { stages: [{ duration: '30s', target: 50 }, { duration: '2m', target: 50 }, { duration: '30s', target: 0 }] },
  stress: { stages: [{ duration: '30s', target: 100 }, { duration: '30s', target: 300 }, { duration: '30s', target: 500 }, { duration: '30s', target: 700 }, { duration: '30s', target: 1000 }, { duration: '30s', target: 1200 }, { duration: '30s', target: 0 }] },
  spike:  { stages: [{ duration: '10s', target: 10 }, { duration: '10s', target: 1000 }, { duration: '30s', target: 1000 }, { duration: '30s', target: 10 }] },
};

const stage = (__ENV.STAGE || 'load').toLowerCase();

export const options = {
  stages: (STAGES[stage] || STAGES.load).stages,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:signup}':  ['p(95)<2000'],
    'http_req_duration{name:login}':   ['p(95)<2000'],
    'http_req_duration{name:refresh}': ['p(95)<2000'],
    'http_req_duration{name:logout}':  ['p(95)<2000'],
  },
};

export default function () {
  const email = `k6-user-${__VU}-${__ITER}-${Date.now()}@test.com`;
  const json = { 'Content-Type': 'application/json' };

  // 회원가입
  const signupRes = http.post(
    `${BASE}/v1/auth/signup`,
    JSON.stringify({ email, password: PASSWORD, name: `k6user${__VU}`, phone: `010-9999-${String(__VU).padStart(4, '0')}` }),
    { headers: json, tags: { name: 'signup' } },
  );
  check(signupRes, { 'signup 2xx': (r) => r.status >= 200 && r.status < 300 });

  // 로그인
  const loginRes = http.post(
    `${BASE}/v1/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: json, tags: { name: 'login' } },
  );
  check(loginRes, { 'login 200': (r) => r.status === 200 });

  let accessToken = '';
  let refreshToken = '';
  try {
    accessToken = loginRes.json('data.accessToken');
    refreshToken = loginRes.json('data.refreshToken');
  } catch (_) {}

  if (!accessToken) {
    sleep(1);
    return;
  }

  // 토큰 재발급
  const refreshRes = http.post(
    `${BASE}/v1/auth/refresh`,
    JSON.stringify({ refreshToken }),
    {
      headers: { ...json, Authorization: `Bearer ${accessToken}` },
      tags: { name: 'refresh' },
    },
  );
  check(refreshRes, { 'refresh 200': (r) => r.status === 200 });

  try {
    accessToken = refreshRes.json('data.accessToken') || accessToken;
  } catch (_) {}

  // 로그아웃
  const logoutRes = http.post(
    `${BASE}/v1/auth/logout`,
    null,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
      tags: { name: 'logout' },
    },
  );
  check(logoutRes, { 'logout 2xx': (r) => r.status >= 200 && r.status < 300 });

  sleep(1);
}
