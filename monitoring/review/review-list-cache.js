/**
 * =====================================================
 * 후기 목록 조회 - Stage 2: 캐싱 + 페이지네이션
 * 조건: 인덱스X / 캐싱O / 페이지네이션O
 * 목적: 캐싱 + 페이지네이션 적용 효과 측정
 *
 * 실행 전 체크:
 *   1. 캐싱 + 페이지네이션 코드 적용 후 서버 재시작
 *   2. Redis 초기화: docker exec -it redis-container redis-cli FLUSHDB
 *
 * 실행:
 *   MSYS_NO_PATHCONV=1 docker compose --profile k6 run --rm \
 *     -e BASE_URL=http://host.docker.internal:8080 \
 *     k6 run -o experimental-prometheus-rw /scripts/review/review-list-cache.js
 *
 * 총 소요시간: 약 4분 30초
 * =====================================================
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const COURSE_ID = __ENV.COURSE_ID || '00000000-0000-0000-0000-000000000201';

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
        http_req_duration: ['p(95)<500'],  // 캐싱 후 p95 500ms 이내
    },

    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],

};

export function setup() {
    const res = http.post(
        `${BASE_URL}/v1/auth/login`,
        JSON.stringify({ email: 'user1@user.com', password: 'testTEST123!@#' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    const token = JSON.parse(res.body)?.data?.accessToken;
    if (!token) {
        throw new Error('로그인 실패: 토큰을 받지 못했습니다');
    }
    return { token };
}

export default function ({ token }) {
    // VU별 초기 지연: 캐시 응답이 빠를 때 VU들이 동시에 요청 몰리는 현상 방지
    if (__ITER === 0) {
        sleep(__VU * 0.05);
    }

    const res = http.get(
        `${BASE_URL}/v1/courses/${COURSE_ID}/reviews?page=0&size=20`,
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`,
            },
            timeout: '10s',  // 기본 60s지만 명시적으로 설정
        }
    );

    check(res, {
        'status 200': (r) => r.status === 200,
        'body 존재':  (r) => r.body?.length > 0,
    });

    sleep(Math.random() * 1 + 0.5 + (__VU % 10) * 0.1);
}

export function handleSummary(data) {
    const d = data.metrics['http_req_duration'];
    const e = data.metrics['http_req_failed'];
    const r = data.metrics['http_reqs'];

    console.log('\n========================================');
    console.log('  [Stage 2] 캐싱 + 페이지네이션 (인덱스X / 캐싱O)');
    console.log('========================================');
    console.log(`  총 요청 수    : ${r?.values?.count ?? '-'}`);
    console.log(`  평균 응답시간 : ${d?.values?.avg?.toFixed(2) ?? '-'} ms`);
    console.log(`  p90 응답시간  : ${d?.values['p(90)']?.toFixed(2) ?? '-'} ms`);
    console.log(`  p95 응답시간  : ${d?.values['p(95)']?.toFixed(2) ?? '-'} ms`);
    console.log(`  p99 응답시간  : ${d?.values['p(99)']?.toFixed(2) ?? '-'} ms`);
    console.log(`  최대 응답시간 : ${d?.values?.max?.toFixed(2) ?? '-'} ms`);
    const totalReqs = r?.values?.count ?? 0;
    const failedReqs = e?.values?.fails ?? 0;
    const errorRate = totalReqs > 0 ? (failedReqs / totalReqs * 100) : 0;
    console.log(`  에러율        : ${errorRate.toFixed(2)} % (${failedReqs}건 실패)`);
    console.log('========================================');
    console.log('  ⬇ 이 수치를 stage1(베이스라인) 결과와 비교하세요');
    console.log('========================================\n');

    return {};
}