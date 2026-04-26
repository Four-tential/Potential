// Member 도메인 부하 테스트
//
// 시나리오: 마이페이지 조회 → 마이페이지 수정 -> 코스 찜 조회 -> 강사 팔로우 조회
// -> 강사 프로필 조회 -> 강사 코스 조회 -> 강사 팔로우 해제 -> 강사 팔로우
// -> 강사의 내 코스 목록 -> 수강생 명단
//
// 실행:
//   K6_SCRIPT=member/k6-member.js STAGE=smoke docker compose --profile k6 run --rm k6 run -o experimental-prometheus-rw --tag testid=member-smoke /scripts/member/k6-member.js
//   K6_SCRIPT=member/k6-member.js STAGE=load docker compose --profile k6 run --rm k6 run -o experimental-prometheus-rw --tag testid=member-load /scripts/member/k6-member.js
//   K6_SCRIPT=member/k6-member.js STAGE=stress docker compose --profile k6 run --rm k6 run -o experimental-prometheus-rw --tag testid=member-stress /scripts/member/k6-member.js
//   K6_SCRIPT=member/k6-member.js STAGE=spike docker compose --profile k6 run --rm k6 run -o experimental-prometheus-rw --tag testid=member-spike /scripts/member/k6-member.js

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const PASSWORD = 'testTEST123!@#';

const STUDENT_EMAILS = Array.from({ length: 10 }, (_, i) => `loadtest-student-${i + 1}@example.com`);
const INSTRUCTOR_EMAIL = 'loadtest-instructor-1@example.com';
const TEST_INSTRUCTOR_COURSE_ID = '00000000-0000-0000-0000-fb0000000001';

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
    'http_req_failed{name:members-me}':          ['rate<0.01'],
    'http_req_failed{name:update-me}':            ['rate<0.01'],
    'http_req_failed{name:wishlist}':              ['rate<0.01'],
    'http_req_failed{name:follows}':               ['rate<0.01'],
    'http_req_failed{name:instructor-profile}':    ['rate<0.01'],
    'http_req_failed{name:instructor-courses}':    ['rate<0.01'],
    'http_req_failed{name:my-courses}':            ['rate<0.01'],
    'http_req_failed{name:my-students}':           ['rate<0.01'],
    'http_req_duration{name:members-me}':         ['p(95)<2000'],
    'http_req_duration{name:update-me}':           ['p(95)<2000'],
    'http_req_duration{name:wishlist}':             ['p(95)<2000'],
    'http_req_duration{name:follows}':              ['p(95)<2000'],
    'http_req_duration{name:instructor-profile}':   ['p(95)<2000'],
    'http_req_duration{name:instructor-courses}':   ['p(95)<2000'],
    'http_req_duration{name:my-courses}':           ['p(95)<2000'],
    'http_req_duration{name:my-students}':          ['p(95)<2000'],
    'http_req_duration{name:follow-instructor}':    ['p(95)<2000'],
    'http_req_duration{name:unfollow-instructor}':  ['p(95)<2000'],
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

function sampleInstructorMemberId(vu, iter) {
  const n = ((vu * 1000 + iter) % 100) + 1;
  return `00000000-0000-0000-0000-02${n.toString(16).padStart(10, '0')}`;
}

export function setup() {
  const studentTokens = STUDENT_EMAILS.map((e) => login(e));
  const instructorToken = login(INSTRUCTOR_EMAIL);
  return { studentTokens, instructorToken };
}

export default function (data) {
  const token = data.studentTokens[__VU % data.studentTokens.length];
  const studentHeaders = { Authorization: `Bearer ${token}` };
  const instructorHeaders = { Authorization: `Bearer ${data.instructorToken}` };
  const instructorId = sampleInstructorMemberId(__VU, __ITER);
  const page = __ITER % 20;

  // 마이페이지 조회
  const meRes = http.get(
    `${BASE}/v1/members/me`,
    { headers: studentHeaders, tags: { name: 'members-me' } },
  );
  check(meRes, { 'members-me 200': (r) => r.status === 200 });

  // 마이페이지 수정 (phone, profileImageUrl)
  const updateRes = http.patch(
    `${BASE}/v1/members/me`,
    JSON.stringify({ phone: `010-8000-${String((__VU * 100 + __ITER) % 10000).padStart(4, '0')}` }),
    {
      headers: { ...studentHeaders, 'Content-Type': 'application/json' },
      tags: { name: 'update-me' },
    },
  );
  check(updateRes, { 'update-me 2xx': (r) => r.status >= 200 && r.status < 300 });

  // 코스 찜 조회
  const wishRes = http.get(
    `${BASE}/v1/members/me/wishlist-courses?page=${page}&size=20`,
    { headers: studentHeaders, tags: { name: 'wishlist' } },
  );
  check(wishRes, { 'wishlist 200': (r) => r.status === 200 });

  // 팔로우 목록 조회
  const followsRes = http.get(
    `${BASE}/v1/members/me/follows?page=${page}&size=20`,
    { headers: studentHeaders, tags: { name: 'follows' } },
  );
  check(followsRes, { 'follows 200': (r) => r.status === 200 });

  // 강사 프로필 조회 (공개)
  const profileRes = http.get(
    `${BASE}/v1/instructors/${instructorId}`,
    { tags: { name: 'instructor-profile' } },
  );
  check(profileRes, { 'instructor-profile 2xx': (r) => r.status >= 200 && r.status < 300 });

  // 강사 코스 조회 (인증 필요)
  const instCoursesRes = http.get(
    `${BASE}/v1/instructors/${instructorId}/courses?page=0&size=20`,
    { headers: studentHeaders, tags: { name: 'instructor-courses' } },
  );
  check(instCoursesRes, { 'instructor-courses 2xx': (r) => r.status >= 200 && r.status < 300 });

  // 팔로우 해제 (동시성 환경에서 4xx는 정상 비즈니스 응답 — http_req_failed 집계 제외)
  const unfollowRes = http.del(
    `${BASE}/v1/instructors/${instructorId}/follows`,
    null,
    {
      headers: studentHeaders,
      tags: { name: 'unfollow-instructor' },
      responseCallback: http.expectedStatuses({ min: 200, max: 499 }),
    },
  );
  check(unfollowRes, { 'unfollow ok': (r) => r.status >= 200 && r.status < 500 });

  // 팔로우 (동시성 환경에서 4xx는 정상 비즈니스 응답 — http_req_failed 집계 제외)
  const followRes = http.post(
    `${BASE}/v1/instructors/${instructorId}/follows`,
    null,
    {
      headers: studentHeaders,
      tags: { name: 'follow-instructor' },
      responseCallback: http.expectedStatuses({ min: 200, max: 499 }),
    },
  );
  check(followRes, { 'follow ok': (r) => r.status >= 200 && r.status < 500 });

  // 내 코스 목록 (강사)
  const myCoursesRes = http.get(
    `${BASE}/v1/instructors/me/courses?page=0&size=20`,
    { headers: instructorHeaders, tags: { name: 'my-courses' } },
  );
  check(myCoursesRes, { 'my-courses 200': (r) => r.status === 200 });

  // 수강생 명단 (강사)
  const studentsRes = http.get(
    `${BASE}/v1/instructors/me/courses/${TEST_INSTRUCTOR_COURSE_ID}/students?page=0&size=20`,
    { headers: instructorHeaders, tags: { name: 'my-students' } },
  );
  check(studentsRes, { 'my-students 200': (r) => r.status === 200 });

  sleep(1);
}
