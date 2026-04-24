# 학생 주문 취소 환불 성능 테스트 기준

## 1. 문서 목적

이 문서는 결제/환불/웹훅 시나리오 중 `학생 주문 취소 환불` 구간의 성능 테스트 기준, 실행 방법, baseline 결과를 정리한 문서다.

이번 시나리오의 목표는 아래 다섯 가지다.

1. `GET /v1/payments/{paymentId}/refund-preview` 응답 시간을 측정한다.
2. `PATCH /v1/orders/{orderId}/cancel` 환불 진입 API 지연 시간을 측정한다.
3. 환불 직후 주문 상태가 실제로 `CANCELLED`로 반영되는지 확인한다.
4. 환불 직후 결제 상태가 실제로 `REFUNDED`로 반영되는지 확인한다.
5. 환불 목록/상세 조회가 정상 동작하는지 확인한다.

즉, 단순히 취소 요청 응답만 보는 것이 아니라 **preview -> 취소 요청 -> 주문 상태 조회 -> 결제 상태 조회 -> 환불 목록/상세 조회**까지 하나의 흐름으로 본다.

---

## 2. local 에서 PG 취소를 스텁으로 처리하는 이유

학생 주문 취소 환불은 내부에서 `PaymentGateway.cancelPayment()`를 호출한다.  
local 환경에서 반복 가능한 baseline을 만들려면 외부 PG 네트워크 지연과 실패를 분리하는 편이 낫다.

그래서 local 프로필에서는 아래 스텁을 사용한다.

- [LocalPaymentGatewayStub.java](/C:/Users/82109/nbc_project/Potential/src/main/java/four_tential/potential/infra/portone/LocalPaymentGatewayStub.java)

이렇게 분리한 이유는 다음과 같다.

1. 외부 PG 호출 지연이 local baseline을 오염시키지 않는다.
2. 우리가 직접 보고 싶은 것은 내부 환불 오케스트레이션, 락, DB 반영, 후속 조회 성능이다.
3. 이후 인덱스/쿼리 개선, 캐시 적용 전후를 같은 조건으로 반복 측정할 수 있다.

주의:

> 이 baseline은 local 기준의 **내부 환불 처리 성능**이다.  
> 실제 PG 네트워크까지 포함한 end-to-end 환불 성능은 별도 통합 환경에서 검증해야 한다.

---

## 3. 프로젝트 규모 기준

튜터님 가이드 [SIZING-AND-SLO.md](/C:/Users/82109/nbc_project/loadlab/docs/SIZING-AND-SLO.md)를 기준으로 팀 프로젝트 규모를 다음처럼 본다.

- DAU: `500 ~ 3000`
- peak RPS: `15 ~ 45`

학생 주문 취소 환불은 전체 사용자 행동 중에서도 **저빈도이지만 중요한 쓰기 경로**에 가깝다.  
그래서 이번 baseline은 전체 peak의 하한선인 **15 RPS**를 기준으로 잡는다.

선정 이유:

1. 결제 준비나 Paid 웹훅보다 실제 발생 빈도가 낮다.
2. 환불은 락, 상태 전이, 환불 이력 생성, 후속 조회가 모두 포함된 무거운 경로다.
3. 따라서 전체 프로젝트 peak를 그대로 가져오기보다 **현실적인 low-frequency critical write path**로 보는 편이 적절하다.

---

## 4. Fixture 전략

local 전용 SQL fixture:

- [R__seed_payment_student_cancel_refund.sql](/C:/Users/82109/nbc_project/Potential/src/main/resources/db/migration-perf/payment/R__seed_payment_student_cancel_refund.sql)

이 파일은 아래 데이터를 준비한다.

- 테스트용 학생 계정 1개
- 테스트용 강사/코스 10개
- `PAID` 주문 500개
- `PAID` 결제 500개
- 환불 가능 기간을 만족하는 코스 일정

fixture 규칙:

