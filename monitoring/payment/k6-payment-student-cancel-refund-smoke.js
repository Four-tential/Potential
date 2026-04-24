import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const STUDENT_EMAIL = __ENV.K6_REFUND_STUDENT_EMAIL || 'perf.payment.refund@example.com';
const STUDENT_PASSWORD = __ENV.K6_REFUND_STUDENT_PASSWORD || 'testTEST123!@#';
const ORDER_ID = __ENV.K6_REFUND_ORDER_ID || '94000000-0000-0000-0000-000000000001';
const PAYMENT_ID = __ENV.K6_REFUND_PAYMENT_ID || '95000000-0000-0000-0000-000000000001';
const REFUND_LIST_PAGE_SIZE = 100;

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const token = login();
  const preview = getRefundPreview(token, PAYMENT_ID);
  cancelOrder(token, ORDER_ID, preview.currentOrderCount);
  getOrderDetailAfterRefund(token, ORDER_ID, 'CANCELLED', 0);
  getPaymentDetailAfterRefund(token, PAYMENT_ID, 'REFUNDED');
  const refund = getRefundList(token, 'COMPLETED', PAYMENT_ID);
  getRefundDetail(token, refund.refundId, PAYMENT_ID, 'COMPLETED');
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
