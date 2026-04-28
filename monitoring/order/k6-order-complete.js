import http from 'k6/http';
import {check, sleep} from 'k6';
import {Counter, Trend} from 'k6/metrics';

/**
 * [테스트 설정]
 * 반복 테스트 속도를 높이기 위해 BATCH_SIZE를 키우고 시나리오 시간을 단축함
 */
const CONFIG = {
    VU_COUNT: parseInt(__ENV.VU_COUNT || '1200'),
    COURSE_CAPACITY: parseInt(__ENV.COURSE_CAPACITY || '20'),
    BASE_URL: __ENV.BASE_URL || 'http://app:8080',
    BATCH_SIZE: 100, // 배치 크기 확대 (Setup 시간 단축)
    TEST_ID: Date.now(),
    USER_PREFIX: 'fixed_perf',
};

// 커스텀 메트릭 정의
const metrics = {
    success: new Counter('order_success_count'),
    waiting: new Counter('order_waiting_count'),
    queueFull: new Counter('order_queue_full_count'),
    error: new Counter('order_error_count'),
    duration: {
        total: new Trend('order_response_time'),
        success: new Trend('order_duration_success'),
        waiting: new Trend('order_duration_waiting'),
    }
};

export const options = {
    setupTimeout: '15m',
    scenarios: {
        // 짧고 강력한 폭주 테스트 (락 병목 확인용)
        surge_order: {
            executor: 'per-vu-iterations',
            vus: CONFIG.VU_COUNT,
            iterations: 1,
            startTime: '0s',
            maxDuration: '1m',
        },
        // 완만한 부하 테스트 (램핑 시간 단축)
        ramping_order: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                {duration: '30s', target: 200},
                {duration: '1m', target: 500},
                {duration: '30s', target: 0},
            ],
            startTime: '1m',
        },
    },
    thresholds: {
        'order_success_count': [`count>=${Math.min(CONFIG.VU_COUNT, CONFIG.COURSE_CAPACITY)}`],
    },
};

function formatLocalDateTime(date, offsetHours = 0) {
    const d = new Date(date.getTime() + (offsetHours * 3600000));
    return d.toISOString().split('.')[0];
}

