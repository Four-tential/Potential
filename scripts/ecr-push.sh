#!/usr/bin/env bash
# Docker 이미지를 빌드하고 AWS ECR에 push하는 스크립트
# GitHub Actions dev-cd.yml의 "Build & Push to ECR" 단계에서 실행됨
set -euo pipefail

# ===== 필수 환경변수 점검 =====
# 값이 없으면 즉시 오류를 내고 종료 (조용히 실패하는 것 방지)
: "${AWS_REGION:?AWS_REGION required}"
: "${ACCOUNT_ID:?ACCOUNT_ID required}"
: "${ECR_REPO:?ECR_REPO required}"
: "${IMAGE_TAG:?IMAGE_TAG required}"

# ===== ECR 주소 조합 =====
# ECR 레지스트리 URI: {계정ID}.dkr.ecr.{리전}.amazonaws.com
REG_URI="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
# 최종 이미지 전체 경로: {레지스트리}/{리포}:{커밋SHA}
FULL_URI="${REG_URI}/${ECR_REPO}:${IMAGE_TAG}"

# ===== ECR 로그인 =====
# ECR은 Docker Hub와 달리 임시 토큰으로 인증함 (12시간 유효)
echo "[ECR] Login to ${REG_URI}"
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${REG_URI}"

# ===== Buildx 빌더 생성 =====
# buildx는 QEMU와 조합하여 크로스 플랫폼 빌드를 지원함
# docker-container 드라이버를 사용해야 --platform + --push 조합이 가능
docker buildx create --name multiarch --use 2>/dev/null || docker buildx use multiarch

# ===== Docker 이미지 빌드 & push (buildx) =====
# --platform: EC2(ARM/Graviton)용 이미지 빌드. amd64 추가 시 멀티 아키텍처 지원 가능
# --push: 빌드 완료 후 ECR에 바로 push (별도 tag/push 불필요)
# --provenance=false: ECR 호환성을 위해 provenance 메타데이터 비활성화
echo "[ECR] Buildx & Push ${FULL_URI}"
docker buildx build \
  --platform linux/arm64 \
  --tag "${FULL_URI}" \
  --push \
  --provenance=false \
  .

# ===== 다음 단계(deploy.sh)로 이미지 전체 경로 전달 =====
# GitHub Actions에서 스텝 간 값을 넘길 때는 GITHUB_OUTPUT 파일에 기록
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "FULL_URI=${FULL_URI}" >> "${GITHUB_OUTPUT}"
else
  # 로컬에서 직접 실행할 때는 stdout으로 출력
  echo "FULL_URI=${FULL_URI}"
fi
