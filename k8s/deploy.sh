#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
NAMESPACE="sanction-monitor"

echo "=== Sanction Monitor — Kubernetes Deployment ==="

# ── Step 1: Ensure Minikube is running ──
if ! minikube status >/dev/null 2>&1; then
  echo "[1/7] Starting Minikube..."
  minikube start --memory=8192 --cpus=4
else
  echo "[1/7] Minikube is already running."
fi

# ── Step 2: Point Docker CLI at Minikube's daemon ──
echo "[2/7] Configuring Docker to use Minikube's daemon..."
eval $(minikube docker-env)

# ── Step 3: Build custom images ──
echo "[3/7] Building custom Docker images..."

docker build -t sanction-monitor/transaction-generator:latest "$PROJECT_DIR/transactiongenerator" &
docker build -t sanction-monitor/transaction-filter:latest "$PROJECT_DIR/transactionfilter" &
docker build -t sanction-monitor/transaction-analyser:latest "$PROJECT_DIR/transactionanalyser" &
docker build -t sanction-monitor/storage-consumer:latest "$PROJECT_DIR/storageconsumer" &
docker build -t sanction-monitor/dashboard:latest "$PROJECT_DIR/dashboard" &

# db-init needs the JSON file in its build context
cp "$PROJECT_DIR/transactiongenerator/src/main/resources/data/Cleaned-Sanctions.json" \
   "$PROJECT_DIR/infrastructure/db/Cleaned-Sanctions.json"
docker build -t sanction-monitor/db-init:latest \
  -f "$PROJECT_DIR/infrastructure/db/Dockerfile.db-init" \
  "$PROJECT_DIR/infrastructure/db" &

wait
echo "  All images built successfully."

# ── Step 4: Apply foundation resources ──
echo "[4/7] Applying namespace, secrets, and configmaps..."
kubectl apply -f "$SCRIPT_DIR/namespace.yaml"

# Inject actual secrets from .env file if it exists
if [ -f "$PROJECT_DIR/.env" ]; then
  echo "  Populating secrets from .env file..."
  source "$PROJECT_DIR/.env"
  kubectl create secret generic sanction-monitor-secrets \
    --namespace="$NAMESPACE" \
    --from-literal=ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY}" \
    --from-literal=SANCTIONS_DB_USER="sanctions_user" \
    --from-literal=SANCTIONS_DB_PASSWORD="sanctions_pass" \
    --from-literal=SANCTIONS_DB_NAME="sanctions" \
    --from-literal=SANCTIONS_DATABASE_URL="postgres://sanctions_user:sanctions_pass@sanctions-db:5432/sanctions" \
    --from-literal=DASHBOARD_DB_USER="dashboard_user" \
    --from-literal=DASHBOARD_DB_PASSWORD="dashboard_pass" \
    --from-literal=DASHBOARD_DB_NAME="dashboard" \
    --from-literal=DASHBOARD_DATABASE_URL="postgresql://dashboard_user:dashboard_pass@dashboard-db:5432/dashboard" \
    --dry-run=client -o yaml | kubectl apply -f -
else
  echo "  WARNING: .env file not found. Using placeholder secrets from secrets.yaml."
  kubectl apply -f "$SCRIPT_DIR/secrets.yaml"
fi

kubectl apply -f "$SCRIPT_DIR/configmaps/"

# ── Step 5: Deploy infrastructure ──
echo "[5/7] Deploying infrastructure (Kafka, PostgreSQL)..."

# Kafka via Bitnami Helm chart
if helm status kafka -n "$NAMESPACE" >/dev/null 2>&1; then
  echo "  Kafka Helm release already exists. Upgrading..."
  helm upgrade kafka oci://registry-1.docker.io/bitnamicharts/kafka \
    --version 31.3.1 -n "$NAMESPACE" -f "$SCRIPT_DIR/infrastructure/kafka/values.yaml" --timeout 10m
else
  helm install kafka oci://registry-1.docker.io/bitnamicharts/kafka \
    --version 31.3.1 -n "$NAMESPACE" -f "$SCRIPT_DIR/infrastructure/kafka/values.yaml" --timeout 10m
fi

# PostgreSQL
kubectl apply -f "$SCRIPT_DIR/infrastructure/postgres/"

# Wait for infrastructure readiness
echo "  Waiting for PostgreSQL databases to be ready..."
kubectl wait --for=condition=ready pod -l app=sanctions-db -n "$NAMESPACE" --timeout=120s
kubectl wait --for=condition=ready pod -l app=dashboard-db -n "$NAMESPACE" --timeout=120s

echo "  Waiting for Kafka to be ready..."
kubectl wait --for=condition=ready pod -l app.kubernetes.io/component=controller-eligible -n "$NAMESPACE" --timeout=300s

echo "  Creating Kafka topics..."
kubectl exec kafka-controller-0 -n "$NAMESPACE" -- bash -c "
kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic transactions-topic --partitions 3 --replication-factor 3 &&
kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic flagged-transactions-topic --partitions 3 --replication-factor 3 &&
kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic approved-transactions-topic --partitions 3 --replication-factor 3 &&
kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic analysis-results-topic --partitions 3 --replication-factor 3
"
echo "  Kafka topics created."

# ── Step 6: Run init jobs and deploy services ──
echo "[6/7] Running init jobs and deploying application services..."

# DB init job
kubectl apply -f "$SCRIPT_DIR/jobs/"
echo "  Waiting for db-init job to complete..."
kubectl wait --for=condition=complete job/db-init -n "$NAMESPACE" --timeout=120s

# Kafka UI
kubectl apply -f "$SCRIPT_DIR/infrastructure/kafka/kafka-ui.yaml"

# Application services
kubectl apply -f "$SCRIPT_DIR/services/"

# ── Step 7: Verify ──
echo "[7/7] Waiting for all pods to be ready..."
sleep 5
kubectl get pods -n "$NAMESPACE"

echo ""
echo "=== Deployment complete! ==="
echo ""
echo "Access services via port-forwarding:"
echo "  Transaction Generator:  kubectl port-forward svc/transaction-generator 8080:8080 -n $NAMESPACE"
echo "  Dashboard:              kubectl port-forward svc/dashboard 8501:8501 -n $NAMESPACE"
echo "  Kafka UI:               kubectl port-forward svc/kafka-ui 9000:8080 -n $NAMESPACE"
echo "  Flink Web UI:           kubectl port-forward svc/analyser-jobmanager 8081:8081 -n $NAMESPACE"
echo ""
echo "Generate transactions:    curl -X POST http://localhost:8080/start/10"
