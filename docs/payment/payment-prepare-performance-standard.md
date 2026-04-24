# 결제 준비/조회 성능 테스트 기준

## 1. 문서 목적

이 문서는 결제/환불/웹훅 도메인 중 `결제 준비/조회` 시나리오의 부하 테스트 기준과 baseline 결과를 정리한 문서다.

이번 시나리오의 목표는 아래 두 가지다.

1. `POST /v1/payments` 결제 준비 API의 응답 성능을 측정한다.
2. `GET /v1/payments/{paymentId}`, `GET /v1/payments` 조회 API의 응답 성능을 측정한다.

주문 생성은 다른 도메인 담당 범위이므로 이 시나리오에서는 제외하고, `PENDING` 주문 fixture를 local 환경에 미리 준비한 뒤 결제 준비/조회만 측정한다.

---

## 2. 왜 주문 생성은 제외했는가

이번 테스트의 관심사는 주문이 아니라 결제 준비/조회 성능이다.

주문 생성까지 포함하면 다음 문제가 생긴다.

1. 대기열 진입 여부에 따라 결제 API까지 도달하지 못할 수 있다.
2. 주문/좌석/대기열 로직의 영향으로 결제 API 성능이 흐려진다.
3. 결제 파트 성능 문제가 아니라 주문 파트 이슈에 결과가 끌려간다.

그래서 이번 시나리오는 아래 전제로 고정한다.

> 결제 가능한 `PENDING` 주문은 [R__seed_payment_prepare.sql](/C:/Users/82109/nbc_project/Potential/src/main/resources/db/migration-perf/payment/R__seed_payment_prepare.sql)로 미리 준비하고, k6는 그 주문을 받아 결제 준비/조회만 수행한다.

---

## 3. 프로젝트 규모 기준

튜터님 가이드인 [SIZING-AND-SLO.md](/C:/Users/82109/nbc_project/loadlab/docs/SIZING-AND-SLO.md)를 기준으로 보면, 부트캠프 팀 프로젝트 규모는 대략 아래 범위로 볼 수 있다.

- DAU: `500 ~ 3000`
- peak RPS: `15 ~ 45`

이번 프로젝트에서는 가운데 값에 가까운 **30 RPS**를 프로젝트 기준 목표 RPS로 채택한다.

이 기준을 선택한 이유는 아래와 같다.

1. 15 RPS는 하한선 baseline으로는 적절하지만, 팀 프로젝트 목표치로 설명하기에는 다소 보수적이다.
2. 45 RPS는 local 반복 검증과 500개 fixture 조건에서는 부담이 크다.
3. 30 RPS는 DAU 500~3000 구간에서 설명 가능한 중간값이라 발표와 문서화에 가장 무리가 없다.

정리하면:

> 15 RPS 결과는 baseline 기록으로 남기고, 프로젝트 기준 load 프로필은 30 RPS로 운영한다.

---

## 4. fixture 수와 시간 창

현재 local 성능 테스트용 데이터는 아래처럼 준비한다.

- 테스트 학생 계정: 1개
- 성능 테스트용 코스: 1개
- fresh `PENDING` 주문 fixture: `500개`

현재 Spring local 프로필은 아래 파일만 읽도록 설정한다.

- [R__seed_payment_prepare.sql](/C:/Users/82109/nbc_project/Potential/src/main/resources/db/migration-perf/payment/R__seed_payment_prepare.sql)

여기서 중요한 점은 `PaymentFacade`가 같은 주문의 기존 `PENDING` payment를 재사용한다는 점이다.

즉, **진짜 결제 준비 생성 경로를 보려면 fresh order를 계속 써야 한다.**

그래서 500개 fixture를 유지하려면 총 iteration 수가 500개를 넘지 않도록 load 프로필을 맞춰야 한다.

이번 프로젝트용 load 프로필은 아래와 같다.

- target RPS: `30`
- warmup: `5s`
- measure: `10s`

총 iteration 수:

```text
30 RPS * (5s + 10s) = 450 iterations
```

즉 500개 fixture 안에서:

- 450회는 fresh order로 처리 가능
- 50개는 여유 버퍼

가 된다.

---

## 5. API 분류와 SLO

튜터님 문서의 Tier 분류를 현재 시나리오에 맞게 적용하면 아래와 같다.

| Tier | API | 목표 |
| --- | --- | --- |
| T2 쓰기 | `POST /v1/payments` | p95 < 1000ms |
| T1 읽기 | `GET /v1/payments/{paymentId}` | p95 < 300ms |
| T1 읽기 | `GET /v1/payments` | p95 < 400ms |
| 공통 | 전체 에러율 | < 1% |

추가로 사용자 흐름 단위에서도 본다.

| 항목 | 목표 |
| --- | --- |
| `payment_prepare_flow_ms` p95 | < 1800ms |
| `payment_prepare_flow_ms` p99 | < 3000ms |
| `payment_prepare_business_success` | > 99% |

해석은 아래처럼 한다.

