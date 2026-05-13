import http from 'k6/http';
import crypto from 'k6/crypto';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const DEFAULT_STUDENT_EMAIL = 'perf.payment.webhook@example.com';
const DEFAULT_STUDENT_PASSWORD = 'testTEST123!@#';
const DEFAULT_PAYMENT_ID_PREFIX = '93000000-0000-0000-0000-';
const DEFAULT_PAYMENT_ID_START = 1;
const DEFAULT_PAYMENT_ID_COUNT = 500;
const DEFAULT_PG_KEY_PREFIX = 'pperfwebhook';
const PAYMENT_LIST_PAGE_SIZE = 100;
const STORE_ID = __ENV.K6_PORTONE_STORE_ID || __ENV.PORTONE_STORE_ID || 'store-perf-local';
const WEBHOOK_SECRET = __ENV.K6_PORTONE_WEBHOOK_SECRET || __ENV.PORTONE_WEBHOOK_SECRET;

// Paid 웹훅 시나리오의 프로젝트 규모 기준 부하 프로필.
// 부트캠프 팀 프로젝트 규모를 DAU 500~3000, peak RPS 15~45 정도로 보고,
// 이 시나리오에서도 중간값인 30 RPS를 기준선으로 사용한다.
// fixture pool은 pending payment 500개이며,
// 30 RPS * (5초 warmup + 10초 measure) = 약 450건의 웹훅 전달이 발생한다.
const TARGET_RPS = 30;
const WARMUP_DURATION = '5s';
const MEASURE_DURATION = '10s';
const PRE_ALLOCATED_VUS = 120;
const MAX_VUS = 320;
const ALLOW_FIXTURE_REUSE = false;

const TOKEN_CACHE = new Map();
let cachedFixtures = null;

const paymentWebhookPaidFlowMs = new Trend('payment_webhook_paid_flow_ms');
const paymentWebhookPaidBusinessSuccess = new Rate('payment_webhook_paid_business_success');

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
    'payment_webhook_paid_business_success{phase:measure}': ['rate>0.99'],
    'payment_webhook_paid_flow_ms{phase:measure}': ['p(95)<2500', 'p(99)<4000'],

    'http_req_duration{phase:measure,api:webhook_paid}': ['p(95)<2000'],
    'http_req_duration{phase:measure,api:payment_detail_after_webhook}': ['p(95)<300'],
    'http_req_duration{phase:measure,api:payment_list_paid}': ['p(95)<400'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
  validateRequiredEnv();
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
  validateRequiredEnv();

  const fixture = pickFixture();
  const token = setupData?.tokenByEmail?.[fixture.email];

  if (!token) {
    fail(`Missing setup token for webhook fixture email. email=${fixture.email}`);
  }

  try {
    const startedAt = Date.now();

    sendPaidWebhook(fixture);
    getPaymentDetail(token, fixture.paymentId, 'PAID');
    getPaymentList(token, 'PAID');

    paymentWebhookPaidFlowMs.add(Date.now() - startedAt);
    paymentWebhookPaidBusinessSuccess.add(true);
  } catch (error) {
    paymentWebhookPaidBusinessSuccess.add(false);
    fail(`payment webhook paid flow failed: ${error.message}`);
  }
}

function validateRequiredEnv() {
  if (!WEBHOOK_SECRET) {
    fail(
      'PORTONE_WEBHOOK_SECRET is required for the Paid webhook scenario. ' +
        'Make sure docker-compose passes the local .env secret into the k6 container.',
    );
  }
}

function pickFixture() {
  const fixtures = getFixtures();
  const index = exec.scenario.iterationInTest;

  if (!ALLOW_FIXTURE_REUSE && index >= fixtures.length) {
    fail(
      `Webhook fixture pool exhausted. requiredMoreThan=${fixtures.length}. ` +
        'Increase seeded pending payments or enable reuse for exploratory runs.',
    );
  }

  return fixtures[index % fixtures.length];
}

function getFixtures() {
  if (cachedFixtures) {
    return cachedFixtures;
  }

  if (__ENV.K6_WEBHOOK_PAID_FIXTURES) {
    const parsed = JSON.parse(__ENV.K6_WEBHOOK_PAID_FIXTURES);

    if (!Array.isArray(parsed) || parsed.length === 0) {
      fail('K6_WEBHOOK_PAID_FIXTURES must be a non-empty JSON array.');
    }

    parsed.forEach((fixture, index) => {
      if (!fixture.email || !fixture.password || !fixture.paymentId || !fixture.pgKey) {
        fail(
          'Each K6_WEBHOOK_PAID_FIXTURES entry must include email, password, paymentId, pgKey. ' +
            `invalidIndex=${index}`,
        );
      }
    });

    cachedFixtures = parsed;
    return cachedFixtures;
  }

  const paymentIdStart = Number(__ENV.K6_WEBHOOK_PAYMENT_ID_START || DEFAULT_PAYMENT_ID_START);
  const paymentIdCount = Number(__ENV.K6_WEBHOOK_PAYMENT_ID_COUNT || DEFAULT_PAYMENT_ID_COUNT);
  const paymentIdPrefix = __ENV.K6_WEBHOOK_PAYMENT_ID_PREFIX || DEFAULT_PAYMENT_ID_PREFIX;
  const pgKeyPrefix = __ENV.K6_WEBHOOK_PG_KEY_PREFIX || DEFAULT_PG_KEY_PREFIX;

  if (!Number.isInteger(paymentIdStart) || paymentIdStart < 1) {
    fail('K6_WEBHOOK_PAYMENT_ID_START must be a positive integer.');
  }
  if (!Number.isInteger(paymentIdCount) || paymentIdCount < 1) {
    fail('K6_WEBHOOK_PAYMENT_ID_COUNT must be a positive integer.');
  }
  if (!paymentIdPrefix.endsWith('-')) {
    fail('K6_WEBHOOK_PAYMENT_ID_PREFIX must end with "-". example: 93000000-0000-0000-0000-');
  }

  cachedFixtures = Array.from({ length: paymentIdCount }, (_, index) => {
    const fixtureNumber = paymentIdStart + index;

    return {
      email: __ENV.K6_WEBHOOK_STUDENT_EMAIL || DEFAULT_STUDENT_EMAIL,
      password: __ENV.K6_WEBHOOK_STUDENT_PASSWORD || DEFAULT_STUDENT_PASSWORD,
      paymentId: `${paymentIdPrefix}${String(fixtureNumber).padStart(12, '0')}`,
      pgKey: `${pgKeyPrefix}${String(fixtureNumber).padStart(6, '0')}`,
      fixtureNumber,
    };
  });

  return cachedFixtures;
}

