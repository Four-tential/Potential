import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const DEFAULT_STUDENT_EMAIL = 'perf.payment.refund@example.com';
const DEFAULT_STUDENT_PASSWORD = 'testTEST123!@#';
const DEFAULT_ORDER_ID_PREFIX = '94000000-0000-0000-0000-';
const DEFAULT_PAYMENT_ID_PREFIX = '95000000-0000-0000-0000-';
const DEFAULT_FIXTURE_ID_START = 1;
const DEFAULT_FIXTURE_ID_COUNT = 500;
const REFUND_LIST_PAGE_SIZE = 100;

// 학생 주문 취소 환불은 전체 트래픽 중 저빈도 쓰기 경로에 가깝다.
// 팀 프로젝트 규모를 DAU 500~3000, peak RPS 15~45로 볼 때,
// 환불 baseline은 보수적인 하한선인 15 RPS를 먼저 기준으로 잡는다.
// 15 RPS * (5초 warmup + 10초 measure) = 약 225 iterations 이므로
// 500개 paid order/payment fixture 안에서 충분히 fresh 경로를 유지할 수 있다.
const TARGET_RPS = 15;
const WARMUP_DURATION = '5s';
const MEASURE_DURATION = '10s';
const PRE_ALLOCATED_VUS = 60;
const MAX_VUS = 160;
const ALLOW_FIXTURE_REUSE = false;
const WARMUP_FIXTURE_OFFSET = estimatePlannedIterations(TARGET_RPS, WARMUP_DURATION);

const TOKEN_CACHE = new Map();
let cachedFixtures = null;

const studentCancelRefundFlowMs = new Trend('student_cancel_refund_flow_ms');
const studentCancelRefundBusinessSuccess = new Rate('student_cancel_refund_business_success');

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
    'student_cancel_refund_business_success{phase:measure}': ['rate>0.99'],
    'student_cancel_refund_flow_ms{phase:measure}': ['p(95)<2000', 'p(99)<3000'],

    'http_req_duration{phase:measure,api:refund_preview}': ['p(95)<300'],
    'http_req_duration{phase:measure,api:order_cancel_refund}': ['p(95)<1500'],
    'http_req_duration{phase:measure,api:order_detail_after_refund}': ['p(95)<300'],
    'http_req_duration{phase:measure,api:payment_detail_after_refund}': ['p(95)<300'],
    'http_req_duration{phase:measure,api:refund_list}': ['p(95)<400'],
    'http_req_duration{phase:measure,api:refund_detail}': ['p(95)<300'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
  const fixtures = getFixtures();
  const uniqueCredentials = new Map();

  fixtures.forEach((fixture) => {
    if (!uniqueCredentials.has(fixture.email)) {
      uniqueCredentials.set(fixture.email, fixture.password);
    }
  });

  const tokenByEmail = {};

  for (const [email, password] of uniqueCredentials.entries()) {
    tokenByEmail[email] = loginOnce(email, password);
  }

  return { tokenByEmail };
}

export function scenario(setupData) {
  const fixture = pickFixture();
  const token = setupData?.tokenByEmail?.[fixture.email];

  if (!token) {
    fail(`Missing setup token for refund fixture email. email=${fixture.email}`);
  }

  try {
    const startedAt = Date.now();

    const preview = getRefundPreview(token, fixture.paymentId);
    cancelOrder(token, fixture.orderId, preview.currentOrderCount);
    getOrderDetailAfterRefund(token, fixture.orderId, 'CANCELLED', 0);
    getPaymentDetailAfterRefund(token, fixture.paymentId, 'REFUNDED');
    const refund = getRefundList(token, 'COMPLETED', fixture.paymentId);
    getRefundDetail(token, refund.refundId, fixture.paymentId, 'COMPLETED');

    studentCancelRefundFlowMs.add(Date.now() - startedAt);
    studentCancelRefundBusinessSuccess.add(true);
  } catch (error) {
    studentCancelRefundBusinessSuccess.add(false);
    fail(`student cancel refund flow failed: ${error.message}`);
  }
}

