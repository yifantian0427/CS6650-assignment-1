#!/usr/bin/env bash
# Register server-v2 instance(s) with the target group. Source params.env first.
# Usage: ./register-targets.sh [instance-id-1] [instance-id-2] ...
# Or set SERVER_INSTANCE_IDS in params.env and run with no args.

set -e
source "$(dirname "$0")/params.env"

REGION="${AWS_REGION:-us-east-1}"
TG_NAME="${TARGET_GROUP_NAME:-cs6650-chat-servers}"

TG_ARN=$(aws elbv2 describe-target-groups --region "$REGION" --names "$TG_NAME" \
  --query 'TargetGroups[0].TargetGroupArn' --output text)

if [ "$#" -gt 0 ]; then
  for INSTANCE_ID in "$@"; do
    aws elbv2 register-targets --region "$REGION" --target-group-arn "$TG_ARN" \
      --targets Id="$INSTANCE_ID"
    echo "Registered: $INSTANCE_ID"
  done
else
  for INSTANCE_ID in $SERVER_INSTANCE_IDS; do
    aws elbv2 register-targets --region "$REGION" --target-group-arn "$TG_ARN" \
      --targets Id="$INSTANCE_ID"
    echo "Registered: $INSTANCE_ID"
  done
fi
