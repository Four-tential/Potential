/**
 * =====================================================
 * QR 스캔 동시성 테스트 - Stage 1: 베이스라인 (인덱스X)
 * 목적: 수강생 동시 QR 스캔 처리량 및 중복 출석 방지 검증
 *
 * 사전 준비:
 *   1. attendance_seed.sql 실행 (학생 10명 ABSENT 레코드 삽입)
 *   2. Redis에 QR 토큰 수동 삽입:
 *      docker exec -it redis-potential redis-cli
 *      SET "qr:token:test-qr-token-k6" "00000000-0000-0000-0000-FB0000000001" EX 600
 *   3. 코스 start_at 현재 시간으로 수정 (QR 유효 10분 이내 조건):
 *      UPDATE courses SET start_at = NOW() - INTERVAL 5 MINUTE
 *      WHERE id = UUID_TO_BIN('00000000-0000-0000-0000-FB0000000001');
 *
 * 실행:
 *   MSYS_NO_PATHCONV=1 docker compose --profile k6 run --rm \
 *     -e BASE_URL=http://host.docker.internal:8080 \
 *     k6 run -o experimental-prometheus-rw /scripts/attendance/qr-scan-baseline.js
 * =====================================================
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const QR_TOKEN  = __ENV.QR_TOKEN  || 'test-qr-token-k6';

// 테스트 학생 10명
const STUDENTS = Array.from({ length: 10 }, (_, i) => ({
    email: `loadtest-student-${i + 1}@example.com`,
    password: 'testTEST123!@#',
}));

export const options = {
    stages: [
        { duration: '10s', target: 10 },   // 학생 10명 동시 접속
        { duration: '1m',  target: 10 },   // 유지 (첫 번째 스캔 성공, 이후 중복 거부)
        { duration: '30s', target: 0  },
    ],

    thresholds: {
        // 중복 스캔 거부(409)는 정상 동작이므로 에러로 보지 않음
        'http_req_failed{expected_response:true}': ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
    },

    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
    // 학생 토큰 발급
    const studentTokens = STUDENTS.map(student => {
        const res = http.post(
            `${BASE_URL}/v1/auth/login`,
            JSON.stringify(student),
            { headers: { 'Content-Type': 'application/json' } }
        );
        console.log(`로그인 응답 status=${res.status} body=${res.body?.substring(0, 100)}`);
        let token = null;
        try {
            token = JSON.parse(res.body)?.data?.accessToken;
        } catch(e) {
            console.error(`파싱 실패: ${e}`);
        }
        if (!token) console.warn(`로그인 실패: ${student.email} status=${res.status}`);
        return token;
    });

    console.log(`학생 토큰 발급: ${studentTokens.filter(Boolean).length}명`);
    console.log(`QR 토큰: ${QR_TOKEN}`);
    return { studentTokens };
}

export default function ({ studentTokens }) {
    // VU ID 기반으로 학생 배정 (10명 순환)
    const studentIndex = (__VU - 1) % 10;
    const token = studentTokens[studentIndex];

    if (!token) {
        console.warn(`학생 토큰 없음 VU=${__VU}`);
        return;
    }

    const res = http.post(
        `${BASE_URL}/v1/attendances/scan`,
        JSON.stringify({ qrToken: QR_TOKEN }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`,
            },
        }
    );

    // 200: 출석 성공 / 409 or 400: 중복 출석 거부 (정상 동작)
    const isExpected = res.status === 200 || res.status === 409 || res.status === 400;
    check(res, {
        '출석 성공 또는 중복 거부 (예상된 응답)': () => isExpected,
        '500 서버 에러 없음':                      (r) => r.status !== 500,
    });

    if (res.status === 200) {
        console.log(`출석 성공 VU=${__VU} student=${studentIndex + 1}`);
    }

    sleep(Math.random() * 0.3 + 0.1 + (__VU % 10) * 0.05);
}

export function handleSummary(data) {
    const d = data.metrics['http_req_duration'];
    const e = data.metrics['http_req_failed'];
    const r = data.metrics['http_reqs'];

    const errorPct = ((e?.values?.rate ?? 0) * 100).toFixed(2);

    console.log('\n========================================');
    console.log('  [QR 스캔] 베이스라인 (인덱스X)');
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