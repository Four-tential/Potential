/**
 * =====================================================
 * 출석 현황 조회 - Stage 2: 인덱스 + 캐싱
 * 조건: 인덱스O (V3) / 캐싱O (@Cacheable TTL 30초)
 * 목적: 베이스라인 대비 성능 개선 효과 측정
 *
 * 실행 전:
 *   1. V3__add_attendance_index.sql 적용 (서버 재시작)
 *   2. Redis FLUSHDB
 *
 * 실행:
 *   MSYS_NO_PATHCONV=1 docker compose --profile k6 run --rm \
 *     -e BASE_URL=http://host.docker.internal:8080 \
 *     k6 run -o experimental-prometheus-rw /scripts/attendance/attendance-list-cache.js
 * =====================================================
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const COURSE_ID = __ENV.COURSE_ID || '00000000-0000-0000-0000-FB0000000001';

const INSTRUCTOR = {
    email: 'loadtest-instructor-1@example.com',
    password: 'testTEST123!@#',
};

export const options = {
    stages: [
        { duration: '30s', target: 10 },
        { duration: '1m',  target: 10 },
        { duration: '30s', target: 30 },
        { duration: '1m',  target: 30 },
        { duration: '30s', target: 50 },
        { duration: '1m',  target: 50 },
        { duration: '30s', target: 0  },
    ],

    thresholds: {
        http_req_failed:   ['rate<0.01'],
        http_req_duration: ['p(95)<200'],
    },

    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
    const res = http.post(
        `${BASE_URL}/v1/auth/login`,
        JSON.stringify(INSTRUCTOR),
        { headers: { 'Content-Type': 'application/json' } }
    );
    const token = JSON.parse(res.body)?.data?.accessToken;
    if (!token) throw new Error('강사 로그인 실패');
    console.log('강사 로그인 성공');
    return { token };
}

export default function ({ token }) {
    if (__ITER === 0) {
        sleep(__VU * 0.05);
    }

    const res = http.get(
        `${BASE_URL}/v1/courses/${COURSE_ID}/attendances`,
        {
            headers: {
                'Authorization': `Bearer ${token}`,
            },
        }
    );

    check(res, {
        'status 200': (r) => r.status === 200,
        'body 존재':  (r) => r.body?.length > 0,
    });

    sleep(Math.random() * 1 + 0.5 + (__VU % 10) * 0.1);

    // default 함수 안에 임시 추가
    if (res.status !== 200) {
        console.log(`비정상 응답 status=${res.status} body=${res.body?.substring(0, 100)}`);
    }
}

export function handleSummary(data) {
    const d = data.metrics['http_req_duration'];
    const e = data.metrics['http_req_failed'];
    const r = data.metrics['http_reqs'];

    const errorPct = ((e?.values?.rate ?? 0) * 100).toFixed(2);

    console.log('\n========================================');
    console.log('  [Stage 2] 출석 현황 조회 (인덱스O / 캐싱O)');
    console.log('========================================');
    console.log(`  총 요청 수    : ${r?.values?.count ?? '-'}`);
    console.log(`  평균 응답시간 : ${d?.values?.avg?.toFixed(2) ?? '-'} ms`);
    console.log(`  p90 응답시간  : ${d?.values['p(90)']?.toFixed(2) ?? '-'} ms`);
    console.log(`  p95 응답시간  : ${d?.values['p(95)']?.toFixed(2) ?? '-'} ms`);
    console.log(`  p99 응답시간  : ${d?.values['p(99)']?.toFixed(2) ?? '-'} ms`);
    console.log(`  최대 응답시간 : ${d?.values?.max?.toFixed(2) ?? '-'} ms`);
    console.log(`  에러율        : ${errorPct} %`);
    console.log('========================================\n');
    console.log('  ⬆ 이 수치를 stage1(베이스라인) 결과와 비교하세요');
    console.log('========================================\n');

    return {};
}