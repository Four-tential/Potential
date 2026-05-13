# Paid 웹훅 처리 성능 테스트 기준

## 1. 문서 목적

이 문서는 결제/환불/웹훅 도메인 중 `Paid 웹훅 처리` 시나리오의 부하 테스트 기준과 실행 방법을 정리한 문서다.

이번 시나리오의 목표는 다음 두 가지다.

1. `POST /v1/webhooks/portone` Paid 웹훅 처리 지연 시간을 측정한다.
2. 웹훅 처리 직후 결제 상태가 실제로 `PAID`로 반영되는지 확인한다.

즉, 단순히 웹훅 엔드포인트가 `200 OK`를 반환하는지만 보는 것이 아니라, **웹훅 이후 결제 상태 전이까지 완료되는지**를 같이 본다.

---

## 2. 왜 미리 준비된 PENDING payment fixture가 필요한가

Paid 웹훅은 결제 준비가 끝난 뒤에 들어오는 후속 이벤트다.

따라서 이 시나리오에서는:

- 주문 생성
- 결제 준비

를 측정 범위에서 제외하고, **이미 `PENDING` 상태인 payment row를 fixture로 준비한 뒤** `Transaction.Paid` 웹훅만 전달한다.

이렇게 분리하는 이유는 다음과 같다.

1. 주문 생성/결제 준비 성능이 웹훅 처리 결과를 흐리지 않는다.
2. `POST /v1/webhooks/portone` 이후의 락, 상태 전이, DB 업데이트, 후속 조회만 분리해서 볼 수 있다.
3. 팀 프로젝트에서 주문 파트와 결제/웹훅 파트 경계를 깔끔하게 유지할 수 있다.

---

## 3. 프로젝트 규모 기준

부트캠프 팀 프로젝트 규모로, 다음처럼 둔다.

- DAU: `500 ~ 3000`
- peak RPS: `15 ~ 45`

이번 Paid 웹훅 시나리오는 이 범위의 중간값인 **30 RPS**를 프로젝트 기준 목표로 사용한다.

선정 이유:

1. 15 RPS는 하한선 검증에는 좋지만 발표용 프로젝트 기준으로는 다소 보수적이다.
2. 45 RPS는 local 반복 검증과 500개 fixture 조건에서 재현성이 떨어질 수 있다.
3. 30 RPS는 팀 프로젝트 규모를 설명하기 쉬운 중간값이다.

---

## 4. Fixture 전략

local 전용 SQL fixture:

- R__seed_payment_webhook_paid.sql

앱은 `local,perf` 프로필로 실행해서 `db/migration-perf` fixture가 함께 적재되도록 맞춘다.

이 파일은 다음 데이터를 준비한다.

- 테스트용 학생 계정 1개
- 테스트용 강사/코스 10개
- `PENDING` 주문 500개
- `PENDING` 결제 500개

웹훅 fixture는 아래 규칙을 따른다.

- `paymentId` UUID 패턴:
  - `93000000-0000-0000-0000-000000000001`
  - ...
  - `93000000-0000-0000-0000-000000000500`
- `pgKey` 패턴:
  - `pperfwebhook000001`
  - ...
  - `pperfwebhook000500`

중요한 점:

> Paid 웹훅이 한 번 처리되면 해당 payment는 `PAID`가 되므로, 같은 fixture를 다시 “첫 처리 경로”로 재사용할 수 없다.

그래서 load 시나리오는 fresh payment fixture pool을 사용하고, 반복 측정 전에는 local fixture를 다시 적재해야 한다.

또한 fixture를 **10개 코스에 분산**해서 넣는다.  
이유는 `PaymentFacade`가 Paid 웹훅 처리 시 `course lock`을 함께 사용하기 때문이다. 모든 payment가 하나의 코스에 몰리면 30 RPS baseline이 아니라 **단일 코스 락 경합 테스트**가 되어버린다.

이번 baseline은 전체 서비스 처리량을 보고 싶기 때문에, **50개 payment씩 10개 코스로 분산**하는 방식을 사용한다.

---

## 5. Load 프로필

현재 Paid 웹훅 load 프로필은 다음과 같다.

| 항목 | 값 |
| --- | --- |
| target RPS | 30 |
| warmup | 5s |
| measure | 10s |
| preAllocatedVUs | 120 |
| maxVUs | 320 |
| fixture pool | 500 pending payments (10 courses x 50) |
| fixture reuse | false |