- `orderId` 패턴  
  `94000000-0000-0000-0000-000000000001`  
  ...  
  `94000000-0000-0000-0000-000000000500`

- `paymentId` 패턴  
  `95000000-0000-0000-0000-000000000001`  
  ...  
  `95000000-0000-0000-0000-000000000500`

- `pgKey` 패턴  
  `pperfrefund000001`  
  ...  
  `pperfrefund000500`

중요:

> 환불은 한 번 처리되면 같은 order/payment fixture를 다시 fresh 환불 경로로 사용할 수 없다.

그래서 load 시나리오는 fresh paid fixture pool을 사용하고, 반복 측정 전에는 local fixture를 다시 적재해야 한다.

또한 환불 시나리오는 warmup과 measure가 같은 fixture를 공유하면 안 된다.  
warmup에서 소비한 fixture를 measure에서 다시 집으면 preview나 cancel API가 실패해서 결과가 오염된다.

그래서 load 스크립트는 아래처럼 fixture를 분리해서 사용한다.

- warmup: 앞쪽 fixture 구간 사용
- measure: warmup에서 소비한 개수만큼 offset을 두고 다음 fixture 구간 사용

또한 fixture는 **10개 course에 50개씩 분산**한다.  
이유는 학생 환불도 `pgKey lock`과 `course lock`을 함께 사용하기 때문이다. 모든 결제가 한 course에 몰리면 프로젝트 baseline이 아니라 단일 코스 핫스팟 테스트가 되어버린다.

---

## 5. Smoke / Load 성공 판정 원칙

이번 시나리오에서는 `PATCH /v1/orders/{orderId}/cancel` 응답 본문만으로 성공 여부를 판정하지 않는다.

이유:

- 실제 local 실행에서 취소 요청 응답은 `200 OK` 였지만,
- 응답 body의 `status` 값은 `PAID`로 남아 있었고,
- DB 확인 결과는 같은 시점에 `orders.status = CANCELLED`, `payments.status = REFUNDED`, `refunds.status = COMPLETED` 로 정상 반영되어 있었다.

취소 요청 응답의 status가 PAID로 남은 이유는, 환불이 실패해서가 아니라 서버가 응답을 만들 때 최신 DB 상태 대신 이전에 읽어둔 주문 객체를 보고 응답했기 때문일 가능성.

즉, 취소 응답 본문은 stale할 수 있으므로 이번 시나리오는 아래 후속 조회 기준으로 성공을 판정한다.

1. 취소 요청 응답 `200`
2. `GET /v1/orders/{orderId}` 에서 `CANCELLED`, `orderCount = 0`
3. `GET /v1/payments/{paymentId}` 에서 `REFUNDED`
4. `GET /v1/refunds` 와 `GET /v1/refunds/{refundId}` 에서 `COMPLETED`

---

## 6. Load 프로필

현재 학생 주문 취소 환불 load 프로필은 다음과 같다.

| 항목 | 값 |
| --- | --- |
| target RPS | 15 |
| warmup | 5s |
| measure | 10s |
| preAllocatedVUs | 60 |
| maxVUs | 160 |
| fixture pool | paid order/payment 500개 (10 courses x 50) |
| fixture reuse | false |
| warmup / measure fixture 분리 | 적용 |

총 iteration 계산:

```text
15 RPS * (5s + 10s) = 약 225 iterations
```

즉 500개 fixture 안에서 충분히 fresh 환불 경로를 유지할 수 있다.

---

## 7. API Tier 와 SLO

튜터님 가이드의 Tier 분류를 학생 환불 시나리오에 적용하면 다음과 같다.

| Tier | API | 목표 |
| --- | --- | --- |
| T1 읽기 | `GET /v1/payments/{paymentId}/refund-preview` | p95 < 300ms |
| T2 쓰기 | `PATCH /v1/orders/{orderId}/cancel` | p95 < 1500ms |
| T1 읽기 | `GET /v1/orders/{orderId}` | p95 < 300ms |
| T1 읽기 | `GET /v1/payments/{paymentId}` | p95 < 300ms |
| T1 읽기 | `GET /v1/refunds` | p95 < 400ms |
| T1 읽기 | `GET /v1/refunds/{refundId}` | p95 < 300ms |
| 공통 | 전체 에러율 | < 1% |

