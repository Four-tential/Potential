import http from 'k6/http';
import {check, sleep} from 'k6';
import {Counter, Trend} from 'k6/metrics';

/**
 * [V2 테스트 설정]
 * 100명의 유저가 동시에 진입하는 시나리오에 최적화됨
 */
const CONFIG = {
    VU_COUNT: parseInt(__ENV.VU_COUNT || '100'),
    COURSE_CAPACITY: parseInt(__ENV.COURSE_CAPACITY || '20'),
    BASE_URL: __ENV.BASE_URL || 'http://host.docker.internal:8080',
    BATCH_SIZE: 50,
    TEST_ID: Date.now(),
    // 매 테스트마다 고유한 유저 생성을 위해 TEST_ID 조합
    USER_PREFIX: 'v2_' + (Date.now() % 100000),
};

// 커스텀 메트릭
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
    setupTimeout: '5m',
    scenarios: {
        burst_100: {
            executor: 'per-vu-iterations',
            vus: CONFIG.VU_COUNT,
            iterations: 1,
            startTime: '0s',
            maxDuration: '1m',
        },
    },
    thresholds: {
        'order_success_count': [`count>=${Math.min(CONFIG.VU_COUNT, CONFIG.COURSE_CAPACITY)}`],
        'http_req_failed': ['rate<0.01'], // 1% 미만으로 엄격하게 관리
    },
};

function formatLocalDateTime(date, offsetHours = 0) {
    const d = new Date(date.getTime() + (offsetHours * 3600000));
    return d.toISOString().split('.')[0];
}

export function setup() {
    console.log(`--- [V2 시작] 100 VU 동시성 테스트 (ID: ${CONFIG.TEST_ID}, PREFIX: ${CONFIG.USER_PREFIX}) ---`);

    const adminToken = login('admin@admin.com', 'testTEST123!@#');
    const instructorToken = login('user2@user.com', 'testTEST123!@#');
    const baseTime = new Date(new Date().getTime() + (12 * 3600000));

    const createCourseRes = http.post(`${CONFIG.BASE_URL}/v1/course-requests`, JSON.stringify({
        title: `V2 성능 테스트 ${CONFIG.TEST_ID}`,
        description: '100 VU 동시성 검증용 코스입니다.',
        addressMain: '서울특별시 강남구',
        addressDetail: 'V2 센터',
        price: 30000,
        capacity: CONFIG.COURSE_CAPACITY,
        orderOpenAt: formatLocalDateTime(baseTime, 0),
        orderCloseAt: formatLocalDateTime(baseTime, 24),
        startAt: formatLocalDateTime(baseTime, 48),
        endAt: formatLocalDateTime(baseTime, 50),
        level: 'BEGINNER',
        imageUrls: ['https://example.com/v2.jpg']
    }), {headers: {'Content-Type': 'application/json', 'Authorization': `Bearer ${instructorToken}`}});

    if (createCourseRes.status !== 201) throw new Error(`코스 생성 실패: ${createCourseRes.status}`);
    const courseId = JSON.parse(createCourseRes.body).data.courseId;

    const approveRes = http.patch(`${CONFIG.BASE_URL}/v1/admin/course-requests/${courseId}`, JSON.stringify({action: 'APPROVE'}),
        {headers: {'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}`}});
    if (approveRes.status !== 200) throw new Error(`코스 승인 실패: ${approveRes.status}`);

    const reconcileRes = http.post(`${CONFIG.BASE_URL}/v1/admin/orders/inventory/reconcile?courseId=${courseId}`, null,
        {headers: {'Authorization': `Bearer ${adminToken}`}});
    if (reconcileRes.status !== 200) console.warn(`재고 초기화 경고: ${reconcileRes.status}`);
    else console.log(`--- Redis 재고 초기화 완료 (Capa: ${CONFIG.COURSE_CAPACITY}) ---`);

    console.log(`--- 유저 준비 중 (${CONFIG.VU_COUNT}명) ---`);
    const tokens = [];
    for (let i = 1; i <= CONFIG.VU_COUNT; i += CONFIG.BATCH_SIZE) {
        const signupReqs = [], loginReqs = [];
        for (let j = 0; j < CONFIG.BATCH_SIZE && (i + j) <= CONFIG.VU_COUNT; j++) {
            const email = `${CONFIG.USER_PREFIX}_${i + j}@example.com`, pw = 'Password123!@#';
            signupReqs.push({
                method: 'POST', url: `${CONFIG.BASE_URL}/v1/auth/signup`,
                body: JSON.stringify({email, password: pw, name: `V2User${i + j}`, phone: `010-2222-${String(i + j).padStart(4, '0')}`}),
                params: {headers: {'Content-Type': 'application/json'}}
            });
            loginReqs.push({
                method: 'POST', url: `${CONFIG.BASE_URL}/v1/auth/login`,
                body: JSON.stringify({email, password: pw}),
                params: {headers: {'Content-Type': 'application/json'}}
            });
        }
        http.batch(signupReqs);
        const responses = http.batch(loginReqs);
        responses.forEach(r => { if (r.status === 200) tokens.push(JSON.parse(r.body).data.accessToken); });
    }

    if (tokens.length !== CONFIG.VU_COUNT) {
        throw new Error(`사용자 준비 실패: ${tokens.length}/${CONFIG.VU_COUNT} 성공`);
    }

    return {tokens, courseId, adminToken};
}

export function teardown(data) {
    console.log(`--- [V2 종료] 테스트 데이터 정리 시작 (ID: ${CONFIG.TEST_ID}) ---`);

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
    const res = http.post(`${CONFIG.BASE_URL}/v1/auth/login`, JSON.stringify({email, password}), {headers: {'Content-Type': 'application/json'}});
    return JSON.parse(res.body).data.accessToken;
}

export default function (data) {
    if (__VU > CONFIG.VU_COUNT) { sleep(10); return; }

    const token = data.tokens[__VU - 1];
    if (!token) return;

    const payload = JSON.stringify({courseId: data.courseId, orderCount: 1});
    const params = {headers: {'Content-Type': 'application/json', 'Authorization': `Bearer ${token}`}, timeout: '30s'};

    const start = Date.now();
    const res = http.post(`${CONFIG.BASE_URL}/v1/orders`, payload, params);
    const duration = Date.now() - start;

    metrics.duration.total.add(duration);

    if (res.status === 201) {
        metrics.success.add(1);
        metrics.duration.success.add(duration);
    } else if (res.status === 202) {
        metrics.waiting.add(1);
        metrics.duration.waiting.add(duration);
    } else if (res.status === 400) {
        // 이미 주문했거나 대기 중인 정상 거절 케이스
    } else if (res.status === 503) {
        metrics.queueFull.add(1);
    } else {
        metrics.error.add(1);
    }
}
