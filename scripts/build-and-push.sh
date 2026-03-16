#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
INFRA_DIR="$PROJECT_DIR/terraform/layers/01-infra"

echo "--- Build and Push Docker Images to ECR ---"

echo "Reading Terraform outputs..."
ECR_REGISTRY=$(terraform -chdir="$INFRA_DIR" output -raw ecr_registry)
REGION=$(terraform -chdir="$INFRA_DIR" output -raw region)

echo "  Registry: $ECR_REGISTRY"
echo "  Region:   $REGION"

echo "Logging into ECR..."
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# We need the json in the build to make life easier.
cp "$PROJECT_DIR/transactiongenerator/src/main/resources/data/Cleaned-Sanctions.json" \
   "$PROJECT_DIR/infrastructure/db/Cleaned-Sanctions.json"

# Build and push each service.
SERVICES="transaction-generator:transactiongenerator
transaction-filter:transactionfilter
transaction-analyser:transactionanalyser
storage-consumer:storageconsumer
dashboard:dashboard"

echo "$SERVICES" | while IFS=: read -r NAME DIR; do
  echo "Building $NAME..."
  docker buildx build --platform linux/amd64 -t "$ECR_REGISTRY/sanction-monitor/$NAME:latest" "$PROJECT_DIR/$DIR"
  echo "Pushing $NAME..."
  docker push "$ECR_REGISTRY/sanction-monitor/$NAME:latest"
done

# Build and push db-init (custom Dockerfile path)
echo "Building db-init..."
docker buildx build --platform linux/amd64 -t "$ECR_REGISTRY/sanction-monitor/db-init:latest" \
  -f "$PROJECT_DIR/infrastructure/db/Dockerfile.db-init" \
  "$PROJECT_DIR/infrastructure/db"
echo "Pushing db-init..."
docker push "$ECR_REGISTRY/sanction-monitor/db-init:latest"

echo "--- All images pushed to ECR ---"