총 iteration 예산:

```text
30 RPS * (5s + 10s) = 약 450 iterations
```

즉 500개 fixture 안에서 충분히 fresh 경로를 유지할 수 있다.

---

## 6. API Tier와 SLO

튜터님 가이드의 Tier 분류를 Paid 웹훅 시나리오에 적용하면 다음과 같다.

| Tier | API | 목표 |
| --- | --- | --- |
| T3 비동기 | `POST /v1/webhooks/portone` | p95 < 2000ms |
| T1 읽기 | `GET /v1/payments/{paymentId}` | p95 < 300ms |
| T1 읽기 | `GET /v1/payments?status=PAID` | p95 < 400ms |
| 공통 | 전체 에러율 | < 1% |

추가로 사용자/운영 체감 기준으로 다음 흐름 지표를 본다.

| 항목 | 목표 |
| --- | --- |
| `payment_webhook_paid_flow_ms` p95 | < 2500ms |
| `payment_webhook_paid_flow_ms` p99 | < 4000ms |
| `payment_webhook_paid_business_success` | > 99% |

설명:

- `POST /v1/webhooks/portone`는 비동기 서버 콜백이므로 T3 기준인 `2초 이내`로 본다.
- 결제 상세/목록 조회는 여전히 읽기 API라 T1 기준을 유지한다.
- 전체 flow는 웹훅 처리 + 상태 검증 조회가 합쳐지므로 단일 웹훅 요청보다 조금 넉넉하게 본다.

---

## 7. Smoke와 Load의 역할

### Smoke

- 목적: 유효한 Paid 웹훅 1건이 정상 처리되는지 확인
- 검증:
  - 웹훅 응답 `200`
  - 결제 상세 상태가 `PAID`
  - 결제 목록 `status=PAID` 조회가 정상

실행:

```powershell
docker-compose run --rm k6 run /scripts/payment/k6-payment-webhook-paid-smoke.js
```

### Load

- 목적: 프로젝트 기준 30 RPS에서 Paid 웹훅 처리가 SLO를 만족하는지 측정
- 검증:
  - business success rate
  - webhook latency
  - payment detail/list latency
  - 전체 flow latency

실행:

```powershell
docker-compose run --rm k6 run /scripts/payment/k6-payment-webhook-paid-load.js
```

---

### 참고

`POST /v1/webhooks/portone`는 잘못된 서명이나 비즈니스 실패가 있어도 컨트롤러 자체는 `200 OK`로 응답할 수 있다.

따라서:

- **Grafana에서는 latency / throughput / DB pressure를 본다.**
- **최종 성공 판정은 k6의 `payment_webhook_paid_business_success` 결과로 본다.**

즉, 이 시나리오는 Grafana와 k6 결과를 같이 봐야 한다.

---

## 8. 실험 이력과 해석

이번 Paid 웹훅 시나리오는 fixture 배치를 바꿔가며 세 번의 의미 있는 실험을 거쳤다.  
둘 다 버릴 결과가 아니라, **어떤 병목이 어디에 있었는지**를 단계적으로 보여주는 기록이므로 문서에 남긴다.

### 8-1. Smoke 검증 결과

Smoke는 단건 Paid 웹훅 처리 경로가 정상인지 확인하는 목적의 테스트다.

| 항목 | 결과 |
| --- | --- |
| checks | 100.00% |
| http_req_failed | 0.00% |
| webhook paid status | 200 |
| payment detail after webhook | `PAID` 반영 확인 |
| payment list after webhook | `PAID` 조회 확인 |

해석:

- 서명 생성, 웹훅 수신, 상태 전이, 후속 조회까지 **단건 경로는 정상**이다.
- 즉, 이후 load 실패는 “기능 자체가 안 된다”가 아니라 **부하 조건에서 어디가 무너지는지**를 찾는 문제로 본다.

### 8-2. 실험 1: 단일 코스 핫스팟 경합 실험

초기 fixture는 `PENDING payment 500개`를 모두 **하나의 course**에 몰아넣은 상태였다.  
이 상태로 30 RPS load를 걸면 웹훅 처리 시 `course lock`이 한 곳에 집중되므로, 사실상 전체 baseline이 아니라 **단일 코스 핫스팟 경쟁 실험**이 된다.

