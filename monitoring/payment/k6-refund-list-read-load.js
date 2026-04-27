import http from 'k6/http';
import { check, fail } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const STUDENT_EMAIL = __ENV.K6_REFUND_LIST_STUDENT_EMAIL || 'perf.payment.refund.list@example.com';
const STUDENT_PASSWORD = __ENV.K6_REFUND_LIST_STUDENT_PASSWORD || 'testTEST123!@#';
const REFUND_STATUS = __ENV.K6_REFUND_LIST_STATUS || 'COMPLETED';
const PAGE = Number(__ENV.K6_REFUND_LIST_PAGE || 0);
const PAGE_SIZE = Number(__ENV.K6_REFUND_LIST_PAGE_SIZE || 100);
const EXPECTED_TOTAL_MIN = Number(__ENV.K6_REFUND_LIST_EXPECTED_TOTAL_MIN || 1000);

// 환불 목록 조회는 별도 read fixture 1000건을 기준으로 비교한다.
// dev baseline -> 인덱스 적용 -> 캐시 적용의 점진 비교를 위해
// 요청 모양과 기대 데이터 크기를 고정해둔다.
const TARGET_RPS = Number(__ENV.K6_REFUND_LIST_TARGET_RPS || 30);
const WARMUP_DURATION = __ENV.K6_REFUND_LIST_WARMUP_DURATION || '5s';
const MEASURE_DURATION = __ENV.K6_REFUND_LIST_MEASURE_DURATION || '10s';
const PRE_ALLOCATED_VUS = Number(__ENV.K6_REFUND_LIST_PRE_ALLOCATED_VUS || 120);
const MAX_VUS = Number(__ENV.K6_REFUND_LIST_MAX_VUS || 320);

const refundListReadMs = new Trend('refund_list_read_ms');
const refundListReadBusinessSuccess = new Rate('refund_list_read_business_success');

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
    'refund_list_read_business_success{phase:measure}': ['rate>0.99'],
    'refund_list_read_ms{phase:measure}': ['p(95)<400', 'p(99)<700'],
    'http_req_duration{phase:measure,api:refund_list_read}': ['p(95)<400'],
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
    fail('Missing setup token for refund list read scenario.');
  }

  const startedAt = Date.now();

  try {
    getRefundList(token);
    refundListReadMs.add(Date.now() - startedAt);
    refundListReadBusinessSuccess.add(true);
  } catch (error) {
    refundListReadBusinessSuccess.add(false);
    fail(`refund list read failed: ${error.message}`);
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

function getRefundList(token) {
  const res = http.get(
    `${BASE_URL}/v1/refunds?status=${REFUND_STATUS}&page=${PAGE}&size=${PAGE_SIZE}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { api: 'refund_list_read' },
    },
  );

  const body = parseJson(res, 'refund list');
  const content = body?.data?.content;
  const totalElements = Number(body?.data?.totalElements || 0);

  const ok =
    check(res, {
      'refund list status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'refund list has content': () => Array.isArray(content) && content.length > 0,
      'refund list totalElements is expected': () => totalElements >= EXPECTED_TOTAL_MIN,
      'refund list page size is respected': () => Array.isArray(content) && content.length <= PAGE_SIZE,
      'refund list items match requested status': () =>
        Array.isArray(content) && content.every((item) => item.status === REFUND_STATUS),
    });

  if (!ok) {
    fail(
      'refund list failed. ' +
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
