#!/usr/bin/env bash
# ECR에 올라간 Docker 이미지를 EC2에 배포하는 스크립트
# SSH 없이 AWS SSM(Systems Manager)을 통해 EC2에 원격 명령을 실행함
# GitHub Actions dev-cd.yml의 "Deploy on EC2 via SSM" 단계에서 실행됨
set -euo pipefail

# ===== 필수 환경변수 점검 =====
: "${AWS_REGION:?AWS_REGION required}"
: "${EC2_INSTANCE_ID:?EC2_INSTANCE_ID required}"
: "${FULL_URI:?FULL_URI required}"           # ecr-push.sh에서 넘겨받은 이미지 전체 경로
: "${CONTAINER_NAME:?CONTAINER_NAME required}"
: "${APP_PORT:?APP_PORT required}"
: "${SPRING_PROFILE:?SPRING_PROFILE required}"

# ===== ECR 주소 파싱 =====
# FULL_URI 예시: 123456789.dkr.ecr.ap-northeast-2.amazonaws.com/potential-dev:abc1234
REG_URI="$(echo "${FULL_URI}" | cut -d/ -f1)"          # 레지스트리 주소만 추출
REPO_AND_TAG="$(echo "${FULL_URI}" | cut -d/ -f2- )"   # 리포지토리:태그 추출
REPO="$(echo "${REPO_AND_TAG}" | rev | cut -d: -f2- | rev)"
TAG="$(echo "${REPO_AND_TAG}"  | awk -F: '{print $NF}')"

# SSM 명령에는 코멘트를 100자 이내로 넣어야 함
COMMENT="Deploy ${REPO}:${TAG}"
if [ ${#COMMENT} -gt 100 ]; then
  COMMENT="${COMMENT:0:100}"
fi

echo "[INFO] FULL_URI=${FULL_URI}"
echo "[INFO] REG_URI=${REG_URI}"
echo "[INFO] EC2_INSTANCE_ID=${EC2_INSTANCE_ID}"
echo "[INFO] COMMENT=${COMMENT}"

# ===== EC2에서 순서대로 실행할 명령 목록 =====
# SSM이 EC2 위에서 이 명령들을 순차적으로 실행함
CMDS=(
  # 1. ECR 로그인 (EC2에서 ECR에 접근하기 위한 임시 인증)
  "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${REG_URI}"
  # 2. 새 이미지 다운로드
  "docker pull ${FULL_URI}"
  # 3. 현재 실행 중인 컨테이너 중단 (없어도 오류 무시)
  "docker stop ${CONTAINER_NAME} || true"
  # 4. 중단된 컨테이너 삭제 (없어도 오류 무시)
  "docker rm   ${CONTAINER_NAME} || true"
  # 5. 새 컨테이너 실행
  #    --restart=always : EC2 재시작 시 컨테이너 자동 재실행
  #    -e SPRING_PROFILES_ACTIVE : dev 프로파일 주입 (Parameter Store에서 설정 로드)
  "docker run -d --name ${CONTAINER_NAME} --restart=always -p ${APP_PORT}:${APP_PORT} -e SPRING_PROFILES_ACTIVE=${SPRING_PROFILE} ${FULL_URI}"
)

# ===== Bash 배열을 SSM이 이해하는 JSON 배열로 변환 =====
COMMANDS_JSON=$(jq -Rn --argjson arr "$(printf '%s\n' "${CMDS[@]}" | jq -R . | jq -s .)" '$arr')
echo "[DEBUG] COMMANDS_JSON=${COMMANDS_JSON}"

# ===== SSM으로 EC2에 명령 전송 =====
# EC2에 직접 SSH하지 않고 AWS API를 통해 명령을 전달함
# EC2에 SSM Agent가 설치되어 있고, IAM 역할에 SSM 권한이 있어야 동작함
RESP=$(aws ssm send-command \
  --document-name "AWS-RunShellScript" \
  --comment "${COMMENT}" \
  --targets "Key=instanceIds,Values=${EC2_INSTANCE_ID}" \
  --parameters "{\"commands\": ${COMMANDS_JSON}}" \
  --region "${AWS_REGION}" \
  --output json)

CMD_ID=$(echo "${RESP}" | jq -r '.Command.CommandId')
echo "[INFO] SSM CommandId: ${CMD_ID}"

# ===== 명령 완료 대기 (최대 150초 = 30회 × 5초) =====
for i in {1..30}; do
  STATUS=$(aws ssm get-command-invocation \
    --command-id "${CMD_ID}" \
    --instance-id "${EC2_INSTANCE_ID}" \
    --query 'Status' \
    --output text \
    --region "${AWS_REGION}") || true

  echo "[INFO] SSM Status: ${STATUS}"

  case "${STATUS}" in
    Success)
      exit 0 ;;   # 배포 성공
    Failed|Cancelled|TimedOut)
      echo "[ERROR] SSM failed: ${STATUS}"
      # 실패 원인 진단을 위해 EC2 실행 로그 출력
      INVOCATION=$(aws ssm get-command-invocation \
        --command-id "${CMD_ID}" \
        --instance-id "${EC2_INSTANCE_ID}" \
        --region "${AWS_REGION}" \
        --output json)
      echo "[ERROR] --- stdout ---"
      echo "${INVOCATION}" | jq -r '.StandardOutputContent'
      echo "[ERROR] --- stderr ---"
      echo "${INVOCATION}" | jq -r '.StandardErrorContent'
      exit 1 ;;
    # InProgress / Pending 은 계속 대기
  esac

  sleep 5
done

echo "[ERROR] SSM command did not complete in time"
exit 1
