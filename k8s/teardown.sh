#!/bin/bash
set -euo pipefail

NAMESPACE="sanction-monitor"

echo "=== Tearing down Sanction Monitor from Kubernetes ==="

# Uninstall Kafka Helm release
if helm status kafka -n "$NAMESPACE" >/dev/null 2>&1; then
  echo "Uninstalling Kafka Helm release..."
  helm uninstall kafka -n "$NAMESPACE"
fi

# Delete the namespace (removes all other resources)
echo "Deleting namespace '$NAMESPACE' and all resources within it..."
kubectl delete namespace "$NAMESPACE" --ignore-not-found

echo ""
echo "=== Teardown complete ==="
echo "Note: PersistentVolumes may still exist. Run 'kubectl get pv' to check."
