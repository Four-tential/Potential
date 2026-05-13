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

### 대기열 시스템 (Waiting Room)
> 작성 예정

### 결제 안정성
> 작성 예정

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

<br>

<details>
<summary><b>LLM Hallucination - 별점 수치 조작</b></summary>

<br>

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

<br>

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


<br>


## 📊 성능 테스트

> K6 부하 테스트 결과 추가 예정

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