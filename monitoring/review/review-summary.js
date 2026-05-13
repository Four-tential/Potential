// 후기 요약 AI 성능 테스트 - setup 기반
//
// 목적: 후기 N건 작성 시 LLM 호출 횟수 및 총 소요 시간 측정
//       현재 방식(매 후기마다 LLM 호출) vs 개선 방식(N건마다 LLM 호출) 비교
//
// 사전 조건:
//   - k6_test_data.sql 실행 완료 (유저 10명, 주문 30개, 출석 10개)
//   - 코스 CLOSED 상태 확인
//   - 매 테스트 실행 전 후기/요약 초기화:
//       DELETE FROM review_images WHERE reviews_id IN (SELECT id FROM reviews WHERE course_id = UUID_TO_BIN('00000000-0000-0000-0002-000000000001'));
//       DELETE FROM review_likes  WHERE reviews_id IN (SELECT id FROM reviews WHERE course_id = UUID_TO_BIN('00000000-0000-0000-0002-000000000001'));
//       DELETE FROM reviews WHERE course_id = UUID_TO_BIN('00000000-0000-0000-0002-000000000001');
//       UPDATE courses SET summary = NULL WHERE id = UUID_TO_BIN('00000000-0000-0000-0002-000000000001');
//
// 실행 (현재 방식):
//   STAGE=smoke K6_SCRIPT=review/review-summay.js docker compose --profile k6 run --rm k6
//
// 실행 (개선 방식 - 코드 변경 후 동일 명령 재실행):
//   STAGE=smoke K6_SCRIPT=review/review-summay.js docker compose --profile k6 run --rm k6

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE      = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const COURSE_ID = __ENV.COURSE_ID || '00000000-0000-0000-0002-000000000001';
const PASSWORD  = 'testTEST123!@#';

// 커스텀 메트릭
const reviewCreateDuration = new Trend('review_create_duration', true);
const summaryGetDuration   = new Trend('summary_get_duration', true);
const reviewCreateSuccess  = new Counter('review_create_success');

