#!/usr/bin/env bash
# Hits every microservice's /health endpoint through API Gateway.
set -euo pipefail

AWS_PROFILE="${AWS_PROFILE:-aws-4}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"

API_URL="$(aws cloudformation describe-stacks \
  --stack-name EcomApiStack \
  --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text)"

echo "API_URL=$API_URL"

for path in /auth/health /users/health /products/health /inventory/health /cart/health \
            /orders/health /payments/health /shipping/health /notifications/health /reviews/health; do
  echo "GET ${API_URL}${path}"
  curl -sS -o /tmp/body -w "  -> HTTP %{http_code} (%{time_total}s)\n" "${API_URL}${path}" || true
  cat /tmp/body && echo
done