function loginOnce(email, password) {
  if (TOKEN_CACHE.has(email)) {
    return TOKEN_CACHE.get(email);
  }

  const res = http.post(
    `${BASE_URL}/v1/auth/login`,
    JSON.stringify({
      email,
      password,
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

  TOKEN_CACHE.set(email, body.data.accessToken);
  return body.data.accessToken;
}

function sendPaidWebhook(fixture) {
  const webhookId = `perf-paid-load-${String(fixture.fixtureNumber).padStart(6, '0')}`;
  const webhookTimestamp = String(Math.floor(Date.now() / 1000));
  const rawBody = buildPaidWebhookBody(fixture.pgKey, webhookId, webhookTimestamp);
  const webhookSignature = buildWebhookSignature(webhookId, webhookTimestamp, rawBody);

  const res = http.post(`${BASE_URL}/v1/webhooks/portone`, rawBody, {
    headers: {
      'Content-Type': 'application/json',
      'webhook-id': webhookId,
      'webhook-timestamp': webhookTimestamp,
      'webhook-signature': webhookSignature,
    },
    tags: { api: 'webhook_paid' },
  });

  const body = parseJson(res, 'webhook paid');

  const ok =
    check(res, {
      'webhook paid status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'webhook paid returns success': (b) => b?.success === true,
    });

  if (!ok) {
    fail(`webhook paid failed. status=${res.status}, body=${res.body}`);
  }
}

function getPaymentDetail(token, paymentId, expectedStatus) {
  const res = http.get(`${BASE_URL}/v1/payments/${paymentId}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { api: 'payment_detail_after_webhook' },
  });

  const body = parseJson(res, 'payment detail after webhook');

  const ok =
    check(res, {
      'payment detail after webhook status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'payment detail after webhook matches status': (b) => b?.data?.status === expectedStatus,
    });

  if (!ok) {
    fail(`payment detail after webhook failed. status=${res.status}, body=${res.body}`);
  }
}

function getPaymentList(token, expectedStatus) {
  const res = http.get(
    `${BASE_URL}/v1/payments?status=${expectedStatus}&page=0&size=${PAYMENT_LIST_PAGE_SIZE}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { api: 'payment_list_paid' },
    },
  );

  const body = parseJson(res, 'payment list after webhook');

  const ok =
    check(res, {
      'payment list after webhook status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'payment list after webhook has content': (b) => Array.isArray(b?.data?.content) && b.data.content.length > 0,
      'payment list after webhook totalElements is positive': (b) => Number(b?.data?.totalElements || 0) > 0,
      'payment list after webhook items match status': (b) =>
        Array.isArray(b?.data?.content) && b.data.content.every((item) => item.status === expectedStatus),
    });

  if (!ok) {
    fail(`payment list after webhook failed. status=${res.status}, body=${res.body}`);
  }
}

function buildPaidWebhookBody(pgKey, webhookId, webhookTimestamp) {
  return JSON.stringify({
    type: 'Transaction.Paid',
    timestamp: new Date(Number(webhookTimestamp) * 1000).toISOString(),
    data: {
      paymentId: pgKey,
      storeId: STORE_ID,
      transactionId: `tx-${webhookId}`,
    },
  });
}

function buildWebhookSignature(webhookId, webhookTimestamp, rawBody) {
  const signedPayload = `${webhookId}.${webhookTimestamp}.${rawBody}`;
  const secretBytes = decodeWebhookSecret(WEBHOOK_SECRET);
  const signature = crypto.hmac('sha256', secretBytes.buffer, signedPayload, 'base64');
  return `v1,${signature}`;
}

function decodeWebhookSecret(rawSecret) {
  const normalizedSecret = rawSecret.startsWith('whsec_') ? rawSecret.slice(6) : rawSecret;
  return base64Decode(normalizedSecret);
}

function base64Decode(base64) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const cleaned = base64.replace(/\s+/g, '');
  const bytes = [];

  let buffer = 0;
  let bitsCollected = 0;

  for (const char of cleaned) {
    if (char === '=') {
      break;
    }

    const value = alphabet.indexOf(char);
    if (value === -1) {
      fail(`Invalid base64 character detected while decoding webhook secret. char=${char}`);
    }

    buffer = (buffer << 6) | value;
    bitsCollected += 6;

    if (bitsCollected >= 8) {
      bitsCollected -= 8;
      bytes.push((buffer >> bitsCollected) & 0xff);
    }
  }

  return Uint8Array.from(bytes);
}

function parseJson(res, label) {
  try {
    return res.json();
  } catch (error) {
    fail(`${label} did not return JSON. status=${res.status}, body=${res.body}`);
  }
}
