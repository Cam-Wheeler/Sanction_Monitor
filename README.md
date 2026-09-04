# Sanction Monitor

Real-time sanctions screening pipeline for financial transactions.

UK banks must screen every transaction against sanctioned individuals. Missing one is expensive — Starling Bank was fined nearly £29 million by the FCA, and Bank of Scotland was fined £160,000 after a sanctioned person accessed systems using a variant spelling of his name. At millions of transactions, manual review is not an option.

Sanction Monitor is an event-driven system that screens transactions as they happen: fuzzy-match against the UK sanctions list, enrich flagged parties with recent history, and use an LLM to triage genuine risk for a compliance dashboard.

**Write-up:** [How Sanction Monitor works](https://cam-wheeler.github.io/projects/sanction-monitor/)

## Architecture

Each stage is a separate service connected by Kafka topics, so components can fail, scale, and evolve independently.

```
┌─────────────────────┐
│ Transaction         │
│ Generator           │  Spring Boot — publishes synthetic transactions
└─────────┬───────────┘
          │  transactions-topic
          ▼
┌─────────────────────┐     ┌──────────────────┐
│ Transaction Filter  │────▶│ Sanctions DB     │
│ (Rust)              │     │ PostgreSQL       │  UK list + pg_trgm fuzzy match
└─────────┬───────────┘     └──────────────────┘
          │
          ├─ no match ──▶ approved-transactions-topic
          │
          └─ match ─────▶ flagged-transactions-topic
                          ▼
                 ┌─────────────────────┐
                 │ Flink Analyser      │  keyed state (30 min TTL, 1 min watermark)
                 │ (Java / Flink)      │  async Anthropic API
                 └─────────┬───────────┘
                           │  analysis-results-topic
                           ▼
                 ┌─────────────────────┐     ┌──────────────────┐
                 │ Storage Consumer    │────▶│ Dashboard DB     │
                 │ (Python)            │     │ PostgreSQL       │
                 └─────────────────────┘     └────────┬─────────┘
                                                      ▼
                                             ┌──────────────────┐
                                             │ Streamlit        │
                                             │ Dashboard :8501  │
                                             └──────────────────┘
```

### Why Kafka and Flink

Kafka turns every transaction, flag, and screening decision into an immutable event that can be replayed for audit. Topics buffer stages so a Flink outage does not drop messages.

Flink holds a rolling window of recent flagged activity per party (keyed state, 30-minute TTL). A 1-minute watermark buffer lets late events land before state is cleaned — without that, out-of-order events would be analysed with incomplete history. See the [blog post](https://cam-wheeler.github.io/projects/sanction-monitor/) for the watermark walkthrough.

## Services

| Service | Language | Role |
|---|---|---|
| `transactiongenerator` | Java 21 / Spring Boot | Synthetic transactions onto `transactions-topic` |
| `transactionfilter` | Rust | `pg_trgm` name similarity vs UK sanctions list; nationality boost (+0.1); default threshold `0.5` |
| `transactionanalyser` | Java 17 / Flink 1.20 | Enrich with party history, call Anthropic, write verdicts |
| `storageconsumer` | Python | Persist approved + analysed results to PostgreSQL |
| `dashboard` | Python / Streamlit | Compliance UI on port 8501 |

Kafka topics: `transactions-topic`, `flagged-transactions-topic`, `approved-transactions-topic`, `analysis-results-topic` (3 partitions, RF 3).

## Prerequisites

- Docker and Docker Compose
- An [Anthropic API key](https://console.anthropic.com/)

Optional (Kubernetes / AWS):

- Minikube, kubectl, Helm
- AWS CLI, Terraform >= 1.5

## Local development (Docker Compose)

1. Copy the env template and add your key:

```bash
cp .example_env .env
```

2. Start the stack:

```bash
docker compose up --build
```

3. Generate traffic:

```bash
# n random transactions
curl -X POST http://localhost:8080/start/10

# 3 small + 1 large payment from the same sanctioned individual
curl -X POST http://localhost:8080/test-sequence
```

| UI | URL |
|---|---|
| Dashboard | http://localhost:8501 |
| Transaction generator | http://localhost:8080 |
| Kafka UI | http://localhost:9000 |

## Kubernetes (Minikube)

```bash
# from repo root; reads ANTHROPIC_API_KEY from .env
./k8s/deploy.sh
```

The script starts Minikube (8 GB / 4 CPUs), builds images into the Minikube Docker daemon, deploys Kafka (Bitnami Helm) and Postgres, then the app services.

```bash
kubectl port-forward svc/transaction-generator 8080:8080 -n sanction-monitor
kubectl port-forward svc/dashboard 8501:8501 -n sanction-monitor
kubectl port-forward svc/kafka-ui 9000:8080 -n sanction-monitor
kubectl port-forward svc/analyser-jobmanager 8081:8081 -n sanction-monitor
```

Tear down:

```bash
./k8s/teardown.sh
```

## Cloud (AWS + Terraform)

Production lives in a VPC: ALB and NAT in public subnets; EKS, MSK (3 KRaft brokers), and RDS in private subnets. Images go to ECR.

**This stack costs money. Destroy it when you are done.**

Apply in order:

```bash
# 1. Remote state (S3 + DynamoDB lock) — eu-west-2
cd terraform/backend
terraform init
terraform plan
terraform apply

# 2. VPC, EKS, MSK, RDS, ECR
cd ../layers/01-infra
terraform init
terraform plan
terraform apply

# 3. Build and push images
cd ../../../scripts
./build-and-push.sh

# 4. Kubernetes app layer on EKS
cd ../terraform/layers/02-app
terraform init
terraform plan
terraform apply
```

Tear down:

```bash
./scripts/teardown.sh              # app + infra; keeps state bucket
./scripts/teardown-everything.sh   # including S3 state and DynamoDB lock
```

## Repository layout

```
transactiongenerator/   Spring Boot producer
transactionfilter/      Rust fuzzy-match consumer
transactionanalyser/    Flink job (enrichment + Anthropic)
storageconsumer/        Python Kafka → Postgres
dashboard/              Streamlit UI
infrastructure/         Postgres init, Kafka topic bootstrap
k8s/                    Minikube manifests and deploy scripts
terraform/
  backend/              S3 state + DynamoDB lock
  layers/01-infra/      VPC, EKS, MSK, RDS, ECR
  layers/02-app/        App on EKS
  modules/              Reusable Terraform modules
scripts/                ECR push and AWS teardown
```

## Further reading

The design notes — Kafka vs batch, Flink watermarks, shifting processing left, Docker → Kubernetes → AWS — are in the project write-up:

**[Sanction Monitor on cam-wheeler.github.io](https://cam-wheeler.github.io/projects/sanction-monitor/)**