// 유저 20명 × 주문 3개
const USERS = [
    { email: 'k6-test-01@test.com', orders: ['00000000-0000-0000-0020-000000001001', '00000000-0000-0000-0020-000000001002', '00000000-0000-0000-0020-000000001003'] },
    { email: 'k6-test-02@test.com', orders: ['00000000-0000-0000-0020-000000002001', '00000000-0000-0000-0020-000000002002', '00000000-0000-0000-0020-000000002003'] },
    { email: 'k6-test-03@test.com', orders: ['00000000-0000-0000-0020-000000003001', '00000000-0000-0000-0020-000000003002', '00000000-0000-0000-0020-000000003003'] },
    { email: 'k6-test-04@test.com', orders: ['00000000-0000-0000-0020-000000004001', '00000000-0000-0000-0020-000000004002', '00000000-0000-0000-0020-000000004003'] },
    { email: 'k6-test-05@test.com', orders: ['00000000-0000-0000-0020-000000005001', '00000000-0000-0000-0020-000000005002', '00000000-0000-0000-0020-000000005003'] },
    { email: 'k6-test-06@test.com', orders: ['00000000-0000-0000-0020-000000006001', '00000000-0000-0000-0020-000000006002', '00000000-0000-0000-0020-000000006003'] },
    { email: 'k6-test-07@test.com', orders: ['00000000-0000-0000-0020-000000007001', '00000000-0000-0000-0020-000000007002', '00000000-0000-0000-0020-000000007003'] },
    { email: 'k6-test-08@test.com', orders: ['00000000-0000-0000-0020-000000008001', '00000000-0000-0000-0020-000000008002', '00000000-0000-0000-0020-000000008003'] },
    { email: 'k6-test-09@test.com', orders: ['00000000-0000-0000-0020-000000009001', '00000000-0000-0000-0020-000000009002', '00000000-0000-0000-0020-000000009003'] },
    { email: 'k6-test-10@test.com', orders: ['00000000-0000-0000-0020-000000010001', '00000000-0000-0000-0020-000000010002', '00000000-0000-0000-0020-000000010003'] },
    { email: 'k6-test-11@test.com', orders: ['00000000-0000-0000-0020-000000011001', '00000000-0000-0000-0020-000000011002', '00000000-0000-0000-0020-000000011003'] },
    { email: 'k6-test-12@test.com', orders: ['00000000-0000-0000-0020-000000012001', '00000000-0000-0000-0020-000000012002', '00000000-0000-0000-0020-000000012003'] },
    { email: 'k6-test-13@test.com', orders: ['00000000-0000-0000-0020-000000013001', '00000000-0000-0000-0020-000000013002', '00000000-0000-0000-0020-000000013003'] },
    { email: 'k6-test-14@test.com', orders: ['00000000-0000-0000-0020-000000014001', '00000000-0000-0000-0020-000000014002', '00000000-0000-0000-0020-000000014003'] },
    { email: 'k6-test-15@test.com', orders: ['00000000-0000-0000-0020-000000015001', '00000000-0000-0000-0020-000000015002', '00000000-0000-0000-0020-000000015003'] },
    { email: 'k6-test-16@test.com', orders: ['00000000-0000-0000-0020-000000016001', '00000000-0000-0000-0020-000000016002', '00000000-0000-0000-0020-000000016003'] },
    { email: 'k6-test-17@test.com', orders: ['00000000-0000-0000-0020-000000017001', '00000000-0000-0000-0020-000000017002', '00000000-0000-0000-0020-000000017003'] },
    { email: 'k6-test-18@test.com', orders: ['00000000-0000-0000-0020-000000018001', '00000000-0000-0000-0020-000000018002', '00000000-0000-0000-0020-000000018003'] },
    { email: 'k6-test-19@test.com', orders: ['00000000-0000-0000-0020-000000019001', '00000000-0000-0000-0020-000000019002', '00000000-0000-0000-0020-000000019003'] },
    { email: 'k6-test-20@test.com', orders: ['00000000-0000-0000-0020-000000020001', '00000000-0000-0000-0020-000000020002', '00000000-0000-0000-0020-000000020003'] },
];

const REVIEW_CONTENTS = [
    '강사님이 정말 친절하시고 설명이 명확해서 이해하기 쉬웠습니다. 소규모 수업이라 개인적인 피드백도 받을 수 있어서 좋았어요.',
    '수업 내용은 알차지만 장소가 좁고 환기가 잘 안 됐어요. 강사님 실력은 좋으신데 시설 면에서 아쉬웠습니다.',
    '전반적으로 만족스러운 수업이었습니다. 가격 대비 퀄리티가 좋고 커리큘럼이 체계적이에요.',
    '운동 초보인데도 따라갈 수 있게 잘 이끌어 주셨습니다. 다음에도 수강하고 싶어요.',
    '수업 시간이 조금 짧게 느껴졌지만 내용은 알찼습니다. 심화 과정도 생기면 좋겠어요.',
    '강사님이 열정적이고 수강생 한 명 한 명에게 신경을 많이 써주셨어요.',
    '시설이 많이 노후화 되어 있어서 기분이 좋지 않았어요.',
    '가격 대비 만족도가 높았고, 친구에게도 추천하고 싶은 클래스입니다.',
    '초보자도 쉽게 따라갈 수 있도록 눈높이에 맞춰 설명해 주셔서 좋았습니다.',
    '예약부터 수업까지 전 과정이 편리하고 만족스러웠습니다.',
    '강사님이 각 수강생의 수준을 파악하고 맞춤형으로 지도해 주셔서 실력이 많이 늘었어요.',
    '클래스 분위기가 좋고 수강생들끼리 서로 격려하며 즐겁게 배울 수 있었습니다.',
    '체계적인 커리큘럼 덕분에 단기간에 실력이 향상된 것을 느낄 수 있었어요.',
    '강사님의 전문 지식이 풍부하고 실습 위주 수업이라 실용적이었습니다.',
    '강사님 말투가 굉장히 공격적이여서 기분이 좋지 않았습니다.',
    '예상보다 훨씬 알찬 수업이었어요. 다음 레벨 수업도 기대됩니다.',
    '강사님이 수업 외에도 건강 관리 팁을 많이 알려주셔서 유익했어요.',
    '소규모 인원이라 집중도가 높고 강사님과 소통이 잘 되는 수업이었습니다.',
    '처음엔 어려울 것 같았는데 강사님 덕분에 자신감이 많이 생겼습니다.',
    '강사님 말투가 쎄셔서 주눅이 드네요.',
];

