# 주문 조회 성능 테스트 기준

## 1. 문서 목적

이 문서는 주문 도메인 중 `내 주문 목록 조회` 및 `주문 상세 조회` API의 읽기 성능 기준을 정리한 문서다.
목록 조회의 페이징 성능과 상세 조회의 조인 성능을 검증하는 것을 목표로 한다.

## 2. 테스트 시나리오 개요

1. **Setup:**
   - 테스트용 학생 계정 로그인
   - 기존 주문 데이터 확인 (없을 경우 1개 생성)
2. **Read Load:**
   - **70% 비중:** 내 주문 목록 조회 (다양한 페이지 번호 랜덤 호출)
   - **30% 비중:** 특정 주문 상세 조회 (기존 주문 ID 중 랜덤 호출)

## 3. 프로젝트 규모 및 부하 프로필

- **Executor:** `ramping-arrival-rate` (목표 처리량 기반)
- **목표 RPS:** 30 ~ 45 RPS
  - Stage 1: 30 RPS (1분 유지)
  - Stage 2: 30 RPS (5분 지속)
  - Stage 3: 45 RPS (피크 부하 1분)

## 4. API 분류와 SLO (Service Level Objective)

| Tier | API | 목표 |
| --- | --- | --- |
| T1 읽기 | `GET /v1/orders/me` (목록) | p95 < 500ms |
| T1 읽기 | `GET /v1/orders/{orderId}` (상세) | p95 < 200ms |
| 공통 | 에러율 | < 1% |

- 상세 조회의 경우 PK 기반 조인이므로 가장 엄격한 기준을 적용한다.
- 목록 조회의 경우 페이징 및 다중 필터링 가능성을 고려하여 상세 조회보다 여유를 둔다.

## 5. k6 스크립트 구성 (monitoring/order)

### `k6-order-read.js`
- **특징:** `ramping-arrival-rate`를 사용하여 시스템이 초당 처리할 수 있는 읽기 요청 수를 측정한다.
- **주요 지표:**
  - `http_req_duration{name:OrderList}`: 목록 조회 응답 시간
  - `http_req_duration{name:OrderDetail}`: 상세 조회 응답 시간

## 6. 실행 방법

```powershell
docker-compose run --rm k6 run /scripts/order/k6-order-read.js
```

## 7. 주요 모니터링 포인트

- **인덱스 활용:** 주문 목록 조회 시 `member_id`와 `created_at` 복합 인덱스가 적절히 활용되는지 확인한다.
- **N+1 문제:** 주문 상세 조회 시 연관된 엔티티(코스 등)를 조회할 때 불필요한 추가 쿼리가 발생하는지 레이턴시를 통해 유추한다.
- **캐싱 효율:** 반복적인 상세 조회 요청 시 캐시 적용 전후의 p95 변화를 관찰한다.