| 항목 | 결과 |
| --- | --- |
| fixture 배치 | 1개 course에 payment 500개 집중 |
| target RPS | 30 |
| checks | 89.04% |
| payment_webhook_paid_business_success | 0.57% |
| http_req_failed | 2.76% |
| dropped_iterations | 109 |
| `POST /v1/webhooks/portone` p95 | 19.89s |
| `GET /v1/payments/{paymentId}` p95 | 15.06s |

해석:

- Smoke는 통과했으므로 서명 검증이나 기본 웹훅 흐름이 깨진 것은 아니었다.
- 하지만 load에서는 같은 course에 대한 락 경쟁이 극단적으로 몰리면서, 웹훅 이후 `PAID` 상태 반영까지 완료되지 못한 케이스가 대량 발생했다.
- 이 결과는 프로젝트 baseline으로 쓰기에는 부적절하지만, **단일 코스 핫스팟이 발생하면 Paid 웹훅 처리 성능이 급격히 붕괴한다**는 점을 보여주는 의미 있는 결과다.

### 8-3. 실험 2: 10개 코스 분산 30 RPS 실험

핫스팟 효과를 줄이기 위해 fixture를 **10개 course에 50개 payment씩 분산**했다.  
이 실험은 “단일 코스 집중”이 아닌, 프로젝트 규모 기준 30 RPS에서 현재 구현이 어디까지 버티는지를 보는 baseline 후보 실험이다.

| 항목 | 결과 |
| --- | --- |
| fixture 배치 | 10개 course x 50 payments |
| target RPS | 30 |
| checks | 98.98% |
| checks{phase:measure} | 98.22% |
| http_req_failed | 2.01% |
| dropped_iterations | 109 |
| iterations | 9 |
| `POST /v1/webhooks/portone` p95 | 29.83s |
| `http_req_duration` p95 | 30.34s |
| payment_webhook_paid_business_success | 0.00% |
| 주요 에러 | login 500, webhook 500 |

해석:

- 단일 코스 핫스팟을 제거했는데도 `500 INTERNAL_SERVER_ERROR`, 높은 `dropped_iterations`, 매우 큰 p95가 발생했다.
- 즉, 이번 결과는 “단일 코스 락 하나만 풀면 해결된다”가 아니라, **30 RPS Paid 웹훅 부하에서 현재 애플리케이션 전반이 이미 포화 상태에 가깝다**는 신호로 읽는 것이 맞다.
- login 500까지 함께 발생한 것은 웹훅 경로만이 아니라 공용 자원(DB connection, lock 대기, 스레드/요청 처리 여유 등)까지 압박받았다는 정황이다.
- 이 결과는 아직 “통과한 baseline”은 아니지만, **현재 30 RPS Paid 웹훅 시나리오를 통과하지 못한다는 before 상태 기록**으로는 충분히 의미가 있다.

### 8-4. 실험 3: 10개 코스 분산 + pre-login setup 적용 후 재측정

최신 load 스크립트에서는 로그인 부하가 Paid 웹훅 측정을 오염시키지 않도록, 사용자 로그인은 `setup()`에서 한 번만 수행하고 measure 구간에서는 재사용된 access token으로 후속 조회만 수행하도록 바꿨다.

이 조건에서 다시 30 RPS를 걸어 측정한 결과는 아래와 같다.

| 항목 | 결과 |
| --- | --- |
| fixture 배치 | 10개 course x 50 payments |
| login 방식 | `setup()` pre-login |
| target RPS | 30 |
| checks | 87.19% |
| checks{phase:measure} | 88.05% |
| http_req_failed | 0.00% |
| http_req_failed{phase:measure} | 0.00% |
| dropped_iterations | 0 |
| iterations | 450 |
| `POST /v1/webhooks/portone` p95 | 18.69s |
| `GET /v1/payments/{paymentId}` p95 | 10.27s |
| `GET /v1/payments?status=PAID` p95 | 7.29s |
| `payment_webhook_paid_flow_ms` p95 | 20994ms |
| `payment_webhook_paid_business_success` | 32.22% |
| `payment_webhook_paid_business_success{phase:measure}` | 35.33% |
| 주요 실패 | 웹훅 응답은 200이지만, 후속 상세 조회 시 status가 `PENDING`에 머묾 |

