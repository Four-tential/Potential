# Potential 운영 환경 데모/발표 매뉴얼

> 최종 갱신: 2026-05-13
> 대상: 발표자 본인 + 팀원 + 시연자
> 목적: 운영 환경(prod)·모니터링(AMP+Grafana)·CI/CD 흐름을 짧은 시간 안에 시연·인수인계할 수 있도록 정리

---

## 0. 한눈에 보기

| 항목 | 값 |
| --- | --- |
| AWS 계정 | `154723391938` (ap-northeast-2 / 서울) |
| 운영 도메인 | https://www.potential-fourtential.shop |
| ECS 클러스터 | `potential-prod-ecs-cluster` |
| ECS 서비스 | `potential-prod-ecs-service` (Fargate 1 task, 1 vCPU / 3GB) |
| Task Definition | `potential-prod-ecs-task-definition:9` (앱 + ADOT sidecar) |
| ALB | `potential-prod-alb` (HTTPS 443 → 8080) |
| RDS MySQL | `prod-potential-rds-mysql-1` |
| RDS PostgreSQL + pgvector | `prod-potential-rds-postgre-1` |
| ElastiCache Redis | `potential-prod-redis` (TLS) |
| ECR 리포지토리 | `potential/prod` |
| S3 이미지 버킷 | `potential-prod-images` (CloudFront 배포) |
| AMP 워크스페이스 | `ws-0e10d9e2-4927-41b1-a32e-0352ae1ea7b6` |
| Grafana 호스트 | EC2 `i-00fbb5ea475c7600b` (Private, SSM 전용) |
| 비용 알람 | Budget `Potential-Demo-Credit-Guard` ($50, 50/80/100% 임계치 → 이메일) |

> 운영 시크릿(DB/JWT/OAuth/PortOne)은 **AWS Secrets Manager**, 일반 설정(JWT 만료 시간 등)은 **SSM Parameter Store**에서 주입됩니다. `.env`/하드코딩 없음.

---

## 1. 데모 진행 시나리오 (10분 트랙)

1. **사이트 접속** — https://www.potential-fourtential.shop
   - 로그인 / 코스 목록 / 코스 상세 / 결제 흐름 → "프론트는 정적, 백엔드는 ECS Fargate"
2. **CI/CD 시연** — GitHub `main` 브랜치 push → `prod-cd.yml` 워크플로우
   - OIDC 페더레이션으로 IAM Role 발급 → ECR push → ECS task definition register → 서비스 update
   - `--health-check-grace-period-seconds 300`으로 Spring 콜드 스타트 보호
3. **모니터링 대시보드** — Grafana `Potential Prod Overview`
   - HTTP 요청 rate / status / p95 latency
   - JVM heap / threads / CPU / HikariCP
