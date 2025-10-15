#!/usr/bin/env bash
set -euo pipefail

# Ensure awslocal is available (should be preinstalled in LocalStack image; fallback to pip install)
if ! command -v awslocal >/dev/null 2>&1; then
  pip install awscli-local >/dev/null 2>&1 || true
fi

awslocal sqs create-queue --queue-name leaky-bucket

echo "[init] SQS queue 'leaky-bucket' created (or already exists)."