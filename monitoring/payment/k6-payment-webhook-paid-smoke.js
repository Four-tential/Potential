import http from 'k6/http';
import crypto from 'k6/crypto';
import { check, fail } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const STUDENT_EMAIL = __ENV.K6_WEBHOOK_STUDENT_EMAIL || 'perf.payment.webhook@example.com';
const STUDENT_PASSWORD = __ENV.K6_WEBHOOK_STUDENT_PASSWORD || 'testTEST123!@#';
const PAYMENT_ID = __ENV.K6_WEBHOOK_PAYMENT_ID || '93000000-0000-0000-0000-000000000500';
const PG_KEY = __ENV.K6_WEBHOOK_PG_KEY || 'pperfwebhook000500';
const STORE_ID = __ENV.K6_PORTONE_STORE_ID || __ENV.PORTONE_STORE_ID || 'store-perf-local';
const WEBHOOK_SECRET = __ENV.K6_PORTONE_WEBHOOK_SECRET || __ENV.PORTONE_WEBHOOK_SECRET;
const PAYMENT_LIST_PAGE_SIZE = 500;

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  validateRequiredEnv();

  const token = login();
  const webhookId = `perf-paid-smoke-${PAYMENT_ID}`;

  sendPaidWebhook(PG_KEY, webhookId);
  getPaymentDetail(token, PAYMENT_ID, 'PAID');
  getPaymentList(token, 'PAID');
}

function validateRequiredEnv() {
  if (!STUDENT_EMAIL || !STUDENT_PASSWORD || !PAYMENT_ID || !PG_KEY || !WEBHOOK_SECRET) {
    fail(
      'Paid webhook smoke fixture is incomplete. ' +
        'Check local data seed and PORTONE_WEBHOOK_SECRET forwarding to the k6 container.',
    );
  }
}

function login() {
  const res = http.post(
    `${BASE_URL}/v1/auth/login`,
    JSON.stringify({
      email: STUDENT_EMAIL,
      password: STUDENT_PASSWORD,
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

  return body.data.accessToken;
}

function sendPaidWebhook(pgKey, webhookId) {
  const webhookTimestamp = String(Math.floor(Date.now() / 1000));
  const rawBody = buildPaidWebhookBody(pgKey, webhookId, webhookTimestamp);
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
