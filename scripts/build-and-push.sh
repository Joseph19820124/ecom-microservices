#!/usr/bin/env bash
# Build all 10 services, tag images as :latest and a git short SHA,
# log into ECR, and push everything.
#
# Required env (with sensible defaults):
#   AWS_PROFILE   default: aws-4
#   AWS_REGION    default: ap-northeast-2 (Seoul)
#   AWS_ACCOUNT_ID auto-resolved from STS if unset
set -euo pipefail

AWS_PROFILE="${AWS_PROFILE:-aws-4}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text)}"
REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

SERVICES=(auth user product inventory cart order payment shipping notification review)

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo ">>> mvn package (skip tests)"
mvn -q -T 1C package -DskipTests

echo ">>> ecr login -> $REGISTRY"
aws ecr get-login-password --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"

GIT_SHA="$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo "manual")"

for s in "${SERVICES[@]}"; do
  IMAGE="${REGISTRY}/ecom/${s}-service"
  echo ">>> docker build $s-service -> $IMAGE"
  docker build \
    -f "services/${s}-service/Dockerfile" \
    -t "${IMAGE}:latest" \
    -t "${IMAGE}:${GIT_SHA}" \
    "services/${s}-service"
  docker push "${IMAGE}:latest"
  docker push "${IMAGE}:${GIT_SHA}"
done

echo ">>> done; tag=${GIT_SHA}"