추가 흐름 지표:

| 항목 | 목표 |
| --- | --- |
| `student_cancel_refund_flow_ms` p95 | < 2000ms |
| `student_cancel_refund_flow_ms` p99 | < 3000ms |
| `student_cancel_refund_business_success` | > 99% |

---

## 8. Smoke 와 Load 실행

### Smoke

목적:

- 학생 환불 1건이 정상 처리되는지 빠르게 확인

검증:

- refund preview 응답 `200`
- 취소 요청 응답 `200`
- 주문 상세 상태 `CANCELLED`
- 결제 상세 상태 `REFUNDED`
- 환불 목록/상세 조회 성공

실행:

```powershell
docker-compose run --rm k6 run /scripts/payment/k6-payment-student-cancel-refund-smoke.js
```

### Load

목적:

- 15 RPS 프로젝트 기준에서 학생 환불 시나리오가 SLO를 만족하는지 측정

검증:

- business success rate
- refund preview / cancel / order detail / payment detail / refund list / refund detail latency
- 전체 flow latency

실행:

```powershell
docker-compose run --rm k6 run /scripts/payment/k6-payment-student-cancel-refund-load.js
```

---

## 9. Smoke 결과

Smoke는 정상 통과했다.

| 항목 | 결과 |
| --- | --- |
| checks | 100.00% |
| http_req_failed | 0.00% |
| iteration_duration | 3.31s |
| 상태 판정 | 통과 |

해석:

- fixture 자체는 정상이다.
- local stub 기반 환불 오케스트레이션은 단건 기준으로는 정상 동작한다.
- 따라서 이후 load 실패는 fixture 구조나 smoke 시나리오 오류가 아니라 **동시성, 락, 자원 경쟁**으로 해석할 수 있다.

---

## 10. 정식 Baseline 결과

이번 결과는 **정식 before baseline으로 확정 가능하다.**

이유:

1. smoke가 먼저 통과했다.
2. warmup / measure fixture 분리 문제를 제거했다.
3. 남아 있는 실패는 `409 CONFLICT 락 획득 실패` 와 높은 지연으로 나타나므로, 테스트 오염이 아니라 현재 구현의 실제 한계로 볼 수 있다.

즉 이번 baseline은 “성공 baseline”이 아니라 **개선 전 현재 상태를 고정한 실패 baseline**이다.

### 테스트 조건

| 항목 | 값 |
| --- | --- |
| target RPS | 15 |
| warmup | 5s |
| measure | 10s |
| fixture | paid order/payment 500개 |
| fixture 배치 | 10 courses x 50 |
| warmup / measure fixture 분리 | 적용 |
| setup 로그인 | 적용 |

### k6 결과

| 항목 | 결과 | 기준 | 판정 |
| --- | --- | --- | --- |
| checks | 93.50% | > 99% | 실패 |
| checks{phase:measure} | 92.22% | > 99% | 실패 |
| student_cancel_refund_business_success | 40.11% | > 99% | 실패 |
| student_cancel_refund_business_success{phase:measure} | 34.28% | > 99% | 실패 |
| http_req_failed | 16.58% | < 1% | 실패 |
| http_req_failed{phase:measure} | 19.49% | < 1% | 실패 |
| dropped_iterations | 54 | 0에 가까울수록 좋음 | 실패 |
| iterations | 172 | 목표 225 근접 | 미달 |
| GET refund-preview p95 | 7.55s | < 300ms | 실패 |
| PATCH order cancel p95 | 15.47s | < 1500ms | 실패 |
| GET order detail after refund p95 | 418.71ms | < 300ms | 실패 |
| GET payment detail after refund p95 | 312.12ms | < 300ms | 실패 |
| GET refund list p95 | 338.27ms | < 400ms | 통과 |
| GET refund detail p95 | 193.30ms | < 300ms | 통과 |
| student_cancel_refund_flow_ms p95 | 21027.5ms | < 2000ms | 실패 |
| student_cancel_refund_flow_ms p99 | 22503.05ms | < 3000ms | 실패 |

