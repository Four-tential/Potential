<div align="center">

# 🎯 Potential
### 원데이 클래스 예약 플랫폼

> 원하는 클래스를 빠르게, 강사와 수강생을 연결하는 클래스 예약 서비스

<br>

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.0.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazonaws&logoColor=white)

**개발 기간** : 2026.04.08 ~ 2026.05.14

</div>

---

## 📋 목차

- [👥 팀원 소개](#-팀원-소개)
- [📌 프로젝트 소개](#-프로젝트-소개)
- [🛠 기술 스택](#-기술-스택)
- [🏗 아키텍처](#-아키텍처)
- [☁️ 인프라 구성](#-인프라-구성)
- [📂 패키지 구조](#-패키지-구조)
- [💡 핵심 기능](#-핵심-기능)
- [🔥 기술적 도전](#-기술적-도전)
- [🐛 트러블 슈팅](#-트러블-슈팅)
- [📊 성능 테스트](#-성능-테스트)
- [🗂 ERD](#-erd)
- [🚀 실행 방법](#-실행-방법)
- [📄 API 명세](#-api-명세)

---

## 👥 팀원 소개

| 역할 | 이름 |
|-----|------|
| 팀장 | 김영재 |
| 팀원 | 김대훈 |
| 팀원 | 김동진 |
| 팀원 | 이한비 |

---

## 📌 프로젝트 소개

**Potential**은 오프라인 클래스를 위한 **선착순 예약 플랫폼**입니다.

- 강사는 클래스를 개설하고 수강생을 모집할 수 있습니다.
- 수강생은 원하는 클래스를 탐색하고 선착순으로 예약할 수 있습니다.
- 동시 접속자가 몰리는 상황에서도 안정적인 예약 처리를 보장합니다.

---

## 🛠 기술 스택

### Libraries & Frameworks
- Java 21
- Spring Boot 4.0.5
- Gradle
- Spring Data JPA
- QueryDSL
- Redisson
- Spring Batch
- Resilience4j

### Database & Caching
- MySQL
- Redis
- PostgreSQL (pgvector)

### Real-time Communication
- SSE (Server-Sent Events)

### Cloud
- AWS EC2
- AWS S3 + CloudFront
- AWS Parameter Store
- AWS ECR
- AWS ECS, Fargate

### Authentication / Authorization
- Spring Security
- JWT
- Spring OAuth 2.0 (Kakao, Google)

### AI
- OpenAI (gpt-4.1-mini, text-embedding-3-small)
- Ollama (llama3.2)
- pgvector (벡터 검색)

### Data Tracking
- Grafana
- Spring Actuator
- Prometheus
- Loki
- Promtail

### CI/CD
- Docker
- GitHub Actions
- SonarCloud + JaCoCo

### Collaboration Tools / Test Tool
- Notion / Slack / Zep
- GitHub / Jira
- ERDCloud / Figma
- SonarQube / CodeRabbit
- K6

---

## 🏗 아키텍처

> 아키텍처 다이어그램 추가 예정

---

## ☁️ 인프라 구성

### CI/CD 파이프라인

```text
GitHub Push (dev 브랜치)
    │
    ▼
GitHub Actions
    ├── Gradle 빌드 & 테스트
    ├── SonarCloud 정적 분석 (JaCoCo 커버리지)
    ├── Docker 이미지 빌드 (linux/arm64 · Graviton)
    ├── AWS ECR Push (커밋 SHA 태그)
    └── AWS SSM으로 EC2 무중단 배포
            └── docker pull → docker stop → docker run
```

### 서버 구성 (AWS EC2 · Amazon Linux 2023 · ARM/aarch64)

| 컴포넌트 | 설명 |
|----------|------|
| **Application** | Spring Boot (port 8080) · eclipse-temurin:21-jre-alpine |
| **MySQL 8.4** | 주 데이터베이스 (Flyway 마이그레이션) |
| **Redis 8.6** | 캐싱 / 분산 락 / 대기열 |
| **PostgreSQL 17 + pgvector** | 벡터 임베딩 저장 (AI 후기 요약) |
| **Prometheus** | 메트릭 수집 (P50 / P95 / P99) |
| **Grafana** | 모니터링 대시보드 · Slack 알림 |
| **Loki + Promtail** | 로그 수집 및 집계 |
| **redis-exporter / mysql-exporter** | DB 메트릭 → Prometheus 연동 |

### 보안 / 설정 관리

- 민감한 환경 변수 (DB, JWT, OAuth, PortOne) 는 **AWS Parameter Store** 에서 주입
- EC2 접속은 IAM 권한 기반 **AWS Session Manager(SSM)** 사용 (SSH 키 불필요)
- Docker 컨테이너는 **비권한 유저(appuser)** 로 실행
- `HEALTHCHECK` : 30초 간격으로 `/actuator/health` 확인, 3회 실패 시 unhealthy

### 이미지 / CDN

- 이미지 업로드 → **AWS S3** (`potential-dev-images`, ap-northeast-2)
- CDN 서빙 → **CloudFront**

---

## 📂 패키지 구조

```
src/main/java/four_tential/potential
├── application          # 서비스 레이어 (도메인별 비즈니스 로직)
│   ├── attendance
│   ├── auth
│   ├── course
│   ├── member
│   ├── order
│   ├── payment
│   └── review
├── domain               # 엔티티 및 레포지토리
│   ├── attendance
│   ├── coupon
│   ├── course
│   ├── member
│   ├── order
│   ├── payment
│   └── review
├── presentation         # 컨트롤러 및 DTO
│   ├── auth
│   ├── course
│   ├── member
│   ├── order
│   ├── payment
│   └── review
├── infra                # 외부 연동 (PortOne, S3, AI 등)
└── common               # 공통 예외, 응답 형식 등
```

---

## 💡 핵심 기능

### 🔐 인증 / 회원
- JWT 기반 인증 및 OAuth 2.0 소셜 로그인 (Kakao, Google)
- 강사 / 수강생 역할 분리

### 📚 클래스 (Course)
- 강사의 클래스 개설 및 승인 요청
- 카테고리 기반 클래스 탐색 및 검색 (QueryDSL)
- 위시리스트 기능

### 🛒 예약 / 주문 (Order)
- 선착순 예약 처리 (동시성 제어)
- 대기열(Waiting Room) 기반 트래픽 분산
- SSE를 활용한 실시간 대기 상태 푸시
- 주문 만료 스케줄링 (Spring Batch)

### 💳 결제 (Payment)
- PortOne V2 연동
- 웹훅 기반 결제 상태 동기화 (멱등성 처리)
- Resilience4j CircuitBreaker 적용 (PortOne 장애 대응)
- 환불 처리

### ✅ 출석 (Attendance)
- QR 코드 기반 출석 처리
- 비관적 락(Pessimistic Lock)으로 TOCTOU 레이스 컨디션 방지

### ⭐ 후기 (Review)
- 수강 완료 후 후기 작성 / 수정 / 삭제
- 이미지 첨부 (S3 + CloudFront)
- 좋아요 토글

### 🎫 쿠폰 (Coupon)
- 고정 금액 / 퍼센트 할인 정책
- 쿠폰 발급 및 사용 상태 관리

### 🤖 AI / 챗봇
- OpenAI / Ollama 기반 LLM을 통한 후기 요약 기능
- pgvector 기반 벡터 유사도 검색 (HNSW 인덱스, cosine 거리)

---

## 🔥 기술적 도전

### 선착순 예약 동시성 제어
> 작성 예정

---

### 대기열 시스템 (Waiting Room)
> 작성 예정

---

### 결제 안정성
> 작성 예정

---

<details>
<summary><b>출석 실시간 조회 — SSE + 트랜잭션 정합성</b></summary>
수강생이 QR을 스캔하면 강사 화면에 출석 현황이 실시간으로 반영되어야 했습니다.

**문제 1 — 커밋 전 SSE 이벤트 발행으로 인한 데이터 불일치**

처음에는 출석 상태를 변경한 직후 SSE 이벤트를 발행했는데, 트랜잭션이 롤백되는 경우 강사 화면에 잘못된 출석 정보가 푸시되는 문제가 발생했습니다.

`TransactionSynchronizationManager`의 `afterCommit()` 훅을 사용해 트랜잭션이 완전히 커밋된 이후에만 SSE 이벤트를 발행하도록 순서를 보장했습니다.

```java
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            sseAttendanceEventPublisher.publish(courseId, attendance);
            attendanceQueryService.evict(courseId); // REST 조회 캐시 무효화
        }
    }
);
```

**문제 2 — SSE 재연결 시 유실되는 출석 현황**

SSE는 연결이 끊기면 그동안 발생한 이벤트가 유실됩니다. 브라우저 새로고침이나 네트워크 단절 후 재연결 시 강사 화면이 비어있는 문제가 있었습니다.

SSE 연결 직후 현재 출석 스냅샷을 `snapshot` 이벤트로 즉시 전송하고, 이후부터 실시간 이벤트를 수신하도록 설계했습니다. 또한 `emitter.onCompletion()` / `onTimeout()` 에서 동일 인스턴스 비교(`deleteIfSame`)로 새 연결이 기존 emitter를 덮어쓰는 경우를 안전하게 처리했습니다.

SSE 타임아웃은 30분으로 설정해 클래스 진행 시간 동안 안정적인 연결을 유지합니다.
</details>

---

<details>
<summary><b>후기 AI 요약 — @Async + Map-Reduce 배치</b></summary>
수강생 후기를 AI로 자동 요약해 잠재 수강생이 빠르게 클래스를 파악할 수 있도록 했습니다.

**문제 1 — LLM 응답 지연으로 인한 API 블로킹**

OpenAI API 호출은 수 초가 소요되어 후기 작성 API 응답이 느려지는 문제가 있었습니다.

`@Async`로 요약 로직을 별도 스레드 풀(`reviewSummaryExecutor`, core 3 / max 5 / queue 20)에서 비동기 처리해 메인 요청 흐름과 완전히 분리했습니다. 큐가 초과되면 요약을 조용히 스킵하고 경고 로그만 남겨 서비스 가용성을 우선시했습니다.

**문제 2 — 누적 갱신 방식의 요약 왜곡**

후기가 쌓일수록 기존 요약에 새 후기를 덧붙이는 방식은 초기 후기 내용이 희석되고 요약의 정확도가 낮아지는 문제가 있었습니다.

매일 새벽 3시 Spring Batch로 **Map-Reduce** 방식의 전체 재요약을 실행합니다. 전체 후기를 100개씩 청크로 나눠 중간 요약(Map)을 생성하고, 중간 요약들을 합쳐 최종 요약(Reduce)을 만들어 토큰 한도 초과 없이 후기 수에 관계없이 처리할 수 있습니다. `ForkJoinPool` 5스레드로 클래스별 병렬 처리를 수행하며, `ShedLock`으로 다중 인스턴스 환경에서 중복 실행을 방지합니다.
</details>

---

## 🐛 트러블 슈팅
<details>

<summary><b>@Async 타이밍 이슈 - 별점 평균 오류</b></summary>

### 문제 상황

후기 작성 시 LLM 요약 갱신을 비동기로 처리하기 위해 `@Async`를 적용했습니다. `updateSummary()` 내부에서 `findAverageRatingByCourseId()`를 호출해 별점 평균을 계산한 뒤 프롬프트에 포함시키려 했으나, 실제 테스트 결과 별점 5건(5, 3, 4, 5, 1)의 평균이 3.6이어야 하는데 1.0이 출력되는 문제가 발생했습니다.

```
DB 실측: 5건(5, 3, 4, 5, 1) → 평균 3.6
LLM 출력: "평균 1.0점"
```

### 원인 분석

로그를 통해 원인을 추적했습니다.

```
[nio-8080-exec-8] ReviewService     : [후기 요약 갱신 조건 충족] reviewCount=5
[nio-8080-exec-8] ReviewService     : findAverageRatingByCourseId 호출
[eview-summary-2] ReviewSummaryService : 기존요약존재=true  ← @Async 스레드 거의 동시 시작
```

`@Async` 스레드(`eview-summary-2`)가 부모 트랜잭션이 커밋되기 전에 시작되면서 DB 조회 시점에 아직 새 후기가 반영되지 않은 이전 값을 읽어오는 문제였습니다. 즉 5번째 후기가 아직 커밋되지 않은 상태에서 평균을 조회하면 1번째 후기(별점 1점)만 있는 상태의 평균 1.0이 반환됩니다.

### 해결 방법

`@Async` 메서드 내부에서 DB를 다시 조회하는 대신, **부모 트랜잭션 안에서 미리 계산한 값을 파라미터로 전달**하는 방식으로 변경했습니다. 이렇게 하면 새 후기가 포함된 정확한 값이 `@Async` 스레드에 전달됩니다.

```java
// ReviewService.create() — 트랜잭션 내부에서 직접 계산 후 전달
long reviewCount = reviewRepository.countByCourseId(courseId);
if (reviewCount == 1 || reviewCount % 5 == 0) {
    // 트랜잭션 안에서 조회 → 새 후기 반영된 정확한 값
    Double avgRating = reviewRepository.findAverageRatingByCourseId(courseId);
    reviewSummaryService.updateSummary(courseId, rating, content, reviewCount, avgRating);
}

// @Async 메서드 — DB 재조회 없이 파라미터 그대로 사용
public void updateSummary(UUID courseId, int rating, String content,
                          long totalCount, double avgRating) { ... }
```

### 결과

별점 평균이 정확하게 LLM 프롬프트에 전달되어 요약 품질이 개선됐습니다. 또한 `@Async` 메서드 내부의 불필요한 DB 조회가 제거되어 성능도 함께 개선됐습니다.
</details>


---


<details>
<summary><b>LLM Hallucination - 별점 수치 조작</b></summary>

### 문제 상황

별점 평균을 프롬프트에 포함시켜 LLM이 요약 첫 문장에 수치를 표현하도록 했는데, LLM이 전달된 수치를 무시하고 임의의 값을 생성하는 문제가 반복적으로 발생했습니다.

```
전달된 값: "평균 3.3점 / 총 6건 / 만족도: 보통"
LLM 출력: "평균 4.3점으로 대체로 만족도가 높습니다."  ← 임의로 수치 변경
```

프롬프트에 "수치를 그대로 사용하라"는 지시를 강화해도 문제가 반복됐습니다.

### 원인 분석

LLM은 학습 데이터 기반으로 "자연스러운 문장"을 생성하는 경향이 있어, 입력된 수치보다 문맥상 자연스러운 수치를 만들어내려는 특성이 있습니다. 또한 평균 수치 자체가 분포를 정확히 반영하지 못하는 문제도 있었습니다. 예를 들어 "5점 4건 + 1점 1건"과 "3점 5건"은 평균이 비슷해도 전혀 다른 분포입니다.

Amazon 사례를 분석한 결과, Amazon도 별점 분포는 UI에서 막대그래프로 별도 표시하고 AI 요약 텍스트에는 수치를 포함하지 않는 방식을 채택하고 있었습니다.

### 해결 방법

**수치를 LLM에 넘기는 방식을 완전히 포기하고**, 별점 구간별 구조화된 요약 방식으로 전환했습니다. 후기에 `[N점]` 형식으로 별점을 함께 전달하고, 출력 형식을 `[긍정] / [보통] / [부정]` 3구간으로 고정했습니다. LLM이 직접 수치를 생성하지 않으므로 Hallucination이 발생할 여지가 없습니다.

```
# 프롬프트 입력 형식
[5점] 강사님이 친절하고 설명이 명확했습니다.
[1점] 장소가 너무 협소합니다.

# 출력 형식 (반드시 준수)
[긍정] (4~5점 후기가 1건 이상일 때만 출력) 공통 칭찬을 1~2문장으로 작성
[보통] (3점 후기가 1건 이상일 때만 출력) 공통 의견을 1~2문장으로 작성
[부정] (1~2점 후기가 1건 이상일 때만 출력) 공통 불만을 1~2문장으로 작성
#키워드1 #키워드2 #키워드3
```

### 결과

수치 Hallucination 문제가 완전히 해결됐습니다. 또한 구간별 요약과 키워드 해시태그 형식으로 사용자가 클래스 평가를 한눈에 파악할 수 있어 UX도 개선됐습니다.
</details>

---

<details>
<summary><b>@RequiredArgsConstructor + @Qualifier 동작 문제</b></summary>

### 문제 상황

`ReviewSummaryBatchJobScheduler`에서 여러 `Job` 빈 중 특정 빈을 주입받기 위해 필드 레벨에 `@Qualifier`를 선언했습니다. 코드 리뷰 중 이 방식이 정상적으로 동작하지 않을 수 있다는 지적을 받았습니다.

```java
// 문제 코드
@RequiredArgsConstructor
public class ReviewSummaryBatchJobScheduler {

    @Qualifier("reviewSummaryBatchJob")  // Lombok이 생성한 생성자에서 무시됨
    private final Job reviewSummaryBatchJob;
}
```

### 원인 분석

Lombok의 `@RequiredArgsConstructor`가 생성자를 생성할 때 필드에 선언된 `@Qualifier` 어노테이션을 복사하지 않습니다. 생성된 생성자에는 `@Qualifier`가 없어서 Spring이 타입 기반으로만 빈을 탐색합니다. 현재는 `Job` 빈이 하나뿐이라 필드명(`reviewSummaryBatchJob`)으로 우연히 매칭되지만, 향후 다른 배치 Job이 추가되면 `NoUniqueBeanDefinitionException`이 발생합니다.

```
lombok.config에 아래를 추가하면 해결 가능하나
lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier

의도를 명확히 하기 위해 생성자 직접 작성을 선택
```

### 해결 방법

`@RequiredArgsConstructor`를 제거하고 생성자를 직접 작성하여 파라미터 레벨에 `@Qualifier`를 적용했습니다.

```java
// 해결 코드
public class ReviewSummaryBatchJobScheduler {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Job reviewSummaryBatchJob;

    public ReviewSummaryBatchJobScheduler(
            JobOperator jobOperator,
            JobRepository jobRepository,
            @Qualifier("reviewSummaryBatchJob") Job reviewSummaryBatchJob  // 파라미터 레벨에 적용
    ) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.reviewSummaryBatchJob = reviewSummaryBatchJob;
    }
}
```

### 결과

`@Qualifier`가 생성자 파라미터 레벨에 명시적으로 선언되어 Spring이 정확한 빈을 주입합니다. 향후 배치 Job이 추가되어도 `NoUniqueBeanDefinitionException` 없이 안전하게 동작합니다.

</details>


---


## 📊 성능 테스트
<details>
<summary><b>후기 목록 조회 성능 개선 : 캐싱 + 페이지네이션 + 인덱스</b></summary>

### 후기 목록 조회 API 성능 개선

| 구분 | 내용 |
| --- | --- |
| **상황** | 코스별 후기 목록 조회 API(`GET/v1/courses/{courseId}/reviews`)가 요청마다 DB Full Scan을 수행. 후기가 500건 이상 쌓이고 동시 사용자가 늘어날수록 응답 지연 심화 |
| **지금 방식** | `findAllByCourseId(courseId)`로 해당 코스의 후기 전체를 매 요청마다 DB에서 조회. 페이지네이션 없음, 캐싱 없음, 인덱스 없음 |
| **문제점** | 1. 매 요청마다 `reviews` 테이블 Full Scan 발생                              2. 동시 사용자 증가 시 HikariCP 커넥션 풀 경합 발생                 3. 전체 데이터를 한 번에 조회하므로 데이터 증가에 비례해 응답시간 증가 
4. 동일 코스 후기를 반복 조회해도 매번 DB 조회 |
| **바꿀 방식** | 세 가지 최적화를 단계적으로 적용 — 
1) 페이지네이션으로 조회 범위 제한
2) Cache-Aside 전략으로 Redis 캐싱
3) 복합 인덱스로 DB 조회 비용 최소화 |
   | **기대 효과** | 캐시 히트 시 DB 조회 완전 생략, 캐시 미스 시에도 인덱스로 조회 비용 절감, 페이지네이션으로 응답 페이로드 크기 고정 |
   | **적용 후** | 1. Redis 캐시 히트율 모니터링
2. 캐시 Evict 빈도 추적 (쓰기 발생 시)
3. DB 슬로우 쿼리 로그로 인덱스 효과 확인 |

---

### 기술 도입 : Cache-Aside (Redis)

### 도입 목적

"동일한 데이터를 반복 조회하는 읽기 중심 API"에서 DB 부하를 줄이기 위해 도입.

### 왜 이 전략인가 — 대안 비교

| 전략 | 탈락 이유 |
| --- | --- |
| Write-Through | 쓰기 시 캐시도 동시 갱신 — 후기는 코스 종료 후 7일 이내만 쓰기 발생하므로 쓰기 빈도가 낮아 불필요한 복잡도 |
| Write-Behind | 쓰기를 캐시에만 먼저 반영 — Redis 장애 시 데이터 유실 위험, 도메인 특성상 불필요 |
| Read-Through | 캐시 레이어가 DB 조회 대행 — Spring Cache 추상화로 Cache-Aside 구현이 더 단순하고 명확 |
| **Cache-Aside** | **읽기 중심 도메인에 최적, 쓰기 시 Evict로 정합성 보장, `@Cacheable` / `@CacheEvict`로 구현 단순** |

### 적용 대상

후기 목록 페이지 단위 조회 (`getCachedReviews(courseId, page, size)`)

### 핵심 설계 포인트

**Self-invocation 문제 방지**`@Cacheable`은 Spring AOP 프록시 기반으로 동작한다. 같은 클래스(`ReviewService`) 내에서 직접 호출하면 프록시를 거치지 않아 캐시가 적용되지 않는다. 캐싱 전담 빈(`ReviewCacheService`)을 별도로 분리하여 프록시를 통해 호출되도록 설계했다.

**직렬화 안정성**`PageResponse<ReviewResponse>`(제네릭 record)를 캐싱하면 Jackson 역직렬화 시 타입 소거로 실패한다. `List<ReviewResponse>`만 캐싱하고 페이지 메타정보(totalCount, totalPages 등)는 캐싱 외부에서 별도 조회하는 방식으로 해결했다.

**캐시 키 설계**

```
캐시 이름: reviewList
캐시 키:   {courseId}:{page}:{size}
Redis 실제 키: reviewList::{courseId}:{page}:{size}
```

페이지 단위로 독립 캐싱하여 특정 페이지만 Evict 가능하도록 설계했다.

**TTL 설계**
10분으로 설정했다. 후기 쓰기는 코스 종료 후 7일 이내에만 발생하므로 이 기간이 지나면 사실상 읽기만 발생한다. 쓰기 발생 시 `@CacheEvict(allEntries = true)`로 즉시 무효화하므로 TTL이 길어도 정합성에 문제 없다.

**Evict 시점**
후기 작성 / 수정 / 삭제 시 `reviewCacheService.evictAll()` 호출로 해당 캐시 이름의 전체 키를 삭제한다.

### 적용하지 않는 경우

| 경우 | 이유 |
| --- | --- |
| 후기 단건 조회 | 반복 조회 빈도가 낮고 캐싱 효과 미미 |
| 좋아요 수 | 실시간성이 중요해 캐시 정합성 관리 비용이 더 큼 |

---

### 기술 도입 : 페이지네이션

### 도입 목적

"대용량 데이터를 한 번에 전체 조회하는 구조"에서 응답 크기와 DB 조회 비용을 고정하기 위해 도입.

### 핵심 설계 포인트

**캐싱과의 관계**
500건 전체를 한 번에 캐싱하면 Redis에 224KB 이상의 데이터가 저장되고, 매 요청마다 역직렬화 비용이 DB 조회보다 오히려 커지는 문제가 발생했다. 페이지 단위(20건)로 캐싱하면 캐시 하나의 크기가 약 9KB 수준으로 줄어 역직렬화 비용이 최소화된다.

**정렬 기준**`created_at DESC`로 최신 후기를 먼저 노출한다. 이 정렬 기준은 복합 인덱스 설계에도 반영했다.

---

### 기술 도입 : 복합 인덱스

### 도입 목적

"특정 조건 필터링 + 정렬이 함께 발생하는 쿼리"에서 Full Scan을 Index Scan으로 전환하기 위해 도입.

### 왜 이 인덱스인가 — 대안 비교

| 인덱스 | 탈락 이유 |
| --- | --- |
| 단일 인덱스 `(course_id)` | WHERE 조건은 최적화되나 ORDER BY를 위한 추가 정렬 비용 발생 |
| **복합 인덱스 `(course_id, created_at DESC)`** | **WHERE + ORDER BY를 Index Scan 한 번으로 처리, Filesort 제거** |

### 적용 대상

`reviews` 테이블 — `findAllByCourseId(courseId, pageable)` 쿼리

```java
@Table(
    name = "reviews",
    indexes = {
        @Index(name = "idx_reviews_course_created", columnList = "course_id, created_at DESC")
    }
)
```

### 핵심 설계 포인트

쿼리 실행 계획이 `WHERE course_id = ? ORDER BY created_at DESC`이므로, 복합 인덱스의 선두 컬럼이 `course_id`(동등 조건), 후행 컬럼이 `created_at DESC`가 되어야 인덱스를 최대한 활용할 수 있다.

---

### K6 부하 테스트 결과

### 테스트 환경

- 대상 API: `GET /v1/courses/{courseId}/reviews`
- 후기 데이터: 500건 (동일 courseId 반복 조회)
- VU: 10 → 30 → 50 (단계적 Ramp-up)
- 총 소요시간: 약 5분

### 결과 비교

| 지표 | Stage 1 (베이스라인) | Stage 2 (인덱스만) | Stage 3 (인덱스+캐싱 +페이징) |
| --- | --- | --- | --- |
| 평균 응답시간 | 24.98 ms | 23.42 ms | 6.91 ms |
| p95 응답시간 | 33.71 ms | 29.87 ms | 8.45 ms |
| p99 응답시간 | 53.58 ms | 34.41 ms | 10.00 ms |
| 에러율 | 0.00% | 0.00% | 0.00% |

### 해석

**Stage 2 — 캐싱 + 페이지네이션**
p95 기준 **33.71ms → 9.20ms로 약 3.7배 개선**됐다. 캐시 히트 시 DB 조회를 완전히 생략하고 Redis에서 직접 반환하므로 응답시간이 일정하게 유지된다.

**Stage 3 — 인덱스 단독**
p99 기준 **53.58ms → 34.41ms로 약 1.6배 개선**됐다. 캐싱 없이 인덱스만 적용한 경우로, DB 조회 자체의 효율은 높아지지만 매 요청마다 DB를 조회하므로 개선폭이 캐싱 대비 작다.

**Stage 4 — 인덱스 + 캐싱**
p95 기준 **33.71ms → 9.53ms로 약 3.5배 개선**됐다. Stage 2(캐싱만)와 유사한 수준이며, 인덱스의 효과는 **캐시 미스 상황에서 극대화**된다. 캐시가 만료되거나 처음 요청이 들어올 때 DB 조회 비용을 줄여 캐시 웜업(warm-up) 시간을 단축하는 역할을 한다.
</details>

---

<details>
<summary><b>후기 요약 성능 테스트 과정</b></summary>

## 백엔드 성능 개선

### 1단계: 후기 작성 시 마다 LLM 호출 후 요약

- 각각의 후기 작성 후 저장할 때 LLM 호출하여 같이 저장

### 2단계: 첫 후기 요약 후 N개 작성 시마다 LLM 호출 후 요약

- 코스에 첫 후기가 작성 되었을 경우 LLM 호출 후 요약
- 첫 후기 이후 예를 들어 5의 배수개의 후기가 들어왔을 때마다 요약

### 3단계: 후기 저장 트랜잭션과 LLM 분리(비동기 처리)

- 1,2단계는 후기 작성, 저장 트랜잭션에 LLM이 포함되어 있어 1~5초 사이의 LLM 호출 시간이 발생
- 해당 LLM 호출 트랜잭션을 원초적으로 분리 시켜 후기 작성, 저장 시간에 대해 영향이 없도록 구성

```java
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "reviewSummaryExecutor")
    public Executor reviewSummaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);      // 기본 스레드 수
        executor.setMaxPoolSize(5);       // 최대 스레드 수
        executor.setQueueCapacity(20);    // 대기 큐 크기
        executor.setThreadNamePrefix("review-summary-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
```

- `corePoolSize = 3` — 평상시 스레드 5개 유지
- `maxPoolSize = 5` — 동시 LLM 호출 최대 5개
- `queueCapacity = 50` — 대기 요청 최대 50개

후기 작성 조건이 코스 종료 후 7일 이내 중복 후기를 작성하지 않고 출석한 자로 한정되어 있어 코스가 많아 후기 작성이 몰리더라도 큰 트래픽으로 이어질 가능성이 낮기에 평상시 스레드를 3개로 잡았습니다.

또한, 후기 작성자가 몰린다는 가정을 해도 초당 10건 이상 넘어가는 경우가 드물 것이기에 LLM 평균 응답시간 2초로 가정 스레드 1개 초당 0.5건 처리가 가능합니다. 후기가 초당 10건 들어와도 5건 마다 LLM을 호출하니 초당 2건의 LLM 호출이 일어나게 되고 이는 스레드 4개 정도면 처리 가능하니 maxPoolSize는 5면 적당하다고 생각했습니다.

## AI 후기 품질 성능 개선

### 누적 요약 시 원문의 내용이 희석되는 문제

- 스케줄러를 통해 서비스 트래픽이 가장 낮은 시간대인 새벽 3시에 전체 통합 요약
- 데이터의 개수가 많아질 경우 토큰이 한 번에 너무 많이 사용되고 시간이 너무 오래 걸림(후기 데이터 1만 건일 때 40분 이상 걸림)
- 해당 문제를 보완하기 위해 청크, 멀티 스레드 도입
    - 청크
        - 청크를 코스 당 후기 100개로 잡아 단일 요청당 토큰 수를 제한하여 LLM API의 토큰 한도 초과를 방지함
        - 후에 reduce연산을 통해 하나로 합침
        - 100건 이하일 경우 reduce 없음
    - 멀티 스레드
        - 후기가 많아질 수록 LLM 호출이 많아지기 때문에 멀티 스레드 도입
        - API Rate Limit을 고려하여 멀티 스레드는 5개로 잡음

  멀티 스레드 도입 전 40분 이상에서 멀티 스레드 5개를 도입 후 10분으로 감축


### 별점 관련 프롬포트 추가

- Amazon 후기 요약 분석

  Amazon은 오히려 전체 평점의 중요성을 줄이고, 수치 점수보다 테마(공통 주제)에 집중
  키워드 기반으로 가장 많이 등장하는 용어를 찾아 긍정/부정 결과와 연결하는 방식

  실제로 평균 별점 4.2~4.5점 제품이 5점 만점 제품보다 더 잘 팔린다는 연구 결과가 있음.
  완벽한 5점짜리 리뷰만 있으면 오히려 가짜처럼 느껴져서 오히려 신뢰도가 떨어지는 결과
  사용자들은 제품의 단점도 알고 싶어하기 때문

- 별점 1~2점, 3~4점, 5점 별점 별 요약 추가
    - 프롬포트

    ```text
    당신은 클래스 후기를 요약하는 도우미입니다.
    아래 규칙을 반드시 따르세요.
    - 오직 한국어만 사용하세요.
    - 후기에 명시된 내용만 사용하고, 없는 내용은 절대 추가하지 마세요.
    - 반드시 아래 출력 형식을 그대로 따르세요. 형식을 바꾸거나 합치지 마세요.
    - 해당 별점 구간의 후기가 0건이면 그 줄 전체를 출력하지 마세요.
    
    [후기 목록] (형식: [별점] 내용)
    {reviews}
    
    출력 형식 (반드시 준수):
    [5점 후기] (5점 후기가 1건 이상일 때만 출력) 5점 후기의 공통 칭찬을 1~2문장으로 작성
    [3~4점 후기] (3~4점 후기가 1건 이상일 때만 출력) 3~4점 후기의 공통 의견을 1~2문장으로 작성
    [1~2점 후기] (1~2점 후기가 1건 이상일 때만 출력) 1~2점 후기의 공통 불만을 1~2문장으로 작성
    
    ```

    - 출력 결과

    ```json lines
    {
        "success": true,
        "status": "OK",
        "message": "후기 요약 조회 성공",
        "data": {
            "courseId": "00000000-0000-0000-0002-000000000001",
            "summary": "[3~4점 후기] 수업 내용은 알차지만 장소가 좁고 환기가 잘 안 되며 주차 공간 부족으로 
            대중교통 이용이 필요합니다. 시설 면에서 아쉬운 점이 있습니다.  
            [2점 후기] 딱 가격 대비 퀄리티 그 이상, 그 이하도 아님"
        }
    }
    ```

- 키워드로 나열

  cource 도메인에 keyword 필드를 추가하여 진행하지 않고 LLM을 통해 기존 summary 필드에 #으로 키워드 구분

    - 프롬포트

    ```text
    당신은 클래스 후기를 요약하는 도우미입니다.
    아래 규칙을 반드시 따르세요.
    - 오직 한국어만 사용하세요.
    - 후기에 명시된 내용만 사용하고, 없는 내용은 절대 추가하지 마세요.
    - 반드시 아래 출력 형식을 그대로 따르세요. 형식을 바꾸거나 합치지 마세요.
    - 해당 별점 구간의 후기가 0건이면 그 줄 전체를 출력하지 마세요.
    - 키워드는 후기에서 자주 언급된 핵심 단어만 추출하고, 3~5개로 제한하세요.
    - 키워드는 반드시 # 기호로 시작하고 공백 없이 작성하세요. 예: #친절한강사
    
    [후기 목록] (형식: [별점] 내용)
    {reviews}
    
    출력 형식 (반드시 준수):
    [5점 후기] (5점 후기가 1건 이상일 때만 출력) 5점 후기의 공통 칭찬을 1~2문장으로 작성
    [3~4점 후기] (3~4점 후기가 1건 이상일 때만 출력) 3~4점 후기의 공통 의견을 1~2문장으로 작성
    [1~2점 후기] (1~2점 후기가 1건 이상일 때만 출력) 1~2점 후기의 공통 불만을 1~2문장으로 작성
    #키워드1 #키워드2 #키워드3
    ```

    - 출력 결과

    ```json
    {
        "success": true,
        "status": "OK",
        "message": "후기 요약 조회 성공",
        "data": {
            "courseId": "00000000-0000-0000-0002-000000000001",
            "summary": "[5점 후기] 강사님의 친절하고 설명이 명확하여 이해하기 쉽고, 
            소규모 수업으로 개별 피드백이 가능해 만족도가 높았습니다.  
            [2점 후기] 딱 가격 대비 퀄리티 그 이상, 그 이하도 아님  
            #친절한강사 #명확한설명 #소규모수업"
        }
    }
    ```

- 별점 별 요약보다 긍정, 보통, 부정의 의견으로 표기해야 더 직관적
    - 프롬포트

    ```text
    당신은 클래스 후기를 요약하는 도우미입니다.
    아래 규칙을 반드시 따르세요.
    - 오직 한국어만 사용하세요.
    - 후기에 명시된 내용만 사용하고, 없는 내용은 절대 추가하지 마세요.
    - 반드시 아래 출력 형식을 그대로 따르세요. 형식을 바꾸거나 합치지 마세요.
    - 해당 구간의 후기가 0건이면 그 줄 전체를 출력하지 마세요.
    - 키워드는 후기에서 자주 언급된 핵심 단어만 추출하고, 3~5개로 제한하세요.
    - 키워드는 반드시 # 기호로 시작하고 공백 없이 작성하세요. 예: #친절한강사
    
    [후기 목록] (형식: [별점] 내용)
    {reviews}
    
    출력 형식 (반드시 준수):
    [긍정] (4~5점 후기가 1건 이상일 때만 출력) 공통 칭찬을 1~2문장으로 작성
    [보통] (3점 후기가 1건 이상일 때만 출력) 공통 의견을 1~2문장으로 작성
    [부정] (1~2점 후기가 1건 이상일 때만 출력) 공통 불만을 1~2문장으로 작성
    #키워드1 #키워드2 #키워드3
    ```

    - 출력 결과

       ```json 
      {
      "success": true,
      "status": "OK",
      "message": "후기 요약 조회 성공",
      "data": {
        "courseId": "00000000-0000-0000-0002-000000000001",
        "summary": "[긍정] 강사님이 친절하시고 강의 내용이 알차다는 평이 있습니다.
        [보통] 장소가 좁고 환기가 잘 안 되는 점과 주차 공간 부족이 아쉬웠다는 의견이 있습니다.
        [부정] 가격 대비 퀄리티가 기대에 못 미친다는 후기가 있습니다.
        #강사 #시설 #가격",
      }
      }
      ```
    
</details>

---

## 🗂 ERD
> ![Portential v12.png](image/Portential%20v12.png)
---

## 🚀 실행 방법

```bash
# 환경 변수 설정
cp .env.example .env

# 빌드
./gradlew clean build

# 실행 (DB, Redis, 모니터링 전체)
docker-compose up -d

# K6 부하 테스트 실행
docker-compose --profile k6 up k6
```

---

## 📄 API 명세

> Swagger / Notion API 문서 링크 추가 예정