- `POST /v1/payments`: 결제 준비 트랜잭션이므로 조회보다 느슨하게 본다.
- `GET /v1/payments/{paymentId}`: 단건 조회이므로 가장 엄격하게 본다.
- `GET /v1/payments`: 목록 조회라 단건보다 약간 여유를 둔다.
- flow p95/p99: 로그인, 결제 준비, 단건 조회, 목록 조회를 묶은 사용자 체감 기준이다.

---

## 6. 15 RPS baseline 측정 결과

아래 결과는 시나리오 검증과 baseline 기록을 위해 먼저 수행한 `15 RPS` 실행 결과다.

### 테스트 조건

| 항목 | 값 |
| --- | --- |
| 시나리오 | 결제 준비/조회 |
| fixture 수 | 500 pending order |
| target RPS | 15 |
| warmup | 10s |
| measure | 20s |
| 총 iteration | 452 |

### k6 결과 요약

| 항목 | 결과 |
| --- | --- |
| checks | 100.00% |
| payment_prepare_business_success | 100.00% |
| http_req_failed | 0.00% |
| `POST /v1/payments` p95 | 159.66ms |
| `GET /v1/payments/{paymentId}` p95 | 84.93ms |
| `GET /v1/payments` p95 | 116.33ms |
| `payment_prepare_flow_ms` p95 | 339ms |
| `payment_prepare_flow_ms` p99 | 611ms |

### baseline 해석

이 결과는 현재 local 환경에서:

1. 시나리오가 정상 동작하고
2. 15 RPS 하한선 baseline에서는 SLO를 충분히 만족하며
3. 다음 단계 인덱스/쿼리/캐시 개선 전 비교 기준점으로 쓰기에 적절하다는 뜻이다

즉 이 표는 이후 before/after 비교의 `Before` 기준으로 저장한다.

---

## 7. 프로젝트 기준 load 프로필

이제부터는 아래 프로필을 프로젝트 기준 load로 사용한다.

| 항목 | 값 |
| --- | --- |
| target RPS | 30 |
| warmup | 5s |
| measure | 10s |
| preAllocatedVUs | 120 |
| maxVUs | 320 |
| fixture pool | 500 fresh order |
| fixture reuse | false |

이 설정은 아래 의미를 가진다.

1. DAU 500~3000 규모에 맞는 중간값 목표 RPS다.
2. 500개 fixture 안에서 fresh order 경로를 유지한다.
3. local에서도 반복 재현 가능한 수준으로 유지한다.

---

## 8. 30 RPS 프로젝트 기준 실행 결과

설정을 30 RPS로 적용한 뒤 local 환경에서 다시 측정한 결과는 아래와 같았다.

### 테스트 조건

| 항목 | 값 |
| --- | --- |
| target RPS | 30 |
| warmup | 5s |
| measure | 10s |
| preAllocatedVUs | 120 |
| maxVUs | 320 |
| 총 iteration | 451 |
| dropped iterations | 0 |

### 결과 요약

| 항목 | 결과 | 기준 | 판정 |
| --- | --- | --- | --- |
| checks | 100.00% | > 99% | 통과 |
| payment_prepare_business_success | 100.00% | > 99% | 통과 |
| http_req_failed | 0.00% | < 1% | 통과 |
| `POST /v1/payments` p95 | 364.23ms | < 1.0s | 통과 |
| `GET /v1/payments/{paymentId}` p95 | 217.87ms | < 300ms | 통과 |
| `GET /v1/payments` p95 | 273ms | < 400ms | 통과 |
| `payment_prepare_flow_ms` p95 | 772ms | < 1.8s | 통과 |
| `payment_prepare_flow_ms` p99 | 844ms | < 3.0s | 통과 |

### 해석

이 결과는 현재 애플리케이션이 30 RPS 프로젝트 기준 부하에서도:

1. 기능적으로 안정적으로 동작하고
2. latency 기준도 모두 만족하며
3. dropped iterations 없이 목표 처리량을 유지했다는 뜻이다

즉 이번 30 RPS 결과는 **프로젝트 기준 baseline으로 채택 가능한 결과**라고 볼 수 있다.

이 표는 이후 인덱스/쿼리/캐시 개선 전후를 비교할 때 `Before` 기준으로 사용한다.

---

## 9. 실행 방법

### 1. local 프로필로 앱 실행

[application-local.yml](/C:/Users/82109/nbc_project/Potential/src/main/resources/application-local.yml)에서 local 환경일 때만 아래 SQL이 실행되도록 설정해두었다.

- [R__seed_payment_prepare.sql](/C:/Users/82109/nbc_project/Potential/src/main/resources/db/migration-perf/payment/R__seed_payment_prepare.sql)

### 2. smoke

```powershell
docker-compose run --rm k6 run /scripts/payment/k6-payment-prepare-smoke.js
```

### 3. project-scale load

```powershell
docker-compose run --rm k6 run /scripts/payment/k6-payment-prepare-load.js
```

---

## 11. 다음 단계 연결

이 문서와 현재 baseline 결과를 기준으로 아래 순서로 진행한다.

1. 결제/환불 조회 인덱스 및 쿼리 성능 개선
2. 같은 load 시나리오로 before/after 비교
3. 결제/환불 조회 캐시 적용
4. 같은 load 시나리오로 다시 before/after 비교

