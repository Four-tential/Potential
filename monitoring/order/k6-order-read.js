import http from 'k6/http';
import { check, group, fail } from 'k6';

export const options = {
  scenarios: {
    order_read_load_test: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 20,
      maxVUs: 100,
      stages: [
        { target: 30, duration: '1m' },
        { target: 30, duration: '5m' },
        { target: 45, duration: '1m' },
      ],
    },
  },
  thresholds: {
    'http_req_failed{name:OrderList}': ['rate<0.01'],
    'http_req_failed{name:OrderDetail}': ['rate<0.01'],
    'http_req_duration{name:OrderList}': ['p(95)<500'],
    'http_req_duration{name:OrderDetail}': ['p(95)<200'],
  },
};

export function setup() {
  const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
  
  const loginRes = http.post(`${baseUrl}/v1/auth/login`, JSON.stringify({
    email: 'user1@user.com',
    password: 'testTEST123!@#'
  }), { headers: { 'Content-Type': 'application/json' } });

  if (!check(loginRes, { 'login success': (r) => r.status === 200 })) {
    fail('Login failed during setup');
  }

  const token = loginRes.json('data.accessToken');
  const authParams = { headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' } };

  // 내 주문 목록 조회
  const listRes = http.get(`${baseUrl}/v1/orders/me?page=0&size=20`, authParams);
  let orderIds = [];
  
  if (listRes.status === 200) {
    const content = listRes.json('data.content');
    if (content && content.length > 0) {
      // API 응답의 ID 필드명을 확인해야 함 (보통 orderId 또는 id)
      orderIds = content.map(item => item.orderId || item.id);
    }
  }

  // 주문이 없을 경우 샘플 하나 생성 시도
  if (orderIds.length === 0) {
    const createRes = http.post(`${baseUrl}/v1/orders`, JSON.stringify({
      courseId: '00000000-0000-0000-0000-000000000201',
      orderCount: 1
    }), authParams);

    if (createRes.status === 201) {
      const orderId = createRes.json('data.orderId');
      if (orderId) orderIds.push(orderId);
    }
  }

  return { token, orderIds };
}

export default function (data) {
  const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
  
  const rand = Math.random();

  if (rand < 0.7) {
    group('My Order List', function () {
      const page = Math.floor(Math.random() * 3);
      const res = http.get(`${baseUrl}/v1/orders/me?page=${page}&size=10`, {
        headers: { 'Authorization': `Bearer ${data.token}` },
        tags: { name: 'OrderList' }
      });
      
      const success = check(res, { 'list status is 200': (r) => r.status === 200 });
      if (!success) {
        console.error(`OrderList failed: ${res.status} ${res.body}`);
      }
    });
  } else {
    group('Order Detail', function () {
      if (data.orderIds.length > 0) {
        const orderId = data.orderIds[Math.floor(Math.random() * data.orderIds.length)];
        const res = http.get(`${baseUrl}/v1/orders/${orderId}`, {
          headers: { 'Authorization': `Bearer ${data.token}` },
          tags: { name: 'OrderDetail' }
        });
        
        const success = check(res, { 'detail status is 200': (r) => r.status === 200 });
        if (!success) {
          // 실패 원인 파악을 위해 로그 출력 (너무 많이 찍힐 수 있으니 샘플링 권장)
          if (Math.random() < 0.01) {
            console.error(`OrderDetail failed for ID ${orderId}: ${res.status} ${res.body}`);
          }
        }
      }
    });
  }
}