// 1 VU, 1 iteration
export const options = {
    vus: 1,
    iterations: 1,
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        'review_create_duration': ['p(95)<15000'],
        'summary_get_duration':   ['p(95)<500'],
    },
};

function login(email) {
    const res = http.post(
        `${BASE}/v1/auth/login`,
        JSON.stringify({ email, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
    );
    check(res, { 'login 200': (r) => r.status === 200 });
    try { return res.json('data.accessToken'); } catch (_) { return null; }
}

function authHeaders(token) {
    return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } };
}

// ── setup: 후기 10건 순차 작성 + 응답시간 측정 ────────────────────────────
export function setup() {
    console.log('=== 후기 요약 AI 성능 측정 시작 ===');

    const results = [];

    for (let i = 0; i < REVIEW_CONTENTS.length; i++) {
        const user    = USERS[i];
        const orderId = user.orders[0];
        const content = REVIEW_CONTENTS[i];

        const token = login(user.email);
        if (!token) {
            console.error(`[후기 ${i + 1}] 로그인 실패: ${user.email}`);
            continue;
        }

        const start = Date.now();
        const res = http.post(
            `${BASE}/v1/courses/${COURSE_ID}/reviews`,
            JSON.stringify({ orderId, rating: 4, content, imageUrls: [] }),
            { ...authHeaders(token), tags: { name: 'review_create' } },
        );
        const duration = Date.now() - start;

        reviewCreateDuration.add(duration);

        const ok = check(res, { 'review create 2xx': (r) => r.status >= 200 && r.status < 300 });

        if (ok) {
            reviewCreateSuccess.add(1);
            console.log(`[후기 ${i + 1}번째] ${duration}ms — ${content.substring(0, 20)}...`);
            results.push({ index: i + 1, duration });
        } else {
            console.error(`[후기 ${i + 1}번째] 실패: status=${res.status}`);
        }

        sleep(0.3);
    }

    return { results };
}

// ── default: 최종 요약 조회 ───────────────────────────────────────────────
export default function (data) {
    const token = login(USERS[0].email);
    if (!token) return;

    const start = Date.now();
    const res = http.get(
        `${BASE}/v1/courses/${COURSE_ID}/reviews/summary`,
        { ...authHeaders(token), tags: { name: 'summary_get' } },
    );
    summaryGetDuration.add(Date.now() - start);

    check(res, {
        'summary get 200':  (r) => r.status === 200,
        'summary not null': (r) => { try { return r.json('data.summary') !== null; } catch (_) { return false; } },
    });

    try {
        console.log(`\n[최종 요약]\n${res.json('data.summary')}`);
    } catch (_) {}
}

// ── teardown: 결과 정리 ───────────────────────────────────────────────────
export function teardown(data) {
    console.log('\n=== 후기별 응답시간 ===');
    if (data && data.results) {
        let total = 0;
        data.results.forEach(r => {
            console.log(`  ${r.index}번째 후기: ${r.duration}ms`);
            total += r.duration;
        });
        console.log(`  합계: ${total}ms / 평균: ${Math.round(total / data.results.length)}ms`);
    }
    console.log('======================');
    console.log('개선 방식 적용 후 재실행하여 review_create_duration 비교');
}