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
- dev
<img width="5924" height="4444" alt="image" src="https://github.com/user-attachments/assets/34cc6b47-2e87-409e-bece-5a3b36ac11c7" />

- prod (main)
<img width="1828" height="1405" alt="image" src="https://github.com/user-attachments/assets/68f9112d-e89f-4a9d-a302-6bd33f39f397" />

---

## ☁️ 인프라 구성

### 환경 분리 개요

본 프로젝트는 **두 환경**으로 분리되어 운영됩니다.

| 구분 | Dev (개발) | Prod (운영) |
| --- | --- | --- |
| **컴퓨트** | EC2 (`t4g.small`) + Docker 컴포즈 | **ECS Fargate** (1 vCPU / 3 GB) |
| **아키텍처** | `linux/arm64` (Graviton) | `linux/amd64` (X86_64) |
| **DB** | RDS MySQL + RDS PostgreSQL (pgvector) | 동일 (`db.t4g.micro` × 2) |
| **Cache** | ElastiCache Redis 7.1 | 동일 |
| **외부 노출** | 없음 (SSM 세션 전용) | **ALB HTTPS:443** (ACM TLS 1.3) |
| **모니터링** | Prometheus + Grafana + Loki (self-host) | **AMP + ADOT 사이드카** + Grafana (Private EC2) |
| **트리거 브랜치** | `dev` push | `main` push |
| **OIDC Role** | `Potential-github-oidc-role` | `Potential-Prod-github-oidc-role` |

---

### CI/CD 파이프라인

#### Dev — `dev` 브랜치 push 시 자동

```text
GitHub Push (dev 브랜치)
    │
    ▼
GitHub Actions (.github/workflows/dev-cd.yml)
    ├── Gradle 빌드 (bootJar, 테스트 별도)
    ├── QEMU + Buildx — linux/arm64 크로스 빌드
    ├── AWS OIDC 인증 (정적 자격증명 0개)
    ├── ECR Push (potential/dev:<commit-sha>)
    └── AWS SSM Run Command → EC2:
            ├── docker pull
            ├── docker stop / rm 옛 컨테이너
            ├── docker run --restart=always
            └── docker image prune -af --filter "until=24h"   ⭐ 자동 청소
```