### 핵심 해석

- 현재 구현은 **15 RPS 학생 환불 baseline을 만족하지 못한다.**
- 가장 큰 실패 지점은 `refund-preview` 와 `order cancel` 구간이다.
- `409 CONFLICT` 메시지로 드러난 **락 획득 실패**가 주요 증상이다.
- 후속 조회 API는 상대적으로 덜 나쁘고, 특히 `refund list`, `refund detail` 은 기준을 만족했다.
- 즉 병목은 조회보다 **환불 진입과 환불 오케스트레이션 구간**에 집중되어 있다고 볼 수 있다.

이번 결과는 다음 단계 개선의 before 기준으로 충분히 유의미하다.

---

## 11. Grafana 대시보드 확인 항목

학생 환불 시나리오에서는 아래 패널을 함께 본다.

| 패널 | 목적 |
| --- | --- |
| `GET /v1/payments/{paymentId}/refund-preview p95` | preview 조회 지연 확인 |
| `PATCH /v1/orders/{orderId}/cancel p95` | 환불 진입 API 지연 확인 |
| `GET /v1/orders/{orderId} p95` | 취소 후 주문 상태 조회 지연 확인 |
| `GET /v1/payments/{paymentId} p95` | 환불 후 결제 상태 조회 지연 확인 |
| `GET /v1/refunds p95` | 환불 목록 조회 지연 확인 |
| `GET /v1/refunds/{refundId} p95` | 환불 상세 조회 지연 확인 |
| `PATCH /v1/orders/{orderId}/cancel throughput` | 실제 처리량 확인 |
| `Hikari Pending` | DB connection 대기 여부 확인 |

PromQL 예시:

```promql
histogram_quantile(
  0.95,
  sum(rate(http_server_requests_seconds_bucket{uri="/v1/payments/{paymentId}/refund-preview",method="GET"}[1m])) by (le)
)
```

```promql
histogram_quantile(
  0.95,
  sum(rate(http_server_requests_seconds_bucket{uri="/v1/orders/{orderId}/cancel",method="PATCH"}[1m])) by (le)
)
```

```promql
histogram_quantile(
  0.95,
  sum(rate(http_server_requests_seconds_bucket{uri="/v1/orders/{orderId}",method="GET"}[1m])) by (le)
)
```

```promql
histogram_quantile(
  0.95,
  sum(rate(http_server_requests_seconds_bucket{uri="/v1/payments/{paymentId}",method="GET"}[1m])) by (le)
)
```

```promql
histogram_quantile(
  0.95,
  sum(rate(http_server_requests_seconds_bucket{uri="/v1/refunds",method="GET"}[1m])) by (le)
)
```

```promql
histogram_quantile(
  0.95,
  sum(rate(http_server_requests_seconds_bucket{uri="/v1/refunds/{refundId}",method="GET"}[1m])) by (le)
)
```

```promql
sum(rate(http_server_requests_seconds_count{uri="/v1/orders/{orderId}/cancel",method="PATCH"}[1m]))
```

```promql
hikaricp_connections_pending
```

---

## 12. 다음 단계 연결

이번 문서 기준으로 학생 주문 취소 환불 시나리오의 before baseline은 확정되었다.

다음 단계는 아래 순서로 이어진다.

1. 환불 경로의 락 경합, preview 조회, cancel 처리 병목 분석
2. 결제/환불 조회 인덱스 및 쿼리 개선
3. 동일 시나리오로 before/after 비교
4. 결제/환불 조회 캐시 적용
5. 동일 시나리오로 다시 before/after 비교