해석:

- `http_req_failed`가 0%라는 점에서, 이번엔 네트워크/HTTP 수준의 실패보다는 **비즈니스 완료 실패**가 핵심이라는 점이 더 분명해졌다.
- 로그인은 `setup()`에서 정상 처리되었고, 웹훅 응답도 대부분 200으로 돌아왔다.
- 그런데도 `payment detail after webhook`에서 상태가 `PAID`가 아니라 `PENDING`으로 남는 사례가 대량 발생했다.
- 즉, 현재 30 RPS에서의 핵심 병목은 로그인보다 **Paid 웹훅 처리 자체 또는 그 하위 자원 경쟁(DB, 락, 후속 상태 전이)** 쪽에 더 가깝다.
- `payment detail after webhook`가 200을 반환하면서도 상태가 `PENDING`에 머무는 사례가 반복되므로, 웹훅 요청을 받은 뒤 최종 상태 전이까지 완료되지 못하는 구간이 실제로 존재한다고 볼 수 있다.
- 이 결과는 “인증 부하를 빼도 30 RPS를 버티지 못하고, 특히 상태 전이 완료율이 크게 떨어진다”는 점을 보여주는, 이전 실험보다 더 정제된 **before 성능 기록**이다.

### 8-5. 현재 해석과 다음 측정 원칙

위 세 실험을 종합하면:

1. 단건 Paid 웹훅 경로는 정상이다.
2. 단일 코스 집중 fixture는 핫스팟 실험으로서 의미가 있다.
3. 10개 코스 분산 후에도 30 RPS를 버티지 못했으므로, 현재 Paid 웹훅 경로는 프로젝트 목표 부하에서 개선이 필요하다.
4. 로그인 부하를 제거한 뒤에도 실패했으므로, 현재 병목은 인증보다 **웹훅 처리 경로 자체**에 더 가깝다.
5. 최신 결과에서는 HTTP 500보다 **200 응답 이후 `PAID` 상태 전이 실패**가 더 핵심적인 실패 패턴으로 보인다.

또한 최신 load 스크립트는 **로그인을 `setup()`에서 미리 수행**하도록 바뀌었다.  
이유는 Paid 웹훅 load가 인증 부하에 오염되지 않고, 실제로 보고 싶은 `POST /v1/webhooks/portone`와 후속 상태 반영 경로에 더 집중되도록 하기 위해서다.

따라서 이후 개선 측정부터는:

- 분산 fixture(10개 course x 50 payments)
- pre-login setup 적용된 최신 load 스크립트
- 동일한 30 RPS / 5s / 10s 조건

으로 다시 측정하고, 이번에 확정한 **정식 before baseline**과 before/after를 비교한다.

### 8-6. 2번 시나리오 정식 baseline 결정

이 문서에서 **2번 Paid 웹훅 처리 성능 테스트 시나리오의 정식 baseline**은 아래 조건으로 확정한다.

| 항목 | 결정값 |
| --- | --- |
| baseline 대상 실험 | 실험 3 |
| fixture 배치 | 10개 course x 50 payments |
| login 방식 | `setup()` pre-login |
| target RPS | 30 |
| warmup | 5s |
| measure | 10s |
| fixture reuse | false |

정식 baseline을 이 실험으로 잡는 이유는 다음과 같다.

1. 튜터님 기준의 프로젝트 규모(DAU 500~3000, peak RPS 15~45)와 가장 자연스럽게 연결되는 중간값이 30 RPS다.
2. 단일 코스 집중 실험은 의미는 있었지만, 프로젝트 baseline이라기보다 **핫스팟 경합 실험**에 가깝다.
3. 분산 fixture + pre-login setup 조건은 인증 부하와 단일 코스 핫스팟을 최대한 덜어낸 상태라, **현재 Paid 웹훅 처리 경로의 실제 before 상태**를 가장 공정하게 보여준다.
4. baseline은 반드시 “통과한 결과”일 필요가 없다. 오히려 개선 전 현재 상태를 같은 조건으로 다시 비교할 수 있어야 하므로, **30 RPS에서 실패하는 현재 수치 자체가 before baseline**이 된다.

즉, 2번 시나리오의 정식 baseline은 이렇게 해석한다.