4. **AMP 쿼리** — Grafana Explore에서 `up{job="spring-actuator"}` 등
5. **장애 대비 흐름** — RDS 스냅샷, ALB Listener Rule(/actuator/* 차단), Health Check 동작 설명
6. **마무리** — 비용 알람·정리 계획 공유

---

## 2. Grafana 접근 방법 (SSM Port Forwarding)

Grafana EC2는 Private Subnet에 있고 **22번 포트가 열려 있지 않습니다.** AWS Session Manager의 port forwarding으로 안전하게 접속합니다.

### 사전 준비 (한 번만)

```bash
# AWS CLI v2 + Session Manager Plugin 설치 확인
session-manager-plugin --version

# 자격 증명: 개인 IAM 사용자 (dev.kyjtheyj 등)
aws sts get-caller-identity
```

### 접속 명령

```bash
aws ssm start-session \
  --region ap-northeast-2 \
  --target i-00fbb5ea475c7600b \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3000"],"localPortNumber":["3000"]}'
```

이후 브라우저에서 `http://localhost:3000`:

| 항목 | 값 |
| --- | --- |
| Username | `admin` |
| Password | `PotentialAdmin2026` |
| Datasource | `AMP-Potential` (기본값) |
| Dashboard | "Potential Prod Overview" (`/d/potential-prod`) |

> **운영 운영자만 사용하는 비밀번호**입니다. 데모가 끝난 뒤 EC2와 함께 폐기하거나 변경하세요.

### 자주 쓰는 PromQL

```promql
# 트래픽 (URI별)
sum(rate(http_server_requests_seconds_count[1m])) by (uri)

# 5xx 비율
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))

# p95 응답 시간
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))

# JVM heap 사용량
sum(jvm_memory_used_bytes{area="heap"})

# HikariCP 사용/대기 커넥션
hikaricp_connections_active
hikaricp_connections_pending
```

---

## 3. 모니터링 데이터 흐름

```
Spring Boot (8080/actuator/prometheus)
        ↓ scrape (localhost, 15s)
ADOT Collector (ECS sidecar, same task)
        ↓ remotewrite (SigV4)
AWS Managed Prometheus (AMP) workspace
        ↓ proxy + SigV4 (EC2 IAM Role)
Grafana on EC2 (Private)
        ↓ SSM Port Forward (3000)
운영자 브라우저
```

### 핵심 설정 위치

- **ADOT 설정**: SSM Parameter Store `/config/potential-prod/adot/collector-config`
- **AMP IAM**: ECS Task Role `ECS-role-task-S3` (aps:RemoteWrite)
- **Grafana IAM**: EC2 Instance Profile `Potential-Monitoring-EC2-Profile`
  - 인라인 정책 `AmpQuery`: `aps:QueryMetrics/GetLabels/GetSeries/GetMetricMetadata`
- **Grafana 환경 변수**:
  - `GF_AUTH_SIGV4_AUTH_ENABLED=true` ← **반드시 필요**
  - `GF_AWS_AUTH_PROVIDERS=default`
  - `GF_AWS_ALLOWED_AUTH_PROVIDERS=default`
- **Grafana 데이터소스**: `sigV4AuthType: default` (AWS SDK 기본 자격 증명 체인 → EC2 IAM 자동 사용)

> ⚠️ `sigV4AuthType: ec2_iam_role`을 쓰려면 `GF_AWS_ALLOWED_AUTH_PROVIDERS`에 `ec2_iam_role`을 추가해야 합니다. 본 데모는 `default`로 통일.

---

## 4. ECS 운영 명령 모음

```bash
# 현재 서비스 상태
aws ecs describe-services \
  --cluster potential-prod-ecs-cluster \
  --services potential-prod-ecs-service \
  --region ap-northeast-2 \
  --query 'services[0].{Running:runningCount,Desired:desiredCount,TD:taskDefinition}'

# 현재 동작 중인 태스크 로그 (앱 컨테이너)
aws logs tail /ecs/potential-prod-ecs-task-definition \
  --follow --since 5m --region ap-northeast-2 \
  --log-stream-name-prefix ecs/potential-prod-container

# ADOT 사이드카 로그
aws logs tail /ecs/potential-prod-ecs-task-definition \
  --follow --since 5m --region ap-northeast-2 \
  --log-stream-name-prefix adot

# 수동 재배포 (이미지 동일, 새 태스크 강제 교체)
aws ecs update-service \
  --cluster potential-prod-ecs-cluster \
  --service potential-prod-ecs-service \
  --force-new-deployment --region ap-northeast-2

# 일시 정지 (비용 절약 — 데모 후)
aws ecs update-service \
  --cluster potential-prod-ecs-cluster \
  --service potential-prod-ecs-service \
  --desired-count 0 --region ap-northeast-2
```

---

## 5. CI/CD 흐름

`.github/workflows/prod-cd.yml`

1. `main` push → OIDC로 `Potential-Prod-github-oidc-role` 발급 (환경별 격리)
2. Gradle 캐시 복원 → `./gradlew bootJar`
3. Docker 빌드 → ECR 푸시 (`<ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/potential/prod:<sha>`)
4. `infra/taskdef.json`의 `<IMAGE_URI>`/`<ACCOUNT_ID>` 치환 → `aws ecs register-task-definition`
5. `aws ecs update-service --health-check-grace-period-seconds 300 --force-new-deployment`
6. `aws ecs wait services-stable` (최대 10분)

> **roll back**: 콘솔에서 이전 task definition revision으로 서비스 업데이트하면 즉시 롤백.

---

## 6. 비용 알람 (AWS Budgets)

| 항목 | 설정 |
| --- | --- |
| Budget 이름 | `Potential-Demo-Credit-Guard` |
| 한도 | **$50 / 월** |
| 알람 임계치 | 50% (실제 사용), 80% (실제 사용), 100% (예측) |
| 알람 수신 | 이메일 `nananan1213@gmail.com` |

```bash
# 현재 사용액 확인
aws budgets describe-budget \
  --account-id 154723391938 \
  --budget-name "Potential-Demo-Credit-Guard" \
  --query 'Budget.CalculatedSpend.ActualSpend'

# 알람 임계치/구독 확인
aws budgets describe-notifications-for-budget \
  --account-id 154723391938 \
  --budget-name "Potential-Demo-Credit-Guard"
```

> 추가로 계정 전역 `My Monthly Cost Budget` ($100/월) 도 운영 중. 두 알람 모두 작동하면 안심.

---

## 7. 데모 종료 후 정리 가이드

비용 누수를 막기 위해 발표 직후 다음 순서로 정리합니다.

### 7.1 즉시 종료 (수분 내 비용 0 수렴)

```bash
# 1) ECS 서비스 0으로
aws ecs update-service --cluster potential-prod-ecs-cluster \
  --service potential-prod-ecs-service --desired-count 0 \
  --region ap-northeast-2

# 2) Monitoring EC2 종료
aws ec2 stop-instances --instance-ids i-00fbb5ea475c7600b --region ap-northeast-2

# 3) NAT Gateway 삭제 (시간당 과금)
aws ec2 describe-nat-gateways --region ap-northeast-2 \
  --query 'NatGateways[?State==`available`].NatGatewayId'
# 위 결과로 출력된 ID에 대해
# aws ec2 delete-nat-gateway --nat-gateway-id nat-xxxx --region ap-northeast-2
```

### 7.2 다음날 마저 정리

- RDS 스냅샷 저장 후 인스턴스 삭제 (`prod-potential-rds-mysql-1`, `prod-potential-rds-postgre-1`)
- ElastiCache Redis 삭제 (`potential-prod-redis`)
- ALB / Target Group 삭제
- AMP 워크스페이스 삭제 (`aws amp delete-workspace --workspace-id ws-...`)
- EC2 인스턴스 + EIP 릴리스
- VPC Endpoint / NAT EIP / VPC 마지막에 정리
- ECR 이미지 — Lifecycle 정책으로 자동 정리 중 (수동 정리 불필요)

> **삭제 전 점검표**
> - [ ] RDS 스냅샷 생성됨?
> - [ ] S3 이미지 백업 필요 여부 결정
> - [ ] Secrets Manager에 남길 시크릿 결정 (`Mark for deletion`은 7일 후 영구 삭제)

---

## 8. 자주 발생한 이슈 & 해결 (트러블슈팅 메모)

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| ECS 태스크 health check 실패 | Spring 콜드 스타트 + Fargate 배치 시간이 grace period(180s)보다 김 | `--health-check-grace-period-seconds 300` |
| ALB Listener Rule이 /actuator/* 차단해 헬스체크 실패? | 잘못된 의심 — Listener Rule은 ALB **타깃** 헬스체크에 영향 없음 | 정상 동작 (오해였음) |
| Grafana → AMP 403 Forbidden | `GF_AUTH_SIGV4_AUTH_ENABLED` 누락 + `sigV4AuthType=ec2_iam_role`이 allow list 밖 | env 추가 + datasource를 `sigV4AuthType: default`로 |
| Grafana "Failed to get prometheus buildinfo" | AMP가 `/api/v1/status/buildinfo` 미지원 | datasource에 `prometheusType: Prometheus`, `prometheusVersion: 2.40.0` 명시 |
| pgvector 인증 실패 (`docker exec`는 성공) | `.env`의 비번 vs named volume의 SCRAM 해시 불일치 + `pg_hba.conf` trust | volume 초기화 후 비밀번호 재생성 |
| SSM agent 등록 None | EC2 시작 후 인스턴스 프로필 부착 → 부착 전 부팅분이 등록 안 됨 | EC2 재시작 또는 재생성 |

---

## 9. 권한 / 자격 증명 노트 (팀원 인계용)

- **개인 IAM 사용자**: `dev.kyjtheyj`, `dev.<이름>` 등 — Console + CLI 사용
- **GitHub OIDC Role**
  - dev 브랜치: `Potential-github-oidc-role`
  - main 브랜치: `Potential-Prod-github-oidc-role`
- **ECS Task Role**: `ECS-role-task-S3` (S3 + AMP RemoteWrite)
- **ECS Task Execution Role**: `ecsTaskExecutionRole` (ECR pull + Secrets/SSM 읽기)
- **Monitoring EC2 Role**: `Potential-Monitoring-EC2-Role` (SSM + `aps:Query*`)

> 팀원 추가 시: 그룹 정책 추가가 Auto-mode classifier에 막힐 수 있으므로, 개별 사용자에 정책을 attach 하는 방식이 안전.

---

## 10. 관련 문서

- [`docs/ARCHITECTURE.md`](./ARCHITECTURE.md) — 전체 아키텍처 + Mermaid 다이어그램 12종
- [`docs/FLOWS.md`](./FLOWS.md) — 주요 비즈니스 흐름 (로그인/주문/검색)
- [`docs/FEATURES.md`](./FEATURES.md) — 기능 명세
- [`docs/SEARCH_IMPROVEMENT_PLAN.md`](./SEARCH_IMPROVEMENT_PLAN.md) — 검색 개선 로드맵
- ADR (Notion): JWT 회전 / 소셜 로그인 / 챗봇 RAG 재수집

---

## 11. 비상 연락 / 알람 도착시 행동 지침

1. **비용 알람 이메일 ($50의 50% 초과)** 도착 시
   - `aws ce get-cost-and-usage` 로 어떤 서비스가 늘었는지 확인
   - 보통 NAT Gateway / RDS / ECS 순서로 비용 비중 큼
   - 데모 종료 직후라면 7.1 절차로 즉시 종료
2. **ALB Target Unhealthy 알람** (CloudWatch 추가 시)
   - `aws ecs describe-tasks` → stopped reason 확인
   - 보통 OOM / DB 커넥션 실패 / 시크릿 누락
3. **AMP 데이터 끊김**
   - ADOT sidecar 로그 확인 (`/ecs/...` 로그그룹 `adot/` prefix)
   - Task Role의 `aps:RemoteWrite` 권한 점검

---

체크리스트가 더 필요하면 이 문서를 PR로 업데이트하세요.
