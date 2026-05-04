import http from 'k6/http';
import { check, fail } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const STUDENT_EMAIL = __ENV.K6_PAYMENT_LIST_STUDENT_EMAIL || 'perf.payment.webhook@example.com';
const STUDENT_PASSWORD = __ENV.K6_PAYMENT_LIST_STUDENT_PASSWORD || 'testTEST123!@#';
const PAYMENT_STATUS = __ENV.K6_PAYMENT_LIST_STATUS || 'PENDING';
const PAGE = Number(__ENV.K6_PAYMENT_LIST_PAGE || 0);
const PAGE_SIZE = Number(__ENV.K6_PAYMENT_LIST_PAGE_SIZE || 100);
const EXPECTED_TOTAL_MIN = Number(__ENV.K6_PAYMENT_LIST_EXPECTED_TOTAL_MIN || 500);

// 기존 webhook payment seed는 한 학생 기준 PENDING payment 500건이다.
// 지금 브랜치에서는 seed를 추가로 만들지 않으므로 기본 기대값도 500으로 둔다.
// 나중에 seed가 1000건으로 늘어나면 K6_PAYMENT_LIST_EXPECTED_TOTAL_MIN=1000 만 주면
// 같은 스크립트로 인덱스/캐시 전후를 그대로 비교할 수 있다.
const TARGET_RPS = Number(__ENV.K6_PAYMENT_LIST_TARGET_RPS || 30);
const WARMUP_DURATION = __ENV.K6_PAYMENT_LIST_WARMUP_DURATION || '5s';
const MEASURE_DURATION = __ENV.K6_PAYMENT_LIST_MEASURE_DURATION || '10s';
const PRE_ALLOCATED_VUS = Number(__ENV.K6_PAYMENT_LIST_PRE_ALLOCATED_VUS || 120);
const MAX_VUS = Number(__ENV.K6_PAYMENT_LIST_MAX_VUS || 320);

const paymentListReadMs = new Trend('payment_list_read_ms');
const paymentListReadBusinessSuccess = new Rate('payment_list_read_business_success');

const COMMON_SCENARIO = {
  executor: 'constant-arrival-rate',
  rate: TARGET_RPS,
  timeUnit: '1s',
  preAllocatedVUs: PRE_ALLOCATED_VUS,
  maxVUs: MAX_VUS,
  exec: 'scenario',
};

export const options = {
  scenarios: {
    warmup: {
      ...COMMON_SCENARIO,
      duration: WARMUP_DURATION,
      tags: { phase: 'warmup' },
    },
    measure: {
      ...COMMON_SCENARIO,
      duration: MEASURE_DURATION,
      startTime: WARMUP_DURATION,
      tags: { phase: 'measure' },
    },
  },
  thresholds: {
    'http_req_failed{phase:measure}': ['rate<0.01'],
    'checks{phase:measure}': ['rate>0.99'],
    'payment_list_read_business_success{phase:measure}': ['rate>0.99'],
    'payment_list_read_ms{phase:measure}': ['p(95)<400', 'p(99)<700'],
    'http_req_duration{phase:measure,api:payment_list_read}': ['p(95)<400'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
  return {
    token: loginOnce(STUDENT_EMAIL, STUDENT_PASSWORD),
  };
}

export function scenario(setupData) {
  const token = setupData?.token;

  if (!token) {
    fail('Missing setup token for payment list read scenario.');
  }

  const startedAt = Date.now();

  try {
    getPaymentList(token);
    paymentListReadMs.add(Date.now() - startedAt);
    paymentListReadBusinessSuccess.add(true);
  } catch (error) {
    paymentListReadBusinessSuccess.add(false);
    fail(`payment list read failed: ${error.message}`);
  }
}

function loginOnce(email, password) {
  const res = http.post(
    `${BASE_URL}/v1/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { api: 'auth_login' },
    },
  );

  const body = parseJson(res, 'login');
  const ok =
    check(res, {
      'login status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'login returns accessToken': (b) => Boolean(b?.data?.accessToken),
    });

  if (!ok) {
    fail(`login failed. status=${res.status}, body=${res.body}`);
  }

  return body.data.accessToken;
}

function getPaymentList(token) {
  const res = http.get(
    `${BASE_URL}/v1/payments?status=${PAYMENT_STATUS}&page=${PAGE}&size=${PAGE_SIZE}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { api: 'payment_list_read' },
    },
  );

  const body = parseJson(res, 'payment list');
  const content = body?.data?.content;
  const totalElements = Number(body?.data?.totalElements || 0);

  const ok =
    check(res, {
      'payment list status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'payment list has content': () => Array.isArray(content) && content.length > 0,
      'payment list totalElements is expected': () => totalElements >= EXPECTED_TOTAL_MIN,
      'payment list page size is respected': () => Array.isArray(content) && content.length <= PAGE_SIZE,
      'payment list items match requested status': () =>
        Array.isArray(content) && content.every((item) => item.status === PAYMENT_STATUS),
    });

  if (!ok) {
    fail(
      'payment list failed. ' +
        `status=${res.status}, page=${PAGE}, size=${PAGE_SIZE}, ` +
        `expectedTotalMin=${EXPECTED_TOTAL_MIN}, body=${res.body}`,
    );
  }
}

function parseJson(res, label) {
  try {
    return res.json();
  } catch (error) {
    fail(`${label} did not return JSON. status=${res.status}, body=${res.body}`);
  }
}
