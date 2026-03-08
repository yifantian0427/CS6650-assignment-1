#!/usr/bin/env bash
#
# Create Application Load Balancer for server-v2 (WebSocket) instances.
# Requirements: AWS CLI configured; source params.env first.
#
# Health check: /health, interval 30s, timeout 5s, healthy 2, unhealthy 3
# Sticky sessions: enabled for WebSocket session affinity
# Idle timeout: > 60s (WebSocket support)
#

set -e

# Load parameters (use params.env.example as template)
if [ -f "$(dirname "$0")/params.env" ]; then
  source "$(dirname "$0")/params.env"
else
  echo "Create params.env from params.env.example and set VPC_ID, SUBNET_IDS, etc."
  exit 1
fi

REGION="${AWS_REGION:-us-east-1}"
ALB_NAME="${ALB_NAME:-cs6650-chat-alb}"
TG_NAME="${TARGET_GROUP_NAME:-cs6650-chat-servers}"

echo "Creating ALB: $ALB_NAME in $REGION"

# 1. Create Application Load Balancer
ALB_ARN=$(aws elbv2 create-load-balancer \
  --region "$REGION" \
  --name "$ALB_NAME" \
  --subnets $SUBNET_IDS \
  --security-groups "$ALB_SECURITY_GROUP_ID" \
  --scheme internet-facing \
  --type application \
  --query 'LoadBalancers[0].LoadBalancerArn' \
  --output text)

echo "ALB ARN: $ALB_ARN"

# 2. Set idle timeout to 70s (WebSocket; requirement says > 60s)
aws elbv2 modify-load-balancer-attributes \
  --region "$REGION" \
  --load-balancer-arn "$ALB_ARN" \
  --attributes Key=idle_timeout.timeout_seconds,Value=70

# 3. Create target group (HTTP port 8080)
TG_ARN=$(aws elbv2 create-target-group \
  --region "$REGION" \
  --name "$TG_NAME" \
  --protocol HTTP \
  --port 8080 \
  --vpc-id "$VPC_ID" \
  --health-check-protocol HTTP \
  --health-check-port 8080 \
  --health-check-path "/health" \
  --health-check-interval-seconds 30 \
  --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3 \
  --target-type instance \
  --query 'TargetGroups[0].TargetGroupArn' \
  --output text)

echo "Target group ARN: $TG_ARN"

# 4. Enable sticky sessions on target group
aws elbv2 modify-target-group-attributes \
  --region "$REGION" \
  --target-group-arn "$TG_ARN" \
  --attributes \
    Key=stickiness.enabled,Value=true \
    Key=stickiness.type,Value=lb_cookie \
    Key=stickiness.lb_cookie.duration_seconds,Value=86400

# 5. Create listener (forward to target group)
aws elbv2 create-listener \
  --region "$REGION" \
  --load-balancer-arn "$ALB_ARN" \
  --protocol HTTP \
  --port 80 \
  --default-actions Type=forward,TargetGroupArn="$TG_ARN"

echo "Listener created (HTTP port 80 -> target group)."

# 6. Register server instances as targets
if [ -n "${SERVER_INSTANCE_IDS:-}" ]; then
  for INSTANCE_ID in $SERVER_INSTANCE_IDS; do
    aws elbv2 register-targets \
      --region "$REGION" \
      --target-group-arn "$TG_ARN" \
      --targets Id="$INSTANCE_ID"
    echo "Registered instance: $INSTANCE_ID"
  done
else
  echo "Set SERVER_INSTANCE_IDS in params.env and run register-targets manually:"
  echo "  aws elbv2 register-targets --region $REGION --target-group-arn $TG_ARN --targets Id=i-xxxx"
fi

# Output for client config
ALB_DNS=$(aws elbv2 describe-load-balancers --region "$REGION" --load-balancer-arns "$ALB_ARN" \
  --query 'LoadBalancers[0].DNSName' --output text)
echo ""
echo "Done. ALB DNS: $ALB_DNS"
echo "WebSocket URL: ws://$ALB_DNS/chat/<roomId>"
echo "Health check: http://$ALB_DNS/health"
