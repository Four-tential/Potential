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

# ===== Docker 이미지 빌드 =====
# Dockerfile의 멀티스테이지 빌드가 내부에서 Gradle 빌드까지 수행함
echo "[ECR] Build ${ECR_REPO}:${IMAGE_TAG}"
docker build -t "${ECR_REPO}:${IMAGE_TAG}" .

# ===== ECR 경로로 태그 추가 =====
# docker build 시 로컬 이름으로 빌드했으므로 ECR 전체 경로로 태그를 달아줌
echo "[ECR] Tag -> ${FULL_URI}"
docker tag "${ECR_REPO}:${IMAGE_TAG}" "${FULL_URI}"

# ===== ECR에 push =====
echo "[ECR] Push -> ${FULL_URI}"
docker push "${FULL_URI}"

# ===== 다음 단계(deploy.sh)로 이미지 전체 경로 전달 =====
# GitHub Actions에서 스텝 간 값을 넘길 때는 GITHUB_OUTPUT 파일에 기록
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "FULL_URI=${FULL_URI}" >> "${GITHUB_OUTPUT}"
else
  # 로컬에서 직접 실행할 때는 stdout으로 출력
  echo "FULL_URI=${FULL_URI}"
fi