> 💡 마지막 `prune` 단계는 [PR #139](https://github.com/Four-tential/Potential/pull/139) 에서 추가. 디스크 누적으로 인한 배포 실패 재발 방지.

#### Prod — `main` 브랜치 push 시 자동

```text
GitHub Push (main 브랜치)
    │
    ▼
GitHub Actions (.github/workflows/prod-cd.yml)
    ├── Gradle 빌드 + 캐시 (bootJar)
    ├── Docker 빌드 — linux/amd64
    ├── AWS OIDC 인증 (브랜치 sub 잠금)
    ├── ECR Push (potential/prod:<commit-sha>)
    ├── infra/taskdef.json 템플릿 sed 치환
    ├── ECS register-task-definition (새 리비전)
    ├── ECS update-service
    │     --health-check-grace-period-seconds 300   ⭐ 콜드 스타트 흡수
    │     --force-new-deployment
    ├── aws ecs wait services-stable (최대 25분)
    └── 서비스 이벤트 출력
```

> 🛡 **Rolling + Circuit Breaker + Auto Rollback** 3중 안전장치로 무중단 배포.

---

### Dev 서버 구성

**EC2** (`t4g.small` · Amazon Linux 2023 · ARM/aarch64)

| 컴포넌트 | 컨테이너 이미지 | 설명 |
| --- | --- | --- |
| **Application** | `eclipse-temurin:21-jre-alpine` | Spring Boot (port 8080) |
| **Prometheus** | `prom/prometheus` | 메트릭 수집 (P50 / P95 / P99) |
| **Grafana** | `grafana/grafana` | 대시보드 + Slack 알림 |
| **Loki + Promtail** | `grafana/loki`, `grafana/promtail` | 로그 수집·집계 |
| **Exporters** | `oliver006/redis_exporter`, `prom/mysqld-exporter`, `prom/node-exporter` | DB·시스템 메트릭 → Prometheus 연동 |

**관리형 데이터 레이어** (EC2 외부)

- RDS MySQL 8.4 — `dev-potential-rds-mysql` (`db.t3.micro`)
- RDS PostgreSQL 17 + pgvector — `dev-potential-rds-postgre` (`db.t3.micro`)
- ElastiCache Redis 7.1 - `potential-dev-redis-001` (`cache.t4g.micro`)

---

### Prod 서버 구성

**ECS Fargate Task** (Serverless · 1 vCPU / 3 GB · X86_64)

| 컨테이너 | 이미지 | 역할 |
| --- | --- | --- |
| `potential-prod-container` | `<ecr>/potential/prod:<sha>` (Spring Boot) | 메인 앱 (`essential: true`) |
| `adot-collector` | `aws-otel-collector:v0.47.0` | **모니터링 사이드카** (`essential: false`) |

**관리형 서비스** (ECS Task 외부)

| 서비스 | 리소스 / 설정 |
| --- | --- |
| **ALB** | `potential-prod-alb` — Multi-AZ, HTTPS:443, ACM TLS 1.3 + 양자내성 |
| **RDS MySQL** | `prod-potential-rds-mysql-1` (`db.t4g.micro`) |
| **RDS PostgreSQL + pgvector** | `prod-potential-rds-postgre-1` (`db.t4g.micro`) |
| **ElastiCache Redis 7.1** | `potential-prod-redis-001` (`cache.t4g.micro`) |
| **S3 + CloudFront** | `potential-prod-images` — Presigned PUT 업로드, CDN 캐싱 |
| **AMP (Managed Prometheus)** | `ws-0e10d9e2-...` — ADOT remote_write 수신 |
| **Grafana on EC2** | `i-00fbb5ea475c7600b` — Private 서브넷, SSM Port Forward 전용 접근 |

**네트워킹** (`prod-vpc` — `10.0.0.0/16`)

- Public Subnet (AZ a/c) · Private 4계층 격리 (ECS / RDS / Redis / Monitoring)
- **VPC Endpoints**: Interface 9개 (ECS / ECR / Logs / SSM / Secrets) + Gateway 1개 (S3)
  → AWS 서비스 호출 100% VPC 내부 트래픽 → NAT 비용 절감 + 인터넷 노출 0
- **NAT Gateway 1개** — 외부 API (Kakao / Google / PortOne / OpenAI) 만 경유

---

### 보안 / 설정 관리

#### 자격증명 0개 원칙

| 항목 | 적용 |
| --- | --- |
| GitHub Actions 자격증명 | **OIDC 페더레이션** (정적 Access Key 0개) — 1시간 단기 STS 토큰 |
| EC2 접속 | **AWS SSM Session Manager** (SSH 키 0개) |
| DB 자격증명 | **AWS Secrets Manager** → ECS 환경변수 자동 주입 |
| 일반 설정 (JWT / OAuth / PortOne / Redis 등) | **AWS Parameter Store SecureString** (KMS 자동 복호화) |

#### IAM Role 5종 (최소권한 분리)

| Role | 환경 | 권한 스코프 |
| --- | --- | --- |
| `Potential-github-oidc-role` | Dev CI/CD | ECR(`potential/dev`) + SSM Send |
| `Potential-Prod-github-oidc-role` | Prod CI/CD | ECR(`potential/prod`) + ECS register/update |
| `ecsTaskExecutionRole` | ECS | 이미지 pull + Secrets / Parameter Store 읽기 |
| `ECS-role-task-S3` | 앱 런타임 | S3 R/W + AMP RemoteWrite |
| `Potential-Monitoring-EC2-Role` | Grafana EC2 | SSM + AMP Query + CloudWatch Read |

#### 컨테이너 / 외부 노출 보안

- Docker 컨테이너는 **비권한 유저(`appuser`)** 로 실행
- `HEALTHCHECK`: 30초 간격 `/actuator/health` 확인, 3회 실패 시 unhealthy
- ALB Listener Rule: `/actuator/*` 외부 노출 차단 (`/actuator/health` 만 허용)
- Grafana: 외부 노출 0 — Private 서브넷 + SSM Port Forward 전용 접근

---

### 모니터링 / 관측성

#### Dev — Self-hosted 스택

- **Prometheus + Grafana + Loki + Promtail + K6 + Exporters** (mysql / redis / node)
- 모두 같은 EC2 위 Docker 컴포즈로 동거

#### Prod — AWS 관리형 + ADOT 사이드카

**앱 메트릭 흐름**

```text
Spring Boot /actuator/prometheus
    └── ADOT 사이드카 (같은 Task, 15초 scrape, localhost)
    └── SigV4 인증 + remote_write ─────▶  AMP (Managed Prometheus)
                                            │
                                            ▼
                                     Grafana on Private EC2
```

**AWS 관리형 서비스 메트릭**

```text
ElastiCache · RDS · ALB · Container Insights
    └── AWS 자동 publish ───────────────▶  CloudWatch
                                            │
                                            ▼
                                Grafana CloudWatch Datasource
```

**분산 트레이싱 / 요청 추적**

```text
MdcFilter ─▶ traceId 부여 ─▶ X-Trace-Id 응답 헤더
    └── 모든 로그에 traceId 자동 포함 (MDC)
    └── CloudWatch Logs Insights 에서 traceId 한 줄로 요청 전체 추적
```

**운영 로그**

- 로그 그룹: `/ecs/potential-prod-ecs-task-definition`
- 보관 정책: **30일**
- 스트림 prefix 분리: `ecs/...` (앱) / `adot/...` (사이드카)

**알림**

| 구분 | 항목 |
| --- | --- |
| ✅ 현재 운영 | AWS Budget `$50/월` (50 · 80 · 100% 임계치 이메일) + Container Insights 기본 알람 |
| 🕓 예정 (별도 PR) | CloudWatch Alarm → Slack/이메일 (ECS Task / ALB 5xx / RDS CPU / Redis Memory) |

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
- PortOne V2 연동 및 결제 준비 / Paid 웹훅 최종 확정 분리
- 웹훅 기반 결제 상태 동기화 (`webhook-id` 멱등 처리)
- Resilience4j CircuitBreaker 적용 (PortOne 장애 전파 차단)
- 전체 / 부분 / 강사 취소 일괄 환불 처리

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

<details>
<summary><b>선착순 예약 동시성 제어</b></summary>
대규모 트래픽 상황에서도 재고(수강 정원)의 정합성을 보장하고 초과 예약을 방지하기 위해 다음과 같은 기술적 장치를 도입했습니다.

- **Redis Lua 스크립트를 통한 원자적 재고 관리**: `GET-DECR` 로직을 하나의 Lua 스크립트로 묶어 Redis 내부에서 원자적으로 실행되도록 구현했습니다. 이를 통해 애플리케이션 레벨의 별도 락 없이도 Race Condition을 방지하고 초과 예약(Over-selling)을 원천 차단했습니다.
- **Redis 기반의 실시간 재고 관리**: DB의 부하를 줄이기 위해 Redis를 Primary Inventory Source로 활용합니다. DB 반영 전 Redis에서 먼저 좌석을 점유하도록 설계하여 응답 속도를 극대화했습니다.
- **Redisson 분산 락 (@DistributedLock)**: 재고 복구(Rollback)나 데이터 정합성 보정이 필요한 핵심 비즈니스 로직에는 Redisson을 이용한 분산 락을 적용하여 다중 인스턴스 환경에서도 데이터 일관성을 유지합니다.
</details>

---

<details>
<summary><b>대기열 시스템 (Waiting Room)</b></summary>
한정된 좌석에 대해 수천 명 이상의 동시 접속자가 몰릴 경우, 서버 부하를 제어하고 사용자에게 공정한 기회를 제공하기 위해 대기열 시스템을 구축했습니다.

- **Redis Sorted Set (ZSET) 활용**: 대기열 진입 순서에 따라 Redis ZSET에 사용자 ID와 시퀀스 번호를 저장합니다. 이를 통해 수만 명의 대기자 중에서도 자신의 순번을 `O(log N)`의 속도로 빠르게 조회할 수 있습니다.
- **SSE (Server-Sent Events) 기반 실시간 알림**: 사용자가 반복적으로 API를 호출(Polling)하는 대신, 서버에서 대기 순번과 입장 가능 상태를 실시간으로 푸시합니다. 이는 클라이언트와 서버 양측의 네트워크 부하를 획기적으로 줄여줍니다.
- **원자적 승격 로직**: 좌석이 확보되었을 때 대기열 1순위 사용자를 자동으로 주문 단계로 승격시키는 프로세스 또한 Lua 스크립트로 구현하여, 찰나의 순간에 발생할 수 있는 '과승격' 문제를 해결했습니다.
</details>

---

<details>
<summary><b>결제 안정성 — 결제 준비와 Paid 웹훅 최종 확정 분리</b></summary>

PortOne 결제는 클라이언트 결제창 응답만으로 성공을 확정하면 중복 결제, 중복 웹훅, 좌석 초과 확정 같은 문제가 발생할 수 있었습니다.  
특히 같은 주문에 대한 중복 결제 준비와, 같은 코스 좌석을 두고 여러 결제가 동시에 `Paid` 로 확정되는 상황을 함께 다뤄야 했습니다.

### 문제 상황

- 사용자가 결제 버튼을 여러 번 누르거나 재시도하면 같은 주문에 대한 결제 준비 요청이 중복될 수 있음
- PortOne 웹훅은 같은 결제건에 대해 재전송될 수 있어 같은 `pgKey` 를 여러 번 처리할 위험이 있음
- 남은 좌석이 적은 상황에서 여러 결제가 동시에 확정되면 `confirmCount` 가 초과 증가할 수 있음
- 외부 PG 장애가 길어지면 결제/환불 요청이 PortOne 호출에 계속 묶여 서비스 내부 자원까지 잠식할 수 있음

### 설계 결정

결제 준비와 최종 확정을 하나의 흐름으로 묶지 않고, 다음과 같이 단계와 충돌 축을 분리했습니다.

- **결제 준비 단계**
  - `orderId lock` 으로 같은 주문의 중복 준비를 직렬화
  - 기존 `PENDING payment` 가 있으면 새 `pgKey` 를 만들지 않고 재사용
- **웹훅 검증 단계**
  - PortOne 서명 검증
  - `webhook-id` 저장으로 중복 웹훅 멱등 처리
- **Paid 웹훅 최종 확정 단계**
  - `pgKey lock` 으로 같은 결제건 중복 상태 전이 방지
  - `courseId lock` 으로 좌석 확정 충돌 방지
  - `Payment FOR UPDATE` 로 payment row 비관적 잠금
  - 재검증 통과 후에만 `payment / order / confirmCount / Redis occupancy` 확정
- **외부 PG 보호**
  - `ResilientPaymentGateway` 에서 PortOne 조회/취소 호출을 Circuit Breaker로 감싸 장애 전파 차단

### 왜 이렇게 설계했는가

하나의 큰 락으로 결제 전체를 막으면 서로 관계없는 요청까지 모두 직렬화되어 처리량이 급격히 떨어집니다.  
반대로 DB 락만으로 해결하면 멀티 인스턴스 환경의 외부 이벤트 중복이나 결제 준비 중복을 충분히 제어하기 어렵습니다.

그래서 결제 도메인의 충돌을

- **주문 기준 (`orderId`)**
- **결제건 기준 (`pgKey`)**
- **좌석 기준 (`courseId`)**

세 축으로 나눠 제어하는 구조를 선택했습니다.

### 기대 효과

- 같은 주문에 대한 `PENDING payment` 중복 생성 방지
- 같은 결제건에 대한 중복 웹훅 처리 방지
- 남은 좌석이 적은 상황에서도 초과 확정 방지
- 외부 PortOne 장애가 내부 결제/환불 경로 전체로 확산되는 상황 완화

</details>

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

<details>
<summary><b>운영 환경 Fargate 채택 — ADR (Architecture Decision Record)</b></summary>

### 1. 결정 (Decision)

> **운영(Prod) 환경의 컴퓨트 플랫폼으로 AWS ECS Fargate를 채택한다.**
>
> - 클러스터: `potential-prod-ecs-cluster` (Container Insights 활성)
> - Launch Type: Fargate (Serverless)
> - 배포 전략: Rolling Update + Circuit Breaker + Auto Rollback

---

### 2. 배경 (Context)

졸업 프로젝트의 운영 환경을 구축하면서 다음 제약 조건이 있었습니다.

**비즈니스 / 운영 제약**
- **인프라 관리 인원**: 본인 1인 — 다른 개발 작업과 병행해야 해서 인프라 관리에 많은 시간을 쓸 수 없음
- **운영 기간**: 프로젝트 발표 전후 단기간 (약 2주) — 장기 운영 인력 X
- **장애 대응**: 24/7 대응 불가능 — 자동 복구 메커니즘 필수

**기술적 제약**
- **Spring Boot 4.0.5 + Spring AI + pgvector** — 콜드 스타트 ~80초의 무거운 의존성
- **ADOT 사이드카 패턴** 으로 메트릭 수집 필요 (AMP에 remote_write)
- 무중단 배포 + 자동 롤백 + Health Check 통합 필수
- DB 자격증명을 **Secrets Manager → 환경변수 자동 주입** 패턴으로 안전하게 운영

**비용 제약**
- 프로젝트 크레딧 한정 (~$140)
- 발표 종료 직후 즉시 비용 정지 가능해야 함
- 트래픽이 거의 없는 데모 환경 — Multi-AZ HA 보다 비용 효율 우선

---

### 3. 고려한 옵션 (Options Considered)

총 6가지 옵션을 검토:

| 옵션 | 설명 |
| :--- | :--- |
| **A. EC2 + Docker** (dev 방식) | EC2 인스턴스 위에 Docker로 컨테이너 실행 |
| **B. ECS on EC2** | ECS가 컨테이너 오케스트레이션, 호스트는 우리가 관리하는 EC2 |
| **C. ECS Fargate** | ECS + AWS가 microVM 호스트 자동 관리 |
| **D. EKS** | Kubernetes 클러스터 (AWS 관리형) |
| **E. AWS Lambda** | 서버리스 함수 (이벤트 기반) |
| **F. AWS App Runner** | Fully Managed Container Service |

---

### 4. 비교 (Comparison)

#### 4-1. 다축 비교 표

| 옵션 | 운영 부담 | 시간당 비용 | 사이드카 패턴 | 무중단 배포 | 학습 곡선 | 보안 격리 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| A. EC2 + Docker | 🔴 높음 (OS 패치 / 디스크) | 🟢 낮음 | △ (compose 가능) | 🔴 직접 구현 | 🟢 낮음 | 🟡 호스트 공유 |
| B. ECS on EC2 | 🟡 중간 (EC2 풀 관리) | 🟢 낮음 | ✅ | ✅ | 🟡 중간 | 🟡 호스트 공유 |
| **C. ECS Fargate** | 🟢 **거의 없음** | 🟡 중간 | ✅ | ✅ | 🟡 중간 | 🟢 **microVM 격리** |
| D. EKS | 🔴 높음 (k8s 운영) | 🔴 높음 (CP 월 $73) | ✅ | ✅ | 🔴 높음 | 🟢 강함 |
| E. Lambda | 🟢 없음 | 🟢 호출당 | ❌ | ✅ (자동) | 🟡 중간 | 🟢 강함 |
| F. App Runner | 🟢 거의 없음 | 🟡 중간 | ❌ | ✅ | 🟢 낮음 | 🟢 강함 |

#### 4-2. 핵심 요구사항 vs 옵션별 매칭

| 요구사항 | A: EC2 | B: ECS/EC2 | **C: Fargate** | D: EKS | E: Lambda | F: App Runner |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| 운영 인력 최소화 (1인) | ❌ | △ | **✅** | ❌ | ✅ | ✅ |
| 사이드카 (ADOT) 지원 | △ | ✅ | **✅** | ✅ | ❌ | ❌ |
| Spring Boot 콜드 스타트 80초 수용 | ✅ | ✅ | **✅** | ✅ | ❌ (15분 제한 별개로 부적합) | ✅ |
| 디스크 누적 사고 방지 | ❌ | ❌ | **✅** | ✅ | ✅ | ✅ |
| 비용 즉시 정지 (단기 운영) | △ | △ | **✅ (초 단위)** | ❌ | ✅ | △ |
| 학습 곡선 (졸업 프로젝트) | ✅ | △ | △ | ❌ | △ | ✅ |
| **합계** | 2 / 6 | 3.5 / 6 | **6 / 6** | 3.5 / 6 | 4 / 6 | 4 / 6 |

→ **C. ECS Fargate** 가 6개 요구사항 모두 충족.

---

### 5. 선택 이유 (Why Fargate)

#### 이유 1 — 서버 관리 부담 거의 0
- OS 패치 / 보안 업데이트 / 커널 버전 → AWS 책임
- Auto Scaling Group / AMI / 인스턴스 타입 선택 불필요
- 우리가 정의한 건 **"1 vCPU / 3 GB 짜리 컨테이너 1개"** 뿐
- → **dev에서 겪었던 "디스크 풀로 배포 실패" 사고가 구조적으로 불가능**

#### 이유 2 — 사이드카 패턴 네이티브 지원
- 같은 Task 안에 Spring Boot + ADOT 사이드카 동거
- localhost 통신 → 네트워크 비용 0, scrape latency 0
- EKS / Beanstalk / Lambda / App Runner 에서 하려면 별도 작업
- → **모니터링 인프라를 별도로 띄우지 않고 한 Task로 해결**

#### 이유 3 — Firecracker microVM 보안 격리
- 각 Task가 자체 microVM 위에서 동작
- 호스트 OS 공유 X → 컨테이너 탈출 / 권한 상승 위험 ↓
- ENI도 Task 단위 → SG 격리 강력
- → **EC2 + Docker 대비 보안 한 단계 위**

#### 이유 4 — 운영 안전망 4중 기본 제공
- **Health Check Grace Period** 300초 → Spring 콜드 스타트 흡수
- **Rolling Deployment** (min 100% / max 200%) → 무중단
- **Circuit Breaker** → N회 실패 시 자동 차단
- **Auto Rollback** → 실패 시 이전 리비전 즉시 복귀
- → EC2 + Docker 로 직접 구현하려면 코드 + 인프라 작업 막대

#### 이유 5 — 초 단위 과금 + 즉시 비용 정지
- 시간 단위 X, **초 단위** 과금
- `--desired-count 0` 한 줄로 **즉시 비용 정지**
- EC2 처럼 stopped 인스턴스 EBS / EIP 잔존 비용 X
- → 졸업 발표 후 비용 통제에 최적

---

### 6. 받아들인 트레이드오프 (Accepted Trade-offs)

#### 트레이드오프 1 — 시간당 비용은 EC2보다 30~50% 비쌈
- 같은 1 vCPU / 3 GB 라면 EC2가 30~50% 저렴
- **수용 근거:** 운영 인력 인건비 / 사고 대응 시간을 고려하면 토탈 비용은 오히려 ↓
- → 단순 인프라 비용보다 "관리 부담 → 0" 이 더 가치 있다고 판단

#### 트레이드오프 2 — 콜드 스타트 시간 발생
- 새 Task 시작 시 microVM 프로비저닝 + 이미지 pull + Spring 부팅 = 합산 ~3분
- **수용 근거:** Grace Period 300초로 자연스럽게 흡수
- → 향후 개선 여지: Spring Boot AOT 컴파일로 콜드 스타트 80s → 20s 단축 가능

#### 트레이드오프 3 — 영구 디스크 마운트 어려움
- Task 종료 시 ephemeral 디스크 통째 소멸
- **수용 근거:** 본 앱은 **stateless** — 영구 데이터는 RDS, 파일은 S3로 분리되어 있음
- → 영구 저장이 필요해지면 EFS 마운트 옵션 존재하지만 현재로선 불필요

#### 트레이드오프 4 — Multi-AZ 분산 안 함 (단일 Task)
- 실제로 ECS Service `desiredCount = 1` 운영
- AZ 1개에서만 Task 동작 → 그 AZ 죽으면 일시 중단
- **수용 근거:** 졸업 데모 트래픽 수준에선 진짜 HA 불필요. 비용 절감 우선
- → 향후 사용자 늘면 `desiredCount = 2`로 분산 가능 (Fargate라 1줄 변경)

#### 트레이드오프 5 — EKS의 풍부한 생태계 포기
- Kubernetes Operator / Helm Chart / Service Mesh 등 ecosystem 못 누림
- **수용 근거:**
  - 졸업 프로젝트 규모에선 오버킬
  - EKS 컨트롤 플레인 고정비 월 $73 부담
  - **Kubernetes 기술 스택 학습 곡선** 도 본인 1인 운영 환경에서 부담
- → 사용자 수 늘고 마이크로서비스 분리 필요해지면 EKS 마이그레이션 가능

---

> 💡 핵심 한 줄: **"인프라 관리 인원 1인 + 단기 운영 + 사이드카 필요"** 라는 제약 아래에서, 운영 부담을 거의 0으로 만들면서 운영 안전망(Grace Period · Rolling · Circuit Breaker · Auto Rollback)을 기본 제공하는 ECS Fargate가 6개 요구사항을 모두 충족하는 유일한 선택지였습니다.

</details>

## 🐛 트러블 슈팅

<details>
<summary><b>Disk Full로 인한 dev 배포 실패 — 자동 prune + Fargate 이펨럴 디스크</b></summary>

### 문제 상황

dev 환경 배포 중 EC2 디스크가 가득 차 새 이미지 pull 단계에서 실패하는 사고가 발생했습니다. 옛 컨테이너는 이미 stop·rm 처리된 뒤라 새 컨테이너도 못 뜨고, 결과적으로 **앱 자체가 다운**되는 상태로 이어졌습니다.

**증상**
- 배포 로그: `failed to register layer: write /app/app.jar: no space left on device`
- 기존 컨테이너 종료 후 새 컨테이너 기동 실패 → 사용자 입장에서 사이트 다운

**원인 분석**
- EC2 (`t4g.small`)의 EBS 디스크 = 20 GB
- 매 배포마다 새 이미지(~524 MB) 만 받고 **옛 이미지는 명시적으로 지우지 않음**
- 누적 결과: `potential/dev` 이미지 **63개 = 18.14 GB** (디스크 99% 사용)

### 해결 방법: `scripts/deploy.sh`에 자동 정리 단계 추가 + Fargate 이펨럴 디스크 활용

**dev** 는 배포 스크립트에 `docker image prune` 한 줄을 영구 추가해 누적을 막고, **prod** 는 ECS Fargate의 ephemeral microVM 특성을 활용해 구조적으로 누적이 불가능한 상태로 운영합니다.

**장점:**
- **자동 청소:** 새 컨테이너가 정상 기동된 다음에 prune 실행 → 안전한 시퀀스
- **롤백 안전성:** `until=24h` 필터로 직전 배포 이미지는 보존 → 즉시 롤백 가능
- **실패 격리:** `|| true` 처리로 정리 실패가 배포 실패로 이어지지 않음
- **구조적 해결 (prod):** Fargate microVM은 Task 종료 시 디스크 자체가 통째로 사라져 누적이 발생할 수 없음

### 코드 예시

**즉시 복구 (수동, 사고 발생 시점)**

```bash
docker image prune -af    # 99% → 20% 회복
```

**근본 해결 (`scripts/deploy.sh`, PR #139)**

```bash
CMDS=(
  "aws ecr get-login-password --region ${AWS_REGION} | docker login ..."
  "docker pull ${FULL_URI}"
  "docker stop ${CONTAINER_NAME} || true"
  "docker rm   ${CONTAINER_NAME} || true"
  "docker run -d --name ${CONTAINER_NAME} --restart=always ..."
  "docker image prune -af --filter \"until=24h\" || true"   # 자동 청소
)
```

**Prod (Fargate) — 디스크 누적이 구조적으로 불가능한 흐름**

```text
[배포 1] 새 microVM 프로비저닝 (Firecracker)
  └─ 디스크 새로 생성 → 이미지 pull v1 (520 MB)
  └─ Task 시작

[배포 2 — Rolling]
  새 microVM 추가 프로비저닝 (별개의 머신)
    └─ 디스크 새로 생성 → 이미지 pull v2 (520 MB)
    └─ Task 시작
  [배포 1의 microVM 종료] → 디스크 통째로 destroy → v1도 사라짐

[배포 60] 새 microVM 프로비저닝
  └─ 디스크 새로 생성 → v60만 있음 (520 MB)
  └─ 이전 59개 배포 이미지는 어디에도 없음
```

### 다른 대안과의 비교

| 구분 | **자동 prune (선택)** | Cron 정기 청소 | EBS 디스크 확장 | Fargate 이전 |
| :--- | :--- | :--- | :--- | :--- |
| **작업 범위** | 배포 스크립트 1줄 | 별도 cron job 운영 | 인스턴스 디스크 재설정 | 인프라 재구성 |
| **즉시 효과** | **매 배포마다 자동** | 정해진 시간에만 | 일시적 (재발 가능) | 영구·구조적 해결 |
| **운영 부담** | 0 | 별도 모니터링 필요 | 디스크 비용 증가 | 시간당 비용 증가 |
| **롤백 안전성** | **`until=24h` 필터로 보존** | 필터 별도 설계 필요 | 무관 | Task definition 리비전 단위 |
| **추천 상황** | **dev 환경 표준** | dev 환경 보조 | 일시 응급 처치 | **prod 환경 표준** |

> 💡 dev 는 “자동 prune”, prod 는 Fargate의 이펨럴 디스크를 활용해 **각 환경에 가장 적합한 해결책**을 적용했습니다.

</details>

---

<details>
<summary><b>ECS 배포 실패 — Health Check Grace Period 부족</b></summary>

### 문제 상황

`main` 브랜치 머지 후 운영 배포 단계에서, 새로 띄운 Task가 **ALB Health Check를 통과하기 전에 강제 종료**되는 사고가 반복 발생했습니다. ECS Deployment Circuit Breaker가 "배포 실패" 로 판정해 자동 롤백하면서, 같은 흐름이 반복되어 무한 재시도 루프에 빠진 상태였습니다.

**증상**
- 새 Task가 `RUNNING` 상태 진입 후 짧은 시간 안에 `STOPPED` 처리
- Circuit Breaker → 자동 롤백 → 재시도 → 다시 실패 반복
- CI 단계의 `aws ecs wait services-stable` 가 10분 이상 멈춤

**원인 분석** — 실제 시간 측정 결과

| 단계 | 소요 시간 |
| :--- | :--- |
| Fargate Task placement (microVM 프로비저닝) | ~30 ~ 60s |
| ECR 이미지 pull (VPC Endpoint 경유) | ~30s |
| **Spring Boot 4.0.5 콜드 스타트** | **~80s** *(Spring AI + pgvector + JPA)* |
| ALB Health Check 5회 통과 (`interval 30s × 5`) | ~30 ~ 150s |
| **합산** | **약 170 ~ 270s** |

→ 기본 grace period **180s** 로는 빠듯해, Spring Boot 부팅이 끝나기 전에 ALB가 unhealthy 판정 → ECS가 Task 종료.

### 해결 방법: Grace Period 300초 + `wait services-stable` 타임아웃 확장

`aws ecs update-service` 단계에서 `--health-check-grace-period-seconds` 를 **300초** 로 명시하고, CI의 `wait services-stable` 단계 타임아웃을 **25분** 으로 확장했습니다.

**장점:**
- **즉시 적용:** `update-service` 옵션 1개 추가로 끝 → 코드 변경 0, 인프라 추가 0
- **측정 기반의 안전 마진:** 실제 측정값(콜드 스타트 80s + 합산 200s 이상)에 보수적 여유 100s 추가
- **변동성 흡수:** 이미지 캐시 미스 / 일시 네트워크 지연 등에도 견딤
- **운영 표준화:** prod-cd.yml 워크플로우에 고정 → 수동 실수 가능성 0

### 코드 예시

**`.github/workflows/prod-cd.yml`** — 운영 CI에 영구 반영

```yaml
# 12. ECS 서비스 업데이트 (강제 재배포)
- name: ECS 서비스 업데이트
  run: |
    aws ecs update-service \
      --cluster "${ECS_CLUSTER}" \
      --service "${ECS_SERVICE}" \
      --task-definition "${{ steps.register.outputs.TASK_DEF_ARN }}" \
      --health-check-grace-period-seconds 300 \    # ⭐ 콜드 스타트 흡수
      --force-new-deployment

# 13. 배포 안정화 대기 (헬스체크 통과까지)
- name: 배포 안정화 대기
  run: |
    aws ecs wait services-stable \
      --cluster "${ECS_CLUSTER}" \
      --services "${ECS_SERVICE}"
  timeout-minutes: 25                              # ⭐ 첫 배포 여유
```

**배포 시점 시각화**

```text
[T+0s]     ECS update-service 호출
[T+30s]    Fargate microVM 프로비저닝 시작
[T+60s]    ECR 이미지 pull 시작
[T+90s]    컨테이너 실행 → Spring Boot 부팅 시작
[T+170s]   Spring Boot 부팅 완료 → /actuator/health 200 OK 응답 시작
[T+200s]   ALB Health Check 5회 통과 → healthy 판정
[T+200s]   옛 Task DRAINING → 새 Task로 트래픽 전환
─────────────────────────────────────────────────────
 0s ~ 300s : Grace Period — 이 구간 health check 실패는 무시
            (Spring 부팅 안 끝나도 Task 강제 종료 안 함)
```

### 다른 대안과의 비교

| 구분 | **Grace Period 300s (선택)** | Spring Boot AOT 컴파일 | 이미지 슬림화 | Capacity Provider 변경 |
| :--- | :--- | :--- | :--- | :--- |
| **작업 범위** | 명령어 옵션 1개 추가 | Spring + GraalVM 빌드 재구성 | Dockerfile 재작성 | ECS 인프라 변경 |
| **콜드 스타트 단축** | 0 (대신 안전하게 견딤) | **80s → ~20s** | 일부 (이미지 pull만 단축) | 무관 |
| **즉시 적용** | **즉시** | 빌드/호환성 검증 필요 | 빌드 재구성 | 인프라 작업 시간 |
| **위험도** | 낮음 | 라이브러리 호환성 이슈 가능 | 의존성 충돌 가능 | 클러스터 영향 |
| **추천 상황** | **즉시 사고 차단** | 장기 성능 개선 | 부수적 개선 | 트래픽 패턴 변화 시 |

> 💡 단기적으로는 **Grace Period 300초** 로 사고를 즉시 차단하고, 장기적으로는 **Spring Boot AOT 컴파일** 을 검토해 콜드 스타트 자체를 줄이는 방향을 후속 과제로 남겼습니다.

### 교훈

- **콜드 스타트는 추정이 아닌 실제 측정으로** 안전 마진을 잡아야 한다
- Grace Period 는 **Service 레벨 설정** — Task Definition이 아니라 `update-service` 시점에 매번 명시되어야 한다 (워크플로우 자동화로 휘발 방지)
- Circuit Breaker + Auto Rollback 안전망이 작동했기에 사용자 영향 0 — 안전망의 존재 자체가 핵심
- 향후 Spring Boot AOT / 의존성 슬림화로 콜드 스타트를 80s → 20s 로 단축할 여지를 남겨둠

</details>

---

<details>
<summary><b>주문 생성 결과 처리 - Sealed Interface 도입</b></summary>

### 문제 상황

주문 생성 과정에서 재고 점유 결과에 따라 201(Created)과 202(Accepted/Queued)라는 서로 다른 HTTP 상태를 반환해야 합니다. 기존 방식(하나의 Response DTO 사용)으로는 성공 시 필요한 `orderId`와 대기 시 필요한 `queueId`를 모두 포함해야 하므로, 특정 상황에서 필드들이 `null`이 되어 데이터 구조가 모호해지는 문제가 발생했습니다.

### 해결 방법: Sealed Interface 도입

**Sealed Interface**와 **Record**를 사용하여 각 상태에 최적화된 데이터 구조를 정의하고, 컴파일 타임의 타입 안전성을 확보했습니다.

**장점:**
- **제한된 확장성:** `permits` 키워드로 허용된 클래스 외 상속을 금지하여 도메인 결과의 범위를 명확히 규정합니다.
- **컴파일 타임 체크:** `switch` 문에서 모든 하위 클래스를 다뤘는지 컴파일러가 검사하여 `default` 문 없이 안전하게 처리 가능합니다.
- **데이터 분리:** 각 결과 상태에 필요한 데이터만 보유하므로 `null` 필드 없이 명확한 API 응답을 생성합니다.

### 코드 예시

```java
// Sealed Interface 정의 (Java 17+)
public sealed interface OrderResult 
    permits OrderResult.Created, OrderResult.Queued, OrderResult.Failed {

    // Record 정의
    record Created(String orderId, LocalDateTime reservedAt) implements OrderResult {}
    record Queued(String queueId, long estimatedWaitTime) implements OrderResult {}
    record Failed(String reason) implements OrderResult {}
}

@PostMapping("/orders")
public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
    OrderResult result = orderService.createOrder(request);

    // Java 17 패턴 매칭 switch 사용
    return switch (result) {
        case OrderResult.Created created -> ResponseEntity.status(HttpStatus.CREATED).body(created);
        case OrderResult.Queued queued -> ResponseEntity.status(HttpStatus.ACCEPTED).body(queued);
        case OrderResult.Failed failed -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(failed.reason());
    };
}
```

### 다른 대안과의 비교

| 구분 | **Sealed Interface** | 일반 Interface | Enum + 필드 | Result 래퍼 |
| :--- | :--- | :--- | :--- | :--- |
| **타입 안정성** | **매우 높음** | 보통 | 낮음 | 높음 |
| **데이터 구조** | 상태별 최적화 | 상태별 최적화 | 통합 (Null 발생) | 공통 구조 |
| **확장성** | 폐쇄적 (의도적) | 완전 개방 | 폐쇄적 | 보통 |
| **추천 상황** | **도메인 결과가 명확할 때** | 프레임워크/라이브러리 | 간단한 상태 구분 | 공통 유틸리티 |

</details>

---

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


<details>
<summary><b>Redis 캐싱 시 List 역직렬화 이슈</b></summary>

### 문제 상황

`RedisCacheConfig`에서 `RedisSerializer.json()`을 사용하여 캐싱을 처리할 때, `List<MyDto>`와 같은 제네릭 컬렉션을 역직렬화하면 에러가 발생하거나 `List<LinkedHashMap>`으로 복원되어 `ClassCastException`이 발생하는 문제가 있었습니다.

### 원인 분석

1. **타입 소거 (Type Erasure)**: 자바 제네릭은 런타임에 타입 정보가 제거됩니다. `List<ReviewResponse>`는 런타임에 단순히 `List`로 보여, 역직렬화 시점에 Jackson이 내부 원소가 무엇인지 판단하지 못하고 기본값인 `LinkedHashMap`으로 변환합니다.
2. **POJO vs Collection**: `PageResponse<MyDto>`와 같은 명확한 POJO 클래스는 필드 구조가 명시되어 있어 복원이 쉽지만, `List`와 같은 루트 레벨의 제네릭 컬렉션은 역직렬화 대상 타입이 불분명합니다.

### 해결 방법 및 권장 사항

1. **래퍼(Wrapper) DTO 사용 권장**: 단독 `List<MyDto>`를 캐싱하기보다, 명확한 구조를 가진 래퍼 클래스나 `PageResponse<MyDto>` 형식을 캐싱하는 것이 안전합니다.
2. **역직렬화 타입 명시**: 꼭 `List`를 단독으로 사용해야 한다면, 역직렬화 시점에 `TypeReference` 등을 통해 타입을 명시하거나 `ObjectMapper` 설정에서 클래스 정보를 JSON에 포함(`activateDefaultTyping`)시켜야 합니다.
3. **결론**: 프로젝트 전반에서는 정합성과 안정성을 위해 **래퍼 DTO 캐싱**을 기본 원칙으로 채택했습니다.

</details>


---

<details>
<summary><b>중복 결제 준비 요청 - 같은 주문에 PENDING payment가 여러 개 생기는 문제</b></summary>

### 문제 상황

사용자가 결제 버튼을 빠르게 두 번 누르거나, 네트워크 지연으로 같은 요청이 재전송되면 같은 주문에 대해 여러 개의 `PENDING payment` 가 생길 수 있었습니다.  
이 경우 서로 다른 `pgKey` 가 하나의 주문에 매핑되어 이후 웹훅 처리와 결제 확정 흐름이 복잡해집니다.

### 원인 분석

결제 준비는 PortOne 결제창을 열기 전에 서버가 `pgKey` 와 `payment` row를 먼저 생성하는 구조입니다.  
따라서 "같은 주문에 대한 중복 준비 요청"을 먼저 막지 않으면, 클라이언트 중복 클릭만으로 내부 상태가 여러 번 만들어질 수 있었습니다.

### 해결 방법

- `orderId lock` 으로 같은 주문의 결제 준비를 직렬화
- 같은 주문에 기존 `PENDING payment` 가 있으면 새로 만들지 않고 기존 `pgKey` 를 그대로 반환
- 이미 `PAID` 상태라면 즉시 예외 처리해 중복 결제 진입 자체를 차단

### 결과

- 같은 주문에 대한 중복 결제 준비 요청을 하나의 흐름으로 묶을 수 있게 됨
- `PENDING payment` 가 불필요하게 여러 개 생기는 문제를 방지
- 이후 웹훅과 결제 확정 흐름의 기준 키를 안정적으로 유지

</details>

---

<details>
<summary><b>외부 PG 취소 성공 후 DB 실패 - 부분 환불 정합성 분리</b></summary>

### 문제 상황

부분 환불은 PortOne 취소 성공 이후 내부 DB 상태까지 함께 맞아야 완결됩니다.  
하지만 외부 PG 취소와 내부 DB 갱신은 하나의 트랜잭션으로 묶을 수 없어서, "외부는 취소됐는데 내부는 실패한 상태"가 발생할 수 있었습니다.

### 원인 분석

부분 환불에는 다음 상태가 동시에 맞아야 했습니다.

- `payment` 상태 (`PAID` / `PART_REFUNDED` / `REFUNDED`)
- `order` 수량
- `refund` 이력
- `course.confirmCount`
- Redis 좌석 상태

이 중 PortOne 취소는 외부 시스템 호출이므로 DB 트랜잭션 밖에서 처리해야 했고,  
결과적으로 "외부 성공 후 내부 실패" 시나리오를 별도로 설계할 필요가 있었습니다.

### 해결 방법

- `prepareRefund -> PortOne 취소 -> completeRefund` 3단계로 분리
- 바깥에서 `pgKey lock`, `course lock`을 잡고 안쪽에서 `Payment FOR UPDATE` 적용
- PortOne 취소 실패는 `createFailed(REQUIRES_NEW)` 로 별도 이력 저장
- 성공한 경우에만 후속 트랜잭션에서 `payment / order / refund / course` 를 확정

### 결과

- 환불 가능 여부 검증과 실제 취소 요청을 분리해 외부 실패가 내부 상태를 오염시키지 않도록 제어
- 실패 이력을 별도로 남겨 운영자가 사후 추적할 수 있게 개선
- 부분 환불의 정합성과 복구 가능성을 함께 확보

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
<summary><b>주문하기 API 성능 테스트 결과 (k6-order-complete)</b></summary>

주문하기 API의 성능 병목 현상을 해결하기 위해 Redis Lua Script를 도입하였으며, k6를 활용해 개선 전후의 성능을 측정 및 비교했습니다.

### 테스트 환경
- **테스트 도구**: k6
- **테스트 대상**: 주문하기(Order) API
- **주요 변경 사항**: 재고 확인 및 차감 로직을 다중 Redis 명령에서 단일 Lua 스크립트로 전환

### 성능 비교 결과 요약

| 지표 (Metric) | 개선 전 (Before) | 개선 후 (After) | 개선 결과 |
| :--- | :--- | :--- | :--- |
| **평균 응답 시간 (Avg)** | 5,720 ms | 484.3 ms | 약 91.5% 단축 |
| **95% 응답 시간 (P95)** | 29,990 ms | 1,470 ms | 약 95.1% 단축 |
| **HTTP 요청 실패율** | 65.59% | 5.07% | 60.52%p 감소 |
| **주문 성공 횟수** | 0 건 | 20 건 | 기능 정상화 |
| **주문 에러 횟수** | 1,328 건 | 6 건 | 99.5% 감소 |
| **큐 가득 참 (Full)** | 3,242 건 | 177 건 | 자원 효율성 증가 |

### 주요 개선 사항 분석

- **서비스 정상화 및 안정성 확보**
  - **개선 전**: 65% 이상의 높은 실패율과 함께 주문 성공 건수가 0건으로, 정상적인 서비스 이용이 불가능했습니다.
  - **개선 후**: 실패율이 5% 대로 급감하였으며, 주문 로직이 정상적으로 작동하여 성공 케이스가 안정적으로 발생함을 확인했습니다.

- **응답 속도의 획기적인 개선 (Latency)**
  - 평균 응답 시간을 **12배 이상 (5.7s → 0.48s)** 단축했습니다.
  - 특히 극단적인 지표인 P95(상위 5% 응답 시간)가 30초에서 1.4초로 줄어들어, 부하 상황에서도 사용자가 체감하는 지연 시간이 대폭 개선되었습니다.

- **원자성(Atomicity) 보장을 통한 데이터 정합성 해결**
  - 기존에는 '재고 조회'와 '재고 차감' 사이의 간극에서 레이스 컨디션(Race Condition)이 발생했으나, Lua Script를 통해 이를 하나의 원자적 연산으로 처리함으로써 데이터 정합성 문제를 해결했습니다.

- **네트워크 오버헤드 최적화**
  - 애플리케이션과 Redis 서버 간의 다중 왕복 통신(Round Trip)을 단일 통신으로 결합하여 불필요한 네트워크 비용을 제거했습니다.

### 결론
Redis Lua Script 도입을 통해 주문 시스템의 처리량(Throughput)을 높이고 응답 지연(Latency)을 대폭 개선했습니다. 특히 대규모 트래픽 상황에서 발생하던 시스템 마비 현상을 해결하고 안정적인 주문 환경을 구축했습니다.

</details>

---

<details>
<summary><b>주문 조회 API 성능 테스트 결과 (k6-order-read)</b></summary>

주문 도메인의 읽기 성능(내 주문 목록 조회 및 상세 조회)을 검증하기 위해 k6를 활용한 부하 테스트를 수행했습니다.

### 테스트 환경
- **테스트 도구**: k6
- **테스트 대상**: 
  - 내 주문 목록 조회 (`GET /v1/orders/me`)
  - 주문 상세 조회 (`GET /v1/orders/{orderId}`)
- **부하 프로필**: 30 ~ 45 RPS (Ramping Arrival Rate)

### 성능 목표 (SLO)

| 구분 | API | 목표 (p95) |
| :--- | :--- | :--- |
| **목록 조회** | `GET /v1/orders/me` | < 500 ms |
| **상세 조회** | `GET /v1/orders/{orderId}` | < 200 ms |
| **공통** | 에러율 | < 1.0% |

### 주요 검증 포인트
- **인덱스 최적화**: `member_id`와 `created_at` 복합 인덱스가 페이징 조회 시 적절히 활용되는지 검증합니다.
- **조인 성능**: 주문 상세 조회 시 연관된 엔티티(코스 등)를 조회할 때 불필요한 추가 쿼리 없이 효율적으로 데이터를 가져오는지 확인합니다.
- **부하 안정성**: 목표 RPS(최대 45) 도달 시에도 시스템이 안정적으로 응답을 유지하는지 측정합니다.

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

<details>
<summary><b>결제 도메인 성능 테스트 및 개선 (k6 + local/perf)</b></summary>

### 테스트 목적

결제 도메인은 단일 API보다 **결제 준비 → Paid 웹훅 → 학생 환불** 흐름 전체에서 병목이 더 잘 드러났습니다.  
그래서 기능 단독이 아니라 실제 사용자 흐름을 하나의 시나리오로 묶어 성능을 측정했습니다.

- `POST /v1/payments` 결제 준비/조회 시나리오
- `POST /v1/webhooks/portone` Paid 웹훅 확정 시나리오
- `PATCH /v1/orders/{orderId}/cancel` 학생 주문 취소 환불 시나리오

외부 PG 지연이 결과를 흐리지 않도록 `local,perf` 프로필과 `PaymentGateway stub` 기반으로 내부 병목만 분리 측정했습니다.

### 주요 개선 사항

- **인덱스 추가**
    - `payments(member_id, created_at)`
    - `payments(member_id, status, created_at)`
    - `refunds(payment_id, status)`
    - `webhooks(pg_key, event/status/received_at)` 복합 인덱스
- **쿼리 개선**
    - 결제 목록 `count` 쿼리에서 불필요한 `orders JOIN` 제거
    - `refund-preview` 조회를 `payment / order / course` 3회 조회에서 projection 1회 조회로 축소
- **락 경합 구간 축소**
    - `course lock` 안에서는 좌석 재확인과 `confirmCount` 증가만 수행
    - 검증 로직은 lock 밖으로 이동
    - `seatsConfirmed = false` 경로에서 `paymentService.fail()` 이 중복 호출되던 버그 수정

### 성능 비교 결과 요약

| 항목 | v1 (Baseline) | v6 (최종) | 해석 |
| --- | ---: | ---: | --- |
| 결제 준비/조회 business success | 98.91% | **99.48%** | 목표치에 가깝게 안정화 |
| 학생 환불 business success | 35.18% | **42.85%** | 환불 시나리오 성공률 개선 |
| 학생 환불 http_req_failed | 27.30% | **15.38%** | 실패 요청 감소 |
| Paid 웹훅 http_req_failed | 14.43% | **0.00%** | 웹훅 요청 실패 제거 |
| Paid 웹훅 interrupted_iterations | 173건 | **0건** | 부하 상황에서도 실행 안정성 회복 |

### 결과 해석

이번 테스트는 기능 단독이 아니라 **사용자 흐름 전체**를 하나의 시나리오로 측정했기 때문에, 인덱스나 쿼리 개선이 모든 API의 p95를 일괄적으로 낮추는 형태로 바로 보이지는 않았습니다.

하지만 최종 버전에서는

- 웹훅 실패율 0%
- interrupted iterations 0건
- 학생 환불 성공률 개선

으로 이어지면서, "가장 빠른 한 요청"보다 **혼잡한 상황에서도 끝까지 처리되는 비율**을 높인 개선으로 판단했습니다.

</details>

---

<details>
<summary><b>결제/환불 목록 조회 성능 개선 : 캐싱 + 인덱스 + 페이지네이션</b></summary>

### 테스트 목적

결제/환불 목록 조회는 사용자 마이페이지에서 반복 호출되는 **read-heavy 경로**입니다.  
특히 `memberId + status` 조건과 `created_at` 정렬이 함께 걸리기 때문에, 목록 데이터 조회뿐 아니라 count 쿼리 비용과 재조회 부하가 누적되기 쉬웠습니다.

그래서 write 경로 성능과 분리해서 아래 목록 조회 시나리오를 별도로 측정했습니다.

- `GET /v1/payments` 결제 목록 조회 시나리오
- `GET /v1/refunds` 환불 목록 조회 시나리오

### 주요 개선 사항

- **캐싱 적용**
    - 결제 목록: `PAYMENT_LIST_CACHE`
    - 환불 목록: `REFUND_LIST_CACHE`
    - `memberId + status + page + size + sort` 기준으로 캐시 키를 분리해 동일 조건 재조회 비용 절감
- **인덱스 추가**
    - `payments(member_id, created_at)`
    - `payments(member_id, status, created_at)`
    - `refunds(payment_id, status)`
- **쿼리 개선**
    - 결제 목록 count 쿼리에서 불필요한 `orders JOIN` 제거
    - 페이지네이션 count 비용과 정렬 비용을 줄여 목록 조회 응답 안정화

### 성능 측정 기준

- `k6-payment-list-read-load.js`
- `k6-refund-list-read-load.js`
- 목표치
    - `business success > 99%`
    - `http_req_duration p(95) < 400ms`
    - `p(99) < 700ms`

### 확인된 개선 결과

환불 목록 조회는 동일한 사용자 흐름 기준 측정에서 아래와 같은 개선을 확인했습니다.

| 항목 | 개선 전 | 개선 후 | 해석 |
| --- | ---: | ---: | --- |
| `refund_list_read_business_success` | 100% | **100%** | 안정성 유지 |
| `http_req_failed` | 0.00% | **0.00%** | 요청 실패 없음 |
| `refund_list_read_ms p(95)` | 7.33s | **4.06s** | 목록 조회 체감 지연 감소 |
| `http_req_duration p(95)` | 7.10s | **4.48s** | 전체 응답 시간 개선 |

결제 목록 조회도 동일한 캐시/인덱스/페이지네이션 전략으로 분리 측정할 수 있도록 시나리오를 구성했고, 결제/환불 목록 경로 모두 **무조건 DB 재조회하던 구조에서 캐시 우선 + 인덱스 보조 구조**로 전환했습니다.

### 결과 해석

이번 개선은 "한 쿼리를 빠르게 만든 것"보다,  
반복적으로 열리는 사용자 목록 화면에서 **같은 조건 재조회 비용을 줄이고 DB read 부하를 분산**하는 데 의미가 있었습니다.

특히 환불 목록 조회는 여전히 목표치(p95 < 400ms)에는 못 미쳤지만,

- 실패율 0% 유지
- business success 100% 유지
- p95 구간 유의미한 감소

를 통해 read path 개선 방향이 맞음을 확인했습니다.

</details>


---

## 🗂 ERD
> ![Portential v14.png](docs/images/Portential%20v14.png)
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