function pickFixture() {
  const fixtures = getFixtures();
  const scenarioOffset = exec.scenario.name === 'measure' ? WARMUP_FIXTURE_OFFSET : 0;
  const index = scenarioOffset + exec.scenario.iterationInTest;

  if (!ALLOW_FIXTURE_REUSE && index >= fixtures.length) {
    fail(
      `Refund fixture pool exhausted. requiredMoreThan=${fixtures.length}. ` +
        'Increase seeded paid orders/payments or enable reuse for exploratory runs.',
    );
  }

  return fixtures[index % fixtures.length];
}

function estimatePlannedIterations(rate, duration) {
  const match = /^(\d+)([smh])$/.exec(duration.trim());

  if (!match) {
    fail(`Unsupported duration format for fixture planning. duration=${duration}`);
  }

  const amount = Number(match[1]);
  const unit = match[2];
  const secondsPerUnit = unit === 's' ? 1 : unit === 'm' ? 60 : 3600;

  return rate * amount * secondsPerUnit;
}

function getFixtures() {
  if (cachedFixtures) {
    return cachedFixtures;
  }

  if (__ENV.K6_STUDENT_REFUND_FIXTURES) {
    const parsed = JSON.parse(__ENV.K6_STUDENT_REFUND_FIXTURES);

    if (!Array.isArray(parsed) || parsed.length === 0) {
      fail('K6_STUDENT_REFUND_FIXTURES must be a non-empty JSON array.');
    }

    parsed.forEach((fixture, index) => {
      if (!fixture.email || !fixture.password || !fixture.orderId || !fixture.paymentId) {
        fail(
          'Each K6_STUDENT_REFUND_FIXTURES entry must include email, password, orderId, paymentId. ' +
            `invalidIndex=${index}`,
        );
      }
    });

    cachedFixtures = parsed;
    return cachedFixtures;
  }

  const fixtureIdStart = Number(__ENV.K6_REFUND_FIXTURE_ID_START || DEFAULT_FIXTURE_ID_START);
  const fixtureIdCount = Number(__ENV.K6_REFUND_FIXTURE_ID_COUNT || DEFAULT_FIXTURE_ID_COUNT);
  const orderIdPrefix = __ENV.K6_REFUND_ORDER_ID_PREFIX || DEFAULT_ORDER_ID_PREFIX;
  const paymentIdPrefix = __ENV.K6_REFUND_PAYMENT_ID_PREFIX || DEFAULT_PAYMENT_ID_PREFIX;

  if (!Number.isInteger(fixtureIdStart) || fixtureIdStart < 1) {
    fail('K6_REFUND_FIXTURE_ID_START must be a positive integer.');
  }
  if (!Number.isInteger(fixtureIdCount) || fixtureIdCount < 1) {
    fail('K6_REFUND_FIXTURE_ID_COUNT must be a positive integer.');
  }

  cachedFixtures = Array.from({ length: fixtureIdCount }, (_, index) => {
    const fixtureNumber = fixtureIdStart + index;
    return {
      email: __ENV.K6_REFUND_STUDENT_EMAIL || DEFAULT_STUDENT_EMAIL,
      password: __ENV.K6_REFUND_STUDENT_PASSWORD || DEFAULT_STUDENT_PASSWORD,
      orderId: buildUuidFromPrefix(orderIdPrefix, fixtureNumber),
      paymentId: buildUuidFromPrefix(paymentIdPrefix, fixtureNumber),
    };
  });

  return cachedFixtures;
}

function buildUuidFromPrefix(prefix, value) {
  const trimmedPrefix = prefix.trim();
  const suffix = String(value).padStart(12, '0');

  if (!trimmedPrefix.endsWith('-')) {
    fail('UUID prefix must end with "-".');
  }

  return `${trimmedPrefix}${suffix}`;
}

function loginOnce(email, password) {
  if (TOKEN_CACHE.has(email)) {
    return TOKEN_CACHE.get(email);
  }

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

  TOKEN_CACHE.set(email, body.data.accessToken);
  return body.data.accessToken;
}