> “분산 fixture와 pre-login setup 조건에서도, 현재 Paid 웹훅 처리 경로는 30 RPS 프로젝트 목표를 만족하지 못한다.”

이 문장을 그대로 before 결론으로 사용하고, 이후 개선 단계에서 **동일한 30 RPS / 5s / 10s 조건**으로 재측정해 before/after를 비교한다.

정식 baseline 수치는 아래와 같이 고정 기록한다.

| 항목 | 정식 baseline 결과 |
| --- | --- |
| checks | 87.19% |
| checks{phase:measure} | 88.05% |
| http_req_failed | 0.00% |
| http_req_failed{phase:measure} | 0.00% |
| dropped_iterations | 0 |
| iterations | 450 |
| `POST /v1/webhooks/portone` p95 | 18.69s |
| `GET /v1/payments/{paymentId}` p95 | 10.27s |
| `GET /v1/payments?status=PAID` p95 | 7.29s |
| `payment_webhook_paid_flow_ms` p95 | 20994ms |
| payment_webhook_paid_business_success | 32.22% |
| payment_webhook_paid_business_success{phase:measure} | 35.33% |


---

## 9. 결과 기록 양식

```markdown
## Paid 웹훅 처리 Baseline

### 테스트 조건
| 항목 | 값 |
| --- | --- |
| target RPS | 30 |
| warmup | 5s |
| measure | 10s |
| fixture | pending payment 500개 |

### k6 결과
| 항목 | 결과 |
| --- | --- |
| checks |  |
| payment_webhook_paid_business_success |  |
| http_req_failed |  |
| POST /v1/webhooks/portone p95 |  |
| GET /v1/payments/{paymentId} p95 |  |
| GET /v1/payments p95 |  |
| payment_webhook_paid_flow_ms p95 |  |
| payment_webhook_paid_flow_ms p99 |  |

### Grafana 확인
| 패널 | 결과 |
| --- | --- |
| POST /v1/webhooks/portone p95 |  |
| POST /v1/webhooks/portone p99 |  |
| GET /v1/payments/{paymentId} p95 |  |
| GET /v1/payments p95 |  |
| Hikari Pending |  |

### 특이사항
- invalid signature 여부:
- webhook 처리 후 PAID 반영 실패 여부:
- dropped iterations 여부:
- 락 경합 / 병목 의심 구간:
```

---

## 10. Grafana 대시보드 확인 항목

Paid 웹훅 시나리오는 k6 결과만 보면 부족하므로, 아래 패널을 함께 본다.

| 패널 | 목적 |
| --- | --- |
| `POST /v1/webhooks/portone p95` | 웹훅 처리 지연 확인 |
| `POST /v1/webhooks/portone p99` | 꼬리 지연 확인 |
| `GET /v1/payments/{paymentId} p95` | 웹훅 직후 상태 조회 지연 확인 |
| `GET /v1/payments p95` | `PAID` 목록 조회 지연 확인 |
| `POST /v1/webhooks/portone throughput` | 실제 처리량 확인 |
| `Hikari Pending` | DB connection 대기 여부 확인 |

특히 이번 실험에서는 다음 조합을 같이 봐야 한다.

- k6 `payment_webhook_paid_business_success`
- Grafana `POST /v1/webhooks/portone p95/p99`
- Grafana `Hikari Pending`
- Grafana throughput

이 조합으로 보면 “응답은 200인데 내부 비즈니스 성공은 실패”, “락/DB 대기로 인해 꼬리 지연이 커짐”, “실제 처리량이 목표 RPS를 못 따라감” 같은 현상을 함께 읽을 수 있다.

---

## 11. 다음 단계 연결

이번 문서는 Paid 웹훅 처리 baseline을 잡기 위한 기준 문서다.

다음 단계는 아래 순서로 이어진다.

1. Paid 웹훅 시나리오 baseline 측정
2. 결제/환불 조회 인덱스 및 쿼리 성능 개선
3. 동일 시나리오로 before/after 비교
4. 결제/환불 조회 캐시 적용
5. 동일 시나리오로 다시 before/after 비교

즉, 이번 문서의 역할은:

> Paid 웹훅 처리 성능을 프로젝트 기준 부하에서 반복 가능한 방식으로 측정하고, 이후 개선 단계의 비교 기준을 만드는 것

이다.
