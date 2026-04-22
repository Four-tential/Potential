import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,
  duration: '10s',
  thresholds: {
    http_req_failed: ['rate<0.01'], // 에러율 1% 미만 유지
    http_req_duration: ['p(95)<500', 'p(99)<1000'], // P95는 500ms, P99는 1s 미만 유지
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'], // 요약 결과에 P99 추가
};

export default function () {
  const res = http.get(`${__ENV.BASE_URL}/actuator/health`);
  
  check(res, {
    'is status 200': (r) => r.status === 200,
  });

  sleep(1);
}
