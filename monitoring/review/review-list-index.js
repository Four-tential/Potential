/**
 * =====================================================
 * 후기 목록 조회 - Stage 3: 인덱스 적용
 * 조건: 인덱스O / 캐싱O / 페이지네이션O
 *
 *
 * 적용 인덱스:
 *   @Index(name = "idx_reviews_course_created", columnList = "course_id, created_at DESC")
 *   - course_id: WHERE 조건 최적화
 *   - created_at DESC: ORDER BY 최적화 (Full Scan → Index Scan)
 *
 * 실행:
 *   MSYS_NO_PATHCONV=1 docker compose --profile k6 run --rm \
 *     -e BASE_URL=http://host.docker.internal:8080 \
 *     k6 run -o experimental-prometheus-rw /scripts/review/review-list-index.js
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
        http_req_duration: ['p(95)<3000'],
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
    const res = http.get(
        `${BASE_URL}/v1/courses/${COURSE_ID}/reviews`,
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`,
            },
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

    // http_req_failed.rate는 0~1 사이 실패 비율
    const errorPct = ((e?.values?.rate ?? 0) * 100).toFixed(2);

    console.log('\n========================================');
    console.log('  [Stage 3] 인덱스 적용 (인덱스O / 캐싱X)');
    console.log('========================================');
    console.log(`  총 요청 수    : ${r?.values?.count ?? '-'}`);
    console.log(`  평균 응답시간 : ${d?.values?.avg?.toFixed(2) ?? '-'} ms`);
    console.log(`  p90 응답시간  : ${d?.values['p(90)']?.toFixed(2) ?? '-'} ms`);
    console.log(`  p95 응답시간  : ${d?.values['p(95)']?.toFixed(2) ?? '-'} ms`);
    console.log(`  p99 응답시간  : ${d?.values['p(99)']?.toFixed(2) ?? '-'} ms`);
    console.log(`  최대 응답시간 : ${d?.values?.max?.toFixed(2) ?? '-'} ms`);
    console.log(`  에러율        : ${errorPct} %`);
    console.log('========================================\n');

    return {};
}