// -------------------------------------------------------------------------
// 1. Setup Phase: 유저 준비 및 코스 생성
// -------------------------------------------------------------------------
export function setup() {
    console.log(`--- [시작] 테스트 환경 준비 (ID: ${CONFIG.TEST_ID}, VU: ${CONFIG.VU_COUNT}) ---`);

    const adminToken = login('admin@admin.com', 'testTEST123!@#');
    const instructorToken = login('user2@user.com', 'testTEST123!@#');

    const baseTime = new Date(new Date().getTime() + (12 * 3600000));

    console.log(`--- 코스 생성 시도 (Capa: ${CONFIG.COURSE_CAPACITY}) ---`);
    const createCourseRes = http.post(`${CONFIG.BASE_URL}/v1/course-requests`, JSON.stringify({
        title: `성능 테스트 코스 ${CONFIG.TEST_ID}`,
        description: '자동 생성된 테스트용 코스입니다.',
        addressMain: '서울특별시 강남구 테헤란로',
        addressDetail: 'k6 테스트 센터',
        price: 50000,
        capacity: CONFIG.COURSE_CAPACITY,
        orderOpenAt: formatLocalDateTime(baseTime, 0),
        orderCloseAt: formatLocalDateTime(baseTime, 24),
        startAt: formatLocalDateTime(baseTime, 48),
        endAt: formatLocalDateTime(baseTime, 50),
        level: 'BEGINNER',
        imageUrls: ['https://example.com/test.jpg']
    }), {headers: {'Content-Type': 'application/json', 'Authorization': `Bearer ${instructorToken}`}});

    if (createCourseRes.status !== 201) {
        throw new Error(`코스 생성 실패: ${createCourseRes.status}`);
    }
    const courseId = JSON.parse(createCourseRes.body).data.courseId;

    http.patch(`${CONFIG.BASE_URL}/v1/admin/course-requests/${courseId}`, JSON.stringify({action: 'APPROVE'}),
        {headers: {'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}`}});

    console.log(`--- 코스 준비 완료 (ID: ${courseId}) ---`);

    console.log(`--- 학생 사용자 로그인/가입 시작 (Batch: ${CONFIG.BATCH_SIZE}) ---`);
    const tokens = [];
    for (let i = 1; i <= CONFIG.VU_COUNT; i += CONFIG.BATCH_SIZE) {
        const signupReqs = [], loginReqs = [];
        for (let j = 0; j < CONFIG.BATCH_SIZE && (i + j) <= CONFIG.VU_COUNT; j++) {
            const email = `${CONFIG.USER_PREFIX}_${i + j}@example.com`, pw = 'Password123!@#';
            signupReqs.push({
                method: 'POST',
                url: `${CONFIG.BASE_URL}/v1/auth/signup`,
                body: JSON.stringify({
                    email, password: pw, name: `User${i + j}`, phone: `010-0000-${String(i + j).padStart(4, '0')}`
                }),
                params: {headers: {'Content-Type': 'application/json'}}
            });
            loginReqs.push({
                method: 'POST',
                url: `${CONFIG.BASE_URL}/v1/auth/login`,
                body: JSON.stringify({email, password: pw}),
                params: {headers: {'Content-Type': 'application/json'}}
            });
        }

        http.batch(signupReqs);
        const responses = http.batch(loginReqs);

        responses.forEach(r => {
            if (r.status === 200) tokens.push(JSON.parse(r.body).data.accessToken);
        });

        if (i % 500 === 1) console.log(`진행 상황: ${Math.min(i + CONFIG.BATCH_SIZE - 1, CONFIG.VU_COUNT)}/${CONFIG.VU_COUNT} 완료`);
    }

    return {tokens, courseId, adminToken};
}

/**
 * [Teardown Phase] 테스트 종료 후 데이터 정리
 */
export function teardown(data) {
    console.log(`--- [종료] 테스트 데이터 정리 시작 (ID: ${CONFIG.TEST_ID}) ---`);

    const params = {
        headers: {
            'Authorization': `Bearer ${data.adminToken}`,
            'Content-Type': 'application/json'
        }
    };

    const res = http.del(`${CONFIG.BASE_URL}/v1/orders/performance-test-data`, null, params);

    if (res.status === 200) {
        console.log('--- [성공] 성능 테스트 데이터가 일괄 삭제되었습니다. ---');
    } else {
        console.error(`--- [실패] 데이터 정리 중 오류 발생: ${res.status} ---`);
        console.error(res.body);
    }
}

function login(email, password) {
    const res = http.post(`${CONFIG.BASE_URL}/v1/auth/login`, JSON.stringify({
        email, password
    }), {headers: {'Content-Type': 'application/json'}});
    return JSON.parse(res.body).data.accessToken;
}

// -------------------------------------------------------------------------
// 2. Main Run Phase
// -------------------------------------------------------------------------
export default function (data) {
    const token = data.tokens[__VU - 1];
    if (!token) {
        metrics.error.add(1);
        sleep(1);
        return;
    }

    const payload = JSON.stringify({courseId: data.courseId, orderCount: 1});
    const params = {headers: {'Content-Type': 'application/json', 'Authorization': `Bearer ${token}`}, timeout: '30s'};

    const start = Date.now();
    const res = http.post(`${CONFIG.BASE_URL}/v1/orders`, payload, params);
    const duration = Date.now() - start;

    metrics.duration.total.add(duration);

    const isOk = check(res, {'expected status': (r) => [201, 202, 503].includes(r.status)});

    if (isOk) {
        if (res.status === 201) {
            metrics.success.add(1);
            metrics.duration.success.add(duration);
        } else if (res.status === 202) {
            metrics.waiting.add(1);
            metrics.duration.waiting.add(duration);
        } else if (res.status === 503) {
            metrics.queueFull.add(1);
        }
    } else {
        metrics.error.add(1);
    }
}
