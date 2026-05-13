import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const ORDER_ID = __ENV.K6_PAYMENT_ORDER_ID || '91000000-0000-0000-0000-000000000001';
const STUDENT_EMAIL = __ENV.K6_STUDENT_EMAIL || 'perf.payment.prepare@example.com';
const STUDENT_PASSWORD = __ENV.K6_STUDENT_PASSWORD || 'testTEST123!@#';
const PAYMENT_LIST_PAGE_SIZE = 500;
const PAY_WAY = __ENV.K6_PAYMENT_PAY_WAY || 'CARD';

// This smoke scenario focuses only on the payment domain.
// Order creation is intentionally excluded and a pre-created order fixture is used.
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
  const payment = preparePayment(token, ORDER_ID);

  getPaymentDetail(token, payment.paymentId, 'PENDING');
  getPaymentList(token, payment.paymentId, 'PENDING');
}

function validateRequiredEnv() {
  if (!ORDER_ID || !STUDENT_EMAIL || !STUDENT_PASSWORD) {
    fail('Failed to resolve default smoke fixture values.');
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
      'payment prepare failed. ' +
        'Check whether the order fixture belongs to this student and whether the order is still payable. ' +
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

function getPaymentList(token, paymentId, expectedStatus) {
  const res = http.get(`${BASE_URL}/v1/payments?status=${expectedStatus}&page=0&size=${PAYMENT_LIST_PAGE_SIZE}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { api: 'payment_list' },
  });

  const body = parseJson(res, 'payment list');

  const ok =
    check(res, {
      'payment list status is 200': (r) => r.status === 200,
    }) &&
    check(body, {
      'payment list contains payment': (b) =>
        Boolean(b?.data?.content?.some((item) => item.paymentId === paymentId)),
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
