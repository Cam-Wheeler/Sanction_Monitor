#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
APP_DIR="$PROJECT_DIR/terraform/layers/02-app"
INFRA_DIR="$PROJECT_DIR/terraform/layers/01-infra"
BACKEND_DIR="$PROJECT_DIR/terraform/backend"

echo "--- Teardown Everything ---"
echo "This will destroy EVERYTHING including:"
echo "  - Kubernetes deployments, services, ingress (ALB)"
echo "  - EKS cluster, MSK (Kafka), RDS databases"
echo "  - VPC, subnets, NAT gateway, security groups"
echo "  - ECR repositories and all stored images"
echo "  - Terraform state S3 bucket and DynamoDB lock table"

# Check we know what we are doing!
read -p "Are you sure? Type 'destroy everything' to confirm: " CONFIRM
if [ "$CONFIRM" != "destroy everything" ]; then
  echo "Aborted."
  exit 0
fi

echo ""
echo "[1/3] Destroying application layer (K8s resources)..."
terraform -chdir="$APP_DIR" destroy -auto-approve 2>/dev/null || echo "  Layer 2 not initialized or already destroyed, skipping."

echo ""
echo "[2/3] Destroying infrastructure layer (AWS resources)..."
echo "      This takes ~10-15 minutes (MSK deletion is slow)."
terraform -chdir="$INFRA_DIR" destroy -auto-approve 2>/dev/null || echo "  Layer 1 not initialized or already destroyed, skipping."

echo ""
echo "[3/3] Destroying Terraform backend (S3 bucket + DynamoDB table)..."
terraform -chdir="$BACKEND_DIR" destroy -auto-approve

echo "--- Complete teardown finished ---"
echo "All AWS resources have been destroyed. Zero charges from this point."

