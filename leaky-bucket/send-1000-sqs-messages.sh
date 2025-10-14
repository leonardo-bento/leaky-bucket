#!/usr/bin/env bash
set -euo pipefail

# Script: send-1000-sqs-messages.sh
# Purpose: Send 1000 messages to the 'leaky-bucket' SQS queue in LocalStack.
# Each message body is numbered so you can identify which message was consumed.
#
# Requirements:
# - AWS CLI installed and available as `aws`
# - LocalStack running (docker-compose up -d localstack)
#
# Usage:
#   ./send-1000-sqs-messages.sh
#
# Optional environment overrides:
#   QUEUE_NAME   - default: leaky-bucket
#   AWS_REGION   - default: us-east-1
#   ENDPOINT_URL - default: http://localhost:4566
#   COUNT        - default: 1000
#
# Examples:
#   QUEUE_NAME=my-queue COUNT=200 ./send-1000-sqs-messages.sh
#   ENDPOINT_URL=http://localhost:4566 ./send-1000-sqs-messages.sh

QUEUE_NAME=${QUEUE_NAME:-leaky-bucket}
AWS_REGION=${AWS_REGION:-us-east-1}
ENDPOINT_URL=${ENDPOINT_URL:-http://localhost:4566}
COUNT=${COUNT:-1000}

if ! command -v aws >/dev/null 2>&1; then
  echo "Error: AWS CLI 'aws' not found. Please install it: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html" >&2
  exit 1
fi

# Obtain the queue URL
QUEUE_URL=$(aws sqs get-queue-url \
  --queue-name "$QUEUE_NAME" \
  --region "$AWS_REGION" \
  --endpoint-url "$ENDPOINT_URL" \
  --output text 2>/dev/null || true)

if [[ -z "$QUEUE_URL" ]]; then
  echo "Queue '$QUEUE_NAME' not found at $ENDPOINT_URL (region $AWS_REGION). Attempting to create it..."
  aws sqs create-queue \
    --queue-name "$QUEUE_NAME" \
    --region "$AWS_REGION" \
    --endpoint-url "$ENDPOINT_URL" >/dev/null
  # Re-fetch URL
  QUEUE_URL=$(aws sqs get-queue-url \
    --queue-name "$QUEUE_NAME" \
    --region "$AWS_REGION" \
    --endpoint-url "$ENDPOINT_URL" \
    --output text)
fi

echo "Using queue URL: $QUEUE_URL"
echo "Sending $COUNT messages to queue '$QUEUE_NAME' at $ENDPOINT_URL (region $AWS_REGION)"

start_ts=$(date +%s)
for i in $(seq 1 "$COUNT"); do
  body="Message #$i"
  # You can add message attributes if needed; for now just send the body
  aws sqs send-message \
    --queue-url "$QUEUE_URL" \
    --message-body "$body" \
    --region "$AWS_REGION" \
    --endpoint-url "$ENDPOINT_URL" >/dev/null
  if (( i % 50 == 0 )); then
    echo "Sent $i/$COUNT messages..."
  fi
done
end_ts=$(date +%s)

echo "Done. Sent $COUNT messages in $((end_ts - start_ts))s."