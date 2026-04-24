import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const DEFAULT_STUDENT_EMAIL = 'perf.payment.prepare@example.com';
const DEFAULT_STUDENT_PASSWORD = 'testTEST123!@#';
const DEFAULT_ORDER_ID_PREFIX = '91000000-0000-0000-0000-';
const DEFAULT_ORDER_ID_START = 1;
const DEFAULT_ORDER_ID_COUNT = 500;
const PAYMENT_LIST_PAGE_SIZE = 100;
const PAY_WAY = __ENV.K6_PAYMENT_PAY_WAY || 'CARD';

// 결제 준비/조회 시나리오의 프로젝트 규모 기준 부하 프로필.
// 부트캠프 팀 프로젝트 규모를 DAU 500~3000, peak RPS 15~45 정도로 보고,
// 이 브랜치에서는 중간값인 30 RPS를 실무적인 기준값으로 사용한다.
// 또한 fresh order fixture만 사용하려면 전체 iteration 예산이
// 시딩된 500개 주문을 넘지 않아야 한다.
// 30 RPS * (5초 warmup + 10초 measure) = 총 450 iterations
const TARGET_RPS = 30;
const WARMUP_DURATION = '5s';
const MEASURE_DURATION = '10s';
const PRE_ALLOCATED_VUS = 120;
const MAX_VUS = 320;
const ALLOW_FIXTURE_REUSE = false;

const TOKEN_CACHE = new Map();
let cachedFixtures = null;

const paymentPrepareFlowMs = new Trend('payment_prepare_flow_ms');
const paymentPrepareBusinessSuccess = new Rate('payment_prepare_business_success');

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
    'payment_prepare_business_success{phase:measure}': ['rate>0.99'],
    'payment_prepare_flow_ms{phase:measure}': ['p(95)<1800', 'p(99)<3000'],

    'http_req_duration{phase:measure,api:payment_prepare}': ['p(95)<1000'],
    'http_req_duration{phase:measure,api:payment_detail}': ['p(95)<300'],
    'http_req_duration{phase:measure,api:payment_list}': ['p(95)<400'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function scenario() {
  const fixture = pickFixture();
  const token = login(fixture);

  try {
    const startedAt = Date.now();

    const payment = preparePayment(token, fixture.orderId);

    getPaymentDetail(token, payment.paymentId, 'PENDING');
    getPaymentList(token, 'PENDING');

    paymentPrepareFlowMs.add(Date.now() - startedAt);
    paymentPrepareBusinessSuccess.add(true);
  } catch (error) {
    paymentPrepareBusinessSuccess.add(false);
    fail(`payment prepare flow failed: ${error.message}`);
  }
}

function pickFixture() {
  const fixtures = getFixtures();
  const index = exec.scenario.iterationInTest;

  if (!ALLOW_FIXTURE_REUSE && index >= fixtures.length) {
    fail(
      `Fixture pool exhausted. requiredMoreThan=${fixtures.length}. ` +
        'Provide more pre-created order fixtures or enable K6_PAYMENT_ALLOW_FIXTURE_REUSE=true.',
    );
  }

  return fixtures[index % fixtures.length];
}

function getFixtures() {
  if (cachedFixtures) {
    return cachedFixtures;
  }

  if (__ENV.K6_PAYMENT_FIXTURES) {
    const parsed = JSON.parse(__ENV.K6_PAYMENT_FIXTURES);

    if (!Array.isArray(parsed) || parsed.length === 0) {
      fail('K6_PAYMENT_FIXTURES must be a non-empty JSON array.');
    }

    parsed.forEach((fixture, index) => {
      if (!fixture.email || !fixture.password || !fixture.orderId) {
        fail(`Each K6_PAYMENT_FIXTURES entry must include email, password, orderId. invalidIndex=${index}`);
      }
    });

    cachedFixtures = parsed;
    return cachedFixtures;
  }

  if (__ENV.K6_STUDENT_EMAIL && __ENV.K6_STUDENT_PASSWORD && __ENV.K6_PAYMENT_ORDER_IDS) {
    const orderIds = parseOrderIds(__ENV.K6_PAYMENT_ORDER_IDS);

    cachedFixtures = orderIds.map((orderId) => ({
      email: __ENV.K6_STUDENT_EMAIL,
      password: __ENV.K6_STUDENT_PASSWORD,
      orderId,
    }));

    return cachedFixtures;
  }

  if (
    (__ENV.K6_STUDENT_EMAIL || DEFAULT_STUDENT_EMAIL) &&
    (__ENV.K6_STUDENT_PASSWORD || DEFAULT_STUDENT_PASSWORD) &&
    (__ENV.K6_PAYMENT_ORDER_ID_PREFIX || DEFAULT_ORDER_ID_PREFIX) &&
    (__ENV.K6_PAYMENT_ORDER_ID_COUNT || DEFAULT_ORDER_ID_COUNT)
  ) {
    const orderIdStart = Number(__ENV.K6_PAYMENT_ORDER_ID_START || DEFAULT_ORDER_ID_START);
    const orderIdCount = Number(__ENV.K6_PAYMENT_ORDER_ID_COUNT || DEFAULT_ORDER_ID_COUNT);

    if (!Number.isInteger(orderIdStart) || orderIdStart < 1) {
      fail('K6_PAYMENT_ORDER_ID_START must be a positive integer.');
    }
    if (!Number.isInteger(orderIdCount) || orderIdCount < 1) {
      fail('K6_PAYMENT_ORDER_ID_COUNT must be a positive integer.');
    }

    cachedFixtures = Array.from({ length: orderIdCount }, (_, index) => ({
      email: __ENV.K6_STUDENT_EMAIL || DEFAULT_STUDENT_EMAIL,
      password: __ENV.K6_STUDENT_PASSWORD || DEFAULT_STUDENT_PASSWORD,
      orderId: buildUuidFromPrefix(
        __ENV.K6_PAYMENT_ORDER_ID_PREFIX || DEFAULT_ORDER_ID_PREFIX,
        orderIdStart + index,
      ),
    }));

    return cachedFixtures;
  }

  if (
    (__ENV.K6_STUDENT_EMAIL || DEFAULT_STUDENT_EMAIL) &&
    (__ENV.K6_STUDENT_PASSWORD || DEFAULT_STUDENT_PASSWORD) &&
    __ENV.K6_PAYMENT_ORDER_ID
  ) {
    cachedFixtures = [
      {
        email: __ENV.K6_STUDENT_EMAIL || DEFAULT_STUDENT_EMAIL,
        password: __ENV.K6_STUDENT_PASSWORD || DEFAULT_STUDENT_PASSWORD,
        orderId: __ENV.K6_PAYMENT_ORDER_ID,
      },
    ];

    return cachedFixtures;
  }

  fail('Failed to build payment prepare fixtures.');
}

function buildUuidFromPrefix(prefix, value) {
  const trimmedPrefix = prefix.trim();
  const suffix = String(value).padStart(12, '0');

  if (!trimmedPrefix.endsWith('-')) {
    fail('K6_PAYMENT_ORDER_ID_PREFIX must end with "-". example: 91000000-0000-0000-0000-');
  }

  return `${trimmedPrefix}${suffix}`;
}

function parseOrderIds(rawValue) {
  const raw = rawValue.trim();

  if (raw.startsWith('[')) {
    const parsed = JSON.parse(raw);

    if (!Array.isArray(parsed) || parsed.length === 0) {
      fail('K6_PAYMENT_ORDER_IDS must be a non-empty JSON array.');
    }

    return parsed;
  }

  const split = raw
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);

  if (split.length === 0) {
    fail('K6_PAYMENT_ORDER_IDS must not be empty.');
  }

  return split;
}

