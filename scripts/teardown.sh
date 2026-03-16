#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
APP_DIR="$PROJECT_DIR/terraform/layers/02-app"
INFRA_DIR="$PROJECT_DIR/terraform/layers/01-infra"

echo "--- Teardown Application and AWS (Not Stuff Needed to Spin It Back Up!) ---"
echo ""
echo "This will destroy ALL AWS resources including:"
echo "  - Kubernetes deployments, services, ingress (ALB)"
echo "  - EKS cluster, MSK (Kafka), RDS databases"
echo "  - VPC, subnets, NAT gateway, security groups"
echo "  - ECR repositories and all stored images"
echo ""

# Ensure we actually want to shut it down and not messing up.
read -p "Are you sure? (yes/no): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
  echo "Aborted."
  exit 0
fi

echo ""
echo "[1/2] Destroying application layer (K8s resources)..."
terraform -chdir="$APP_DIR" destroy -auto-approve

echo ""
echo "[2/2] Destroying infrastructure layer (AWS resources)..."
terraform -chdir="$INFRA_DIR" destroy -auto-approve

echo ""
echo "-- Teardown complete ---"
echo "Most of AWS resources have been destroyed. There is still Terraform state S3 bucket and DynamoDB lock table."
echo "To remove them all, use teardown-everything!"