function getRefundPreview(token, paymentId) {
  const res = http.get(`${BASE_URL}/v1/payments/${paymentId}/refund-preview`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { api: 'refund_preview' },
  });

  const body = parseJson(res, 'refund preview');

  const ok =
    check(res, {
      'refund preview status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'refund preview says refundable': (b) => b?.data?.refundable === true,
      'refund preview currentOrderCount is positive': (b) => Number(b?.data?.currentOrderCount || 0) > 0,
    });

  if (!ok) {
    fail(`refund preview failed. status=${res.status}, body=${res.body}`);
  }

  return body.data;
}

function cancelOrder(token, orderId, cancelCount) {
  const res = http.patch(
    `${BASE_URL}/v1/orders/${orderId}/cancel`,
    JSON.stringify({ cancelCount }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      tags: { api: 'order_cancel_refund' },
    },
  );

  const body = parseJson(res, 'order cancel refund');

  const ok =
    check(res, {
      'order cancel refund status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'order cancel refund response has orderId': (b) => b?.data?.orderId === orderId,
    });

  if (!ok) {
    fail(`order cancel refund failed. status=${res.status}, body=${res.body}`);
  }
}

function getPaymentDetailAfterRefund(token, paymentId, expectedStatus) {
  const res = http.get(`${BASE_URL}/v1/payments/${paymentId}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { api: 'payment_detail_after_refund' },
  });

  const body = parseJson(res, 'payment detail after refund');

  const ok =
    check(res, {
      'payment detail after refund status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'payment detail after refund matches status': (b) => b?.data?.status === expectedStatus,
    });

  if (!ok) {
    fail(`payment detail after refund failed. status=${res.status}, body=${res.body}`);
  }
}

function getOrderDetailAfterRefund(token, orderId, expectedStatus, expectedOrderCount) {
  const res = http.get(`${BASE_URL}/v1/orders/${orderId}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { api: 'order_detail_after_refund' },
  });

  const body = parseJson(res, 'order detail after refund');

  const ok =
    check(res, {
      'order detail after refund status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'order detail after refund matches status': (b) => b?.data?.status === expectedStatus,
      'order detail after refund orderCount matches': (b) =>
        Number(b?.data?.orderCount) === expectedOrderCount,
    });

  if (!ok) {
    fail(`order detail after refund failed. status=${res.status}, body=${res.body}`);
  }
}

function getRefundList(token, expectedStatus, paymentId) {
  const res = http.get(
    `${BASE_URL}/v1/refunds?status=${expectedStatus}&page=0&size=${REFUND_LIST_PAGE_SIZE}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { api: 'refund_list' },
    },
  );

  const body = parseJson(res, 'refund list');

  const ok =
    check(res, {
      'refund list status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'refund list has content': (b) => Array.isArray(b?.data?.content) && b.data.content.length > 0,
      'refund list totalElements is positive': (b) => Number(b?.data?.totalElements || 0) > 0,
      'refund list items match requested status': (b) =>
        Array.isArray(b?.data?.content) && b.data.content.every((item) => item.status === expectedStatus),
    });

  if (!ok) {
    fail(`refund list failed. status=${res.status}, body=${res.body}`);
  }

  const found = body?.data?.content?.find((item) => item.paymentId === paymentId);
  if (!found?.refundId) {
    fail(`refund list does not contain the refunded payment. paymentId=${paymentId}, body=${res.body}`);
  }

  return found;
}

function getRefundDetail(token, refundId, paymentId, expectedStatus) {
  const res = http.get(`${BASE_URL}/v1/refunds/${refundId}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { api: 'refund_detail' },
  });

  const body = parseJson(res, 'refund detail');

  const ok =
    check(res, {
      'refund detail status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'refund detail matches status': (b) => b?.data?.status === expectedStatus,
      'refund detail paymentId matches': (b) => b?.data?.paymentId === paymentId,
    });

  if (!ok) {
    fail(`refund detail failed. status=${res.status}, body=${res.body}`);
  }
}

function parseJson(res, label) {
  try {
    return res.json();
  } catch (error) {
    fail(`${label} did not return JSON. status=${res.status}, body=${res.body}`);
  }
}
