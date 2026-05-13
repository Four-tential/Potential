#!/usr/bin/env bash
# Docker 이미지를 빌드하고 AWS ECR에 push하는 스크립트
# GitHub Actions의 dev-cd.yml / prod-cd.yml "Build & Push to ECR" 단계에서 실행됨
set -euo pipefail

# ===== 필수 환경변수 점검 =====
: "${AWS_REGION:?AWS_REGION required}"
: "${ACCOUNT_ID:?ACCOUNT_ID required}"
: "${ECR_REPO:?ECR_REPO required}"
: "${IMAGE_TAG:?IMAGE_TAG required}"

# ===== 플랫폼 (선택, 기본 arm64) =====
# dev EC2(Graviton) → linux/arm64
# prod ECS task (X86_64로 정의됨) → linux/amd64
PLATFORM="${PLATFORM:-linux/arm64}"

# ===== ECR 주소 조합 =====
REG_URI="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
FULL_URI="${REG_URI}/${ECR_REPO}:${IMAGE_TAG}"

# ===== ECR 로그인 =====
echo "[ECR] Login to ${REG_URI}"
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${REG_URI}"

# ===== Buildx 빌더 생성 =====
docker buildx create --name multiarch --use 2>/dev/null || docker buildx use multiarch

# ===== Docker 이미지 빌드 & push =====
echo "[ECR] Buildx & Push ${FULL_URI} (platform=${PLATFORM})"
docker buildx build \
  --platform "${PLATFORM}" \
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
