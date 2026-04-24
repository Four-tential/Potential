// Course 도메인 부하 테스트
//
// 시나리오: 코스 목록 -> 카테고리 필터 -> 코스 상세 -> 코스 찜 추가/제거
//
// 실행:
//   STAGE=smoke BASE_URL=http://localhost:8080 k6 run monitoring/course/k6-course.js
//   STAGE=load  BASE_URL=http://localhost:8080 k6 run monitoring/course/k6-course.js
//   STAGE=stress BASE_URL=http://localhost:8080 k6 run monitoring/course/k6-course.js
//   STAGE=spike  BASE_URL=http://localhost:8080 k6 run monitoring/course/k6-course.js

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const PASSWORD = 'testTEST123!@#';

const STUDENT_EMAILS = Array.from({ length: 10 }, (_, i) => `loadtest-student-${i + 1}@example.com`);
const CATEGORIES = ['FITNESS', 'YOGA', 'PILATES', 'DANCE', 'SWIMMING', 'RUNNING', 'CLIMBING', 'BOXING'];

const STAGES = {
  smoke:  { stages: [{ duration: '60s', target: 10 }] },
  load:   { stages: [{ duration: '30s', target: 50 }, { duration: '2m', target: 50 }, { duration: '30s', target: 0 }] },
  stress: { stages: [{ duration: '30s', target: 100 }, { duration: '30s', target: 300 }, { duration: '30s', target: 500 }, { duration: '30s', target: 700 }, { duration: '30s', target: 1000 }, { duration: '30s', target: 1200 }, { duration: '30s', target: 0 }] },
  spike:  { stages: [{ duration: '10s', target: 10 }, { duration: '10s', target: 1000 }, { duration: '30s', target: 1000 }, { duration: '30s', target: 10 }] },
};

const stage = (__ENV.STAGE || 'load').toLowerCase();

export const options = {
  stages: (STAGES[stage] || STAGES.load).stages,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:course-list}':           ['p(95)<2000'],
    'http_req_duration{name:course-list-filtered}':  ['p(95)<2000'],
    'http_req_duration{name:course-detail}':         ['p(95)<2000'],
    'http_req_duration{name:wishlist-add}':           ['p(95)<2000'],
    'http_req_duration{name:wishlist-remove}':        ['p(95)<2000'],
  },
};

function login(email) {
  const res = http.post(
    `${BASE}/v1/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(res, { 'login 200': (r) => r.status === 200 });
  return res.json('data.accessToken');
}

function sampleCourseId(iter) {
  const n = (iter % 2000) + 1;
  return `00000000-0000-0000-0000-04${n.toString(16).padStart(10, '0')}`;
}

export function setup() {
  const studentTokens = STUDENT_EMAILS.map((e) => login(e));
  return { studentTokens };
}

export default function (data) {
  const token = data.studentTokens[__VU % data.studentTokens.length];
  const studentHeaders = { Authorization: `Bearer ${token}` };
  const courseId = sampleCourseId(__ITER);
  const page = __ITER % 20;
  const category = CATEGORIES[__ITER % CATEGORIES.length];

  // 코스 목록 조회 (페이징)
  const listRes = http.get(
    `${BASE}/v1/courses?page=${page}&size=20`,
    { tags: { name: 'course-list' } },
  );
  check(listRes, { 'course-list 200': (r) => r.status === 200 });

  // 코스 목록 조회 (카테고리 필터)
  const filteredRes = http.get(
    `${BASE}/v1/courses?page=0&size=20&categoryCode=${category}`,
    { tags: { name: 'course-list-filtered' } },
  );
  check(filteredRes, { 'course-list-filtered 2xx': (r) => r.status >= 200 && r.status < 300 });

  // 코스 상세 조회
  const detailRes = http.get(
    `${BASE}/v1/courses/${courseId}`,
    { tags: { name: 'course-detail' } },
  );
  check(detailRes, { 'course-detail 2xx': (r) => r.status >= 200 && r.status < 300 });

  // 코스 찜 제거 (시드 데이터에 이미 찜이 있을 수 있으므로 먼저 제거)
  const removeRes = http.del(
    `${BASE}/v1/courses/${courseId}/wishlist-courses`,
    null,
    { headers: studentHeaders, tags: { name: 'wishlist-remove' } },
  );
  check(removeRes, { 'wishlist-remove ok': (r) => r.status >= 200 && r.status < 500 });

  // 코스 찜 추가
  const addRes = http.post(
    `${BASE}/v1/courses/${courseId}/wishlist-courses`,
    null,
    { headers: studentHeaders, tags: { name: 'wishlist-add' } },
  );
  check(addRes, { 'wishlist-add ok': (r) => r.status >= 200 && r.status < 500 });

  sleep(1);
}
