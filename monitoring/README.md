# 📊 Monitoring & Load Testing Guide

이 프로젝트는 시스템의 성능 분석과 안정성 검증을 위해 **k6, Prometheus, Grafana** 기반의 실시간 모니터링 환경을 제공합니다.

---

## 🏗️ 시스템 구성 (Tech Stack)

*   **Spring Boot Actuator**: 애플리케이션 메트릭 노출 (`/actuator/prometheus`)
*   **Prometheus**: 메트릭 수집(Scrape) 및 k6 지표 수신(Remote Write) 시계열 데이터베이스
*   **Grafana**: 수집된 데이터를 시각화하는 대시보드 도구
*   **k6**: 시나리오 기반 부하 테스트 도구

---

## 🚀 빠른 시작 (Quick Start)

### 1. 인프라 실행
모든 모니터링 서비스는 Docker Compose로 통합 관리됩니다.
```bash
docker-compose up -d
```
*   **Prometheus**: [http://localhost:9090](http://localhost:9090)
*   **Grafana**: [http://localhost:3000](http://localhost:3000) (초기 ID/PW: `admin` / `admin`)

### 2. 애플리케이션 실행
스프링 부트 애플리케이션을 실행합니다. (`8080` 포트)
*   `JwtFilter`에서 `/actuator/**` 경로는 인증 제외 처리되어 있습니다.
*   `application.yml`에 설정된 P95, P99 분위수 데이터가 실시간으로 수집됩니다.

### 3. k6 부하 테스트 수행
`docker-compose` 서비스를 활용하여 별도의 설치 없이 테스트를 수행할 수 있습니다.

#### **기본 테스트 (k6-test.js)**
```bash
docker-compose run --rm k6
```

#### **커스텀 스크립트 실행**
`monitoring/` 폴더에 새로운 `.js` 파일을 만든 후 아래 명령어로 실행하세요.
```bash
docker-compose run --rm k6 run /scripts/your-script-name.js
```

---

## 📈 Grafana 설정 및 활용

### 데이터 소스 (Data Source)
`monitoring/grafana-datasource.yml`을 통해 **Prometheus**가 자동으로 등록됩니다.

### 추천 대시보드 (Dashboard Import)
다음 ID를 임포트하여 즉시 모니터링을 시작하세요.
1.  **ID: 11378** (Spring Boot Statistics) - **추천!** RPS, Latency, Error Rate 한눈에 확인 가능
2.  **ID: 4701** (JVM Micrometer) - 상세한 JVM 메모리, GC, 쓰레드 상태 분석용

### ⚠️ `${DS_PROMETHEUS}` 에러 해결법
대시보드 임포트 후 데이터가 나오지 않을 경우:
1.  대시보드 상단 `Settings (톱니바퀴) -> Variables` 접속
2.  `DS_PROMETHEUS` 변수 클릭
3.  **Type**: `Data source`, **Instance name filter**: `Prometheus` 선택 후 **Apply**

---

## 🔍 주요 모니터링 지표 (Key Metrics)

*   **RPS (Throughput)**: 초당 처리량 (`http_server_requests_seconds_count`)
*   **Latency (P95, P99)**: 상위 5%, 1% 사용자 체감 응답 속도
*   **Error Rate**: 전체 요청 대비 실패율 (4xx, 5xx 에러 감시)
*   **JVM Memory**: 힙 메모리 사용량 및 가비지 컬렉션(GC) 빈도

---

## 💾 데이터 보존 (Persistence)
*   **대시보드 설정**: `./grafana-data` 폴더에 영구 저장됩니다.
*   **메트릭 기록**: `./prometheus-data` 폴더에 영구 저장됩니다.
*   **DB/Redis 데이터**: 프로젝트 루트의 각 `-data` 폴더에 저장됩니다.