function login(fixture) {
  if (TOKEN_CACHE.has(fixture.email)) {
    return TOKEN_CACHE.get(fixture.email);
  }

  const res = http.post(
    `${BASE_URL}/v1/auth/login`,
    JSON.stringify({
      email: fixture.email,
      password: fixture.password,
    }),
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

  TOKEN_CACHE.set(fixture.email, body.data.accessToken);
  return body.data.accessToken;
}

function preparePayment(token, orderId) {
  const res = http.post(
    `${BASE_URL}/v1/payments`,
    JSON.stringify({
      orderId,
      payWay: PAY_WAY,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      tags: { api: 'payment_prepare' },
    },
  );

  const body = parseJson(res, 'payment prepare');

  const ok =
    check(res, {
      'payment prepare status is 201': (r) => r.status === 201,
    }) &&
    check(body, {
      'paymentId exists': (b) => Boolean(b?.data?.paymentId),
      'pgKey exists': (b) => Boolean(b?.data?.pgKey),
      'payment status is PENDING': (b) => b?.data?.status === 'PENDING',
    });

  if (!ok) {
    fail(
      'payment prepare failed. Check whether the order fixture belongs to the authenticated student and is still payable. ' +
        `status=${res.status}, body=${res.body}`,
    );
  }

  return body.data;
}

function getPaymentDetail(token, paymentId, expectedStatus) {
  const res = http.get(`${BASE_URL}/v1/payments/${paymentId}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { api: 'payment_detail' },
  });

  const body = parseJson(res, 'payment detail');

  const ok =
    check(res, {
      'payment detail status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'payment detail status matches': (b) => b?.data?.status === expectedStatus,
    });

  if (!ok) {
    fail(`payment detail failed. status=${res.status}, body=${res.body}`);
  }
}

function getPaymentList(token, expectedStatus) {
  const res = http.get(
    `${BASE_URL}/v1/payments?status=${expectedStatus}&page=0&size=${PAYMENT_LIST_PAGE_SIZE}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { api: 'payment_list' },
    },
  );

  const body = parseJson(res, 'payment list');

  const ok =
    check(res, {
      'payment list status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'payment list has content': (b) => Array.isArray(b?.data?.content) && b.data.content.length > 0,
      'payment list totalElements is positive': (b) => Number(b?.data?.totalElements || 0) > 0,
      'payment list items match requested status': (b) =>
        Array.isArray(b?.data?.content) &&
        b.data.content.every((item) => item.status === expectedStatus),
    });

  if (!ok) {
    fail(`payment list failed. status=${res.status}, body=${res.body}`);
  }
}

function parseJson(res, label) {
  try {
    return res.json();
  } catch (error) {
    fail(`${label} did not return JSON. status=${res.status}, body=${res.body}`);
  }
}
