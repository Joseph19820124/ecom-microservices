#!/usr/bin/env bash
# End-to-end deploy:
#   1. CDK bootstrap (idempotent)
#   2. CDK deploy ECR + Network stacks (creates the empty repos & VPC/ALB)
#   3. Build + push images to the new ECR repos
#   4. CDK deploy the rest (ECS + API)
#   5. Force ECS service redeployment so the new :latest image is pulled
set -euo pipefail

AWS_PROFILE="${AWS_PROFILE:-aws-4}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text)}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/infra"

export AWS_PROFILE AWS_REGION AWS_ACCOUNT_ID
export CDK_DEFAULT_ACCOUNT="$AWS_ACCOUNT_ID"
export CDK_DEFAULT_REGION="$AWS_REGION"

echo ">>> cdk bootstrap"
npx cdk bootstrap "aws://${AWS_ACCOUNT_ID}/${AWS_REGION}" --profile "$AWS_PROFILE"

echo ">>> deploy EcomEcrStack + EcomNetworkStack"
npx cdk deploy EcomEcrStack EcomNetworkStack \
  --profile "$AWS_PROFILE" --require-approval never --concurrency 2

echo ">>> build & push images"
"$ROOT/scripts/build-and-push.sh"

echo ">>> deploy EcomEcsStack + EcomApiStack"
npx cdk deploy EcomEcsStack EcomApiStack \
  --profile "$AWS_PROFILE" --require-approval never --concurrency 2

echo ">>> force ECS service redeploy"
for s in auth user product inventory cart order payment shipping notification review; do
  aws ecs update-service \
    --cluster ecom-cluster \
    --service "${s}-service" \
    --force-new-deployment \
    --profile "$AWS_PROFILE" --region "$AWS_REGION" \
    --query 'service.serviceName' --output text || true
done

echo ">>> API endpoint:"
aws cloudformation describe-stacks \
  --stack-name EcomApiStack \
  --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text
