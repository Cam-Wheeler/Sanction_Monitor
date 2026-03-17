
# Namespace
resource "kubernetes_namespace_v1" "sanction_monitor" {
  metadata {
    name = local.namespace
  }
}

# Secret
resource "kubernetes_secret_v1" "sanction_monitor" {
  metadata {
    name = "sanction-monitor-secrets"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
  }

  data = {
    ANTHROPIC_API_KEY = var.anthropic_api_key
    SANCTIONS_DB_USER = local.sanctions_db.username
    SANCTIONS_DB_PASSWORD = local.sanctions_db.password
    SANCTIONS_DB_NAME = local.sanctions_db.db_name
    SANCTIONS_DATABASE_URL = local.sanctions_db_url
    DASHBOARD_DB_USER = local.dashboard_db.username
    DASHBOARD_DB_PASSWORD = local.dashboard_db.password
    DASHBOARD_DB_NAME = local.dashboard_db.db_name
    DASHBOARD_DATABASE_URL = local.dashboard_db_url
  }
}

# ConfigMap — App Config
resource "kubernetes_config_map_v1" "app_config" {
  metadata {
    name = "app-config"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
  }

  data = {
    KAFKA_BOOTSTRAP_SERVERS = local.msk_brokers
    MATCH_THRESHOLD = "0.5"
    RUST_LOG = "info"
    KAFKA_GROUP_ID = "storage-consumer-group"

    "application.properties" = <<-EOT
      spring.application.name=transactiongenerator
      data.sanction.json=classpath:data/Cleaned-Sanctions.json
      data.valid.json=classpath:data/Valid.json
      kafka.bootstrap-servers=${local.msk_brokers}
      kafka.acks=all
      kafka.key-serializer=org.apache.kafka.common.serialization.StringSerializer
      kafka.value-serializer=org.apache.kafka.common.serialization.StringSerializer
      kafka.topic-name=transactions-topic
    EOT
  }
}

# ConfigMap — Init Scripts (SQL)
resource "kubernetes_config_map_v1" "init_scripts" {
  metadata {
    name = "init-scripts"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
  }

  data = {
    "sanctions-init.sql" = <<-EOT
      CREATE EXTENSION IF NOT EXISTS pg_trgm;

      CREATE TABLE IF NOT EXISTS sanctioned_individuals (
          id               SERIAL PRIMARY KEY,
          name             TEXT NOT NULL,
          nationality      TEXT,
          gender           TEXT,
          dob              TEXT,
          position         TEXT,
          sanctions        TEXT,
          sanction_creator TEXT,
          reason           TEXT,
          other_info       TEXT
      );

      CREATE INDEX IF NOT EXISTS idx_sanctions_name_trgm ON sanctioned_individuals USING GIN (name gin_trgm_ops);
      CREATE INDEX IF NOT EXISTS idx_sanctions_nationality ON sanctioned_individuals (nationality);
    EOT

    "dashboard-init.sql" = <<-EOT
      CREATE TABLE IF NOT EXISTS transactions (
          transaction_id        UUID PRIMARY KEY,
          amount                DOUBLE PRECISION NOT NULL,
          date                  TEXT NOT NULL,
          time                  TEXT NOT NULL,
          type                  TEXT NOT NULL,
          sender_uid            UUID NOT NULL,
          sender_name           TEXT NOT NULL,
          sender_nationality    TEXT NOT NULL,
          sender_account        UUID NOT NULL,
          sender_bank           TEXT NOT NULL,
          sender_location       TEXT NOT NULL,
          receiver_uid            UUID NOT NULL,
          receiver_name           TEXT NOT NULL,
          receiver_nationality    TEXT NOT NULL,
          receiver_account        UUID NOT NULL,
          receiver_bank           TEXT NOT NULL,
          receiver_location       TEXT NOT NULL,
          flagged               BOOLEAN NOT NULL,
          sender_match_score    REAL,
          sender_match_name     TEXT,
          receiver_match_score  REAL,
          receiver_match_name   TEXT,
          verdict               TEXT,
          confidence            DOUBLE PRECISION,
          reasoning             TEXT,
          model                 TEXT,
          analysed_at           TIMESTAMPTZ,
          status                TEXT NOT NULL DEFAULT 'APPROVED',
          ingested_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );

      CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions (status);
      CREATE INDEX IF NOT EXISTS idx_transactions_flagged ON transactions (flagged);
      CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions (date);
      CREATE INDEX IF NOT EXISTS idx_transactions_verdict ON transactions (verdict) WHERE verdict IS NOT NULL;
    EOT
  }
}

# Helm — AWS Load Balancer Controller
resource "helm_release" "alb_controller" {
  name = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart = "aws-load-balancer-controller"
  namespace = "kube-system"

  set {
    name = "clusterName"
    value = local.eks_cluster_name
  }

  set {
    name = "serviceAccount.create"
    value = "true"
  }

  set {
    name = "serviceAccount.name"
    value = "aws-load-balancer-controller"
  }

  set {
    name = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = local.alb_role_arn
  }

  set {
    name = "region"
    value = local.region
  }

  set {
    name = "vpcId"
    value = local.vpc_id
  }
}

# Job — Kafka Topic Init
resource "kubernetes_job_v1" "kafka_init" {
  metadata {
    name = "kafka-init"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
  }

  spec {
    backoff_limit = 5

    template {
      metadata {}
      spec {
        restart_policy = "OnFailure"

        init_container {
          name = "wait-for-kafka"
          image = "busybox:1.36"
          command = [
            "sh", "-c",
            "echo 'Waiting for MSK...'; i=0; while [ $i -lt 60 ]; do nc -z ${local.msk_first_broker_host} 9092 && exit 0; i=$((i+1)); sleep 5; done; echo 'Timed out waiting for MSK'; exit 1"
          ]
        }

        container {
          name = "kafka-init"
          image = "apache/kafka:3.7.0"
          command = [
            "sh", "-c",
            <<-EOT
              BOOTSTRAP="${local.msk_brokers}"

              for TOPIC in transactions-topic flagged-transactions-topic approved-transactions-topic analysis-results-topic; do
                echo "Creating topic: $TOPIC"
                /opt/kafka/bin/kafka-topics.sh \
                  --bootstrap-server "$BOOTSTRAP" \
                  --create \
                  --if-not-exists \
                  --topic "$TOPIC" \
                  --partitions 3 \
                  --replication-factor 3
              done

              echo "All topics created."
              /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --list
            EOT
          ]
        }
      }
    }
  }

  wait_for_completion = true

  timeouts {
    create = "10m"
  }
}

# Job — Database Init
resource "kubernetes_job_v1" "db_init" {
  metadata {
    name = "db-init"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
  }

  spec {
    backoff_limit = 10

    template {
      metadata {}
      spec {
        restart_policy = "OnFailure"

        # Init: create sanctions-db schema
        init_container {
          name = "init-sanctions-schema"
          image = "postgres:16-alpine"
          command = [
            "sh", "-c",
            <<-EOT
              echo "Initializing sanctions-db schema..."
              until pg_isready -h "$DB_HOST" -p 5432 -U "$DB_USER"; do
                sleep 2
              done
              PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" \
                -f /scripts/sanctions-init.sql
              echo "Sanctions schema initialized."
            EOT
          ]

          env {
            name = "DB_HOST"
            value = local.sanctions_db.address
          }

          env {
            name = "DB_NAME"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "SANCTIONS_DB_NAME"
              }
            }
          }

          env {
            name = "DB_USER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "SANCTIONS_DB_USER"
              }
            }
          }

          env {
            name = "DB_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "SANCTIONS_DB_PASSWORD"
              }
            }
          }

          volume_mount {
            name = "init-scripts"
            mount_path = "/scripts"
          }
        }

        # Init: create dashboard-db schema
        init_container {
          name = "init-dashboard-schema"
          image = "postgres:16-alpine"
          command = [
            "sh", "-c",
            <<-EOT
              echo "Initializing dashboard-db schema..."
              until pg_isready -h "$DB_HOST" -p 5432 -U "$DB_USER"; do
                sleep 2
              done
              PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" \
                -f /scripts/dashboard-init.sql
              echo "Dashboard schema initialized."
            EOT
          ]

          env {
            name = "DB_HOST"
            value = local.dashboard_db.address
          }

          env {
            name = "DB_NAME"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "DASHBOARD_DB_NAME"
              }
            }
          }

          env {
            name = "DB_USER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "DASHBOARD_DB_USER"
              }
            }
          }

          env {
            name = "DB_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "DASHBOARD_DB_PASSWORD"
              }
            }
          }

          volume_mount {
            name = "init-scripts"
            mount_path = "/scripts"
          }
        }

        # Main: load sanctions data
        container {
          name = "load-sanctions"
          image = "${local.ecr_repository_urls["db-init"]}:latest"

          env {
            name = "DB_HOST"
            value = local.sanctions_db.address
          }

          env {
            name = "DB_PORT"
            value = "5432"
          }

          env {
            name = "DB_NAME"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "SANCTIONS_DB_NAME"
              }
            }
          }

          env {
            name = "DB_USER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "SANCTIONS_DB_USER"
              }
            }
          }

          env {
            name = "DB_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "SANCTIONS_DB_PASSWORD"
              }
            }
          }
        }

        volume {
          name = "init-scripts"
          config_map {
            name = kubernetes_config_map_v1.init_scripts.metadata[0].name
          }
        }
      }
    }
  }

  wait_for_completion = true

  timeouts {
    create = "10m"
  }

  depends_on = [kubernetes_job_v1.kafka_init]
}

# Deployment + Service — Transaction Generator
resource "kubernetes_deployment_v1" "transaction_generator" {
  metadata {
    name = "transaction-generator"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
    labels = {
      app = "transaction-generator"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "transaction-generator"
      }
    }

    template {
      metadata {
        labels = {
          app = "transaction-generator"
        }
      }

      spec {
        init_container {
          name = "wait-for-kafka"
          image = "busybox:1.36"
          command = [
            "sh", "-c",
            "i=0; while [ $i -lt 60 ]; do nc -z ${local.msk_first_broker_host} 9092 && exit 0; i=$((i+1)); sleep 5; done; echo 'Timed out waiting for Kafka'; exit 1"
          ]
        }

        container {
          name = "transaction-generator"
          image = "${local.ecr_repository_urls["transaction-generator"]}:latest"

          port {
            container_port = 8080
          }

          volume_mount {
            name = "app-config"
            mount_path = "/app/application.properties"
            sub_path = "application.properties"
          }

          resources {
            requests = {
              memory = "256Mi"
              cpu = "250m"
            }
            limits = {
              memory = "512Mi"
              cpu = "500m"
            }
          }
        }

        volume {
          name = "app-config"
          config_map {
            name = kubernetes_config_map_v1.app_config.metadata[0].name
          }
        }
      }
    }
  }

  depends_on = [kubernetes_job_v1.db_init]
}

resource "kubernetes_service_v1" "transaction_generator" {
  metadata {
    name = "transaction-generator"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
  }

  spec {
    selector = {
      app = "transaction-generator"
    }

    port {
      port = 8080
      target_port = 8080
    }
  }
}


# Deployment — Transaction Filter
resource "kubernetes_deployment_v1" "transaction_filter" {
  metadata {
    name = "transaction-filter"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
    labels = {
      app = "transaction-filter"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "transaction-filter"
      }
    }

    template {
      metadata {
        labels = {
          app = "transaction-filter"
        }
      }

      spec {
        init_container {
          name = "wait-for-dependencies"
          image = "busybox:1.36"
          command = [
            "sh", "-c",
            "i=0; while [ $i -lt 60 ]; do nc -z ${local.msk_first_broker_host} 9092 && break; i=$((i+1)); sleep 5; done; [ $i -ge 60 ] && echo 'Timed out waiting for Kafka' && exit 1; i=0; while [ $i -lt 60 ]; do nc -z ${local.sanctions_db.address} 5432 && exit 0; i=$((i+1)); sleep 5; done; echo 'Timed out waiting for sanctions-db'; exit 1"
          ]
        }

        container {
          name = "transaction-filter"
          image = "${local.ecr_repository_urls["transaction-filter"]}:latest"

          env {
            name = "DATABASE_URL"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "SANCTIONS_DATABASE_URL"
              }
            }
          }

          env {
            name = "KAFKA_BOOTSTRAP_SERVERS"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map_v1.app_config.metadata[0].name
                key = "KAFKA_BOOTSTRAP_SERVERS"
              }
            }
          }

          env {
            name = "MATCH_THRESHOLD"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map_v1.app_config.metadata[0].name
                key = "MATCH_THRESHOLD"
              }
            }
          }

          env {
            name = "RUST_LOG"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map_v1.app_config.metadata[0].name
                key = "RUST_LOG"
              }
            }
          }

          resources {
            requests = {
              memory = "128Mi"
              cpu = "100m"
            }
            limits = {
              memory = "256Mi"
              cpu = "250m"
            }
          }
        }
      }
    }
  }

  depends_on = [kubernetes_job_v1.db_init]
}


# Deployment + Service — Analyser JobManager
resource "kubernetes_deployment_v1" "analyser_jobmanager" {
  metadata {
    name = "analyser-jobmanager"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
    labels = {
      app = "analyser-jobmanager"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "analyser-jobmanager"
      }
    }

    template {
      metadata {
        labels = {
          app = "analyser-jobmanager"
        }
      }

      spec {
        init_container {
          name = "wait-for-kafka"
          image = "busybox:1.36"
          command = [
            "sh", "-c",
            "i=0; while [ $i -lt 60 ]; do nc -z ${local.msk_first_broker_host} 9092 && exit 0; i=$((i+1)); sleep 5; done; echo 'Timed out waiting for Kafka'; exit 1"
          ]
        }

        container {
          name = "jobmanager"
          image = "${local.ecr_repository_urls["transaction-analyser"]}:latest"

          # Use args only (not command) so Docker entrypoint runs and processes env vars
          args = ["standalone-job", "--job-classname", "com.camwheeler.transactionanalyser.AnalyserJob"]

          port {
            name = "rpc"
            container_port = 6123
          }

          port {
            name = "web-ui"
            container_port = 8081
          }

          env {
            name = "JOB_MANAGER_RPC_ADDRESS"
            value = "analyser-jobmanager"
          }

          env {
            name = "TASK_MANAGER_NUMBER_OF_TASK_SLOTS"
            value = "2"
          }

          env {
            name = "ANTHROPIC_API_KEY"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "ANTHROPIC_API_KEY"
              }
            }
          }

          env {
            name = "KAFKA_BOOTSTRAP_SERVERS"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map_v1.app_config.metadata[0].name
                key = "KAFKA_BOOTSTRAP_SERVERS"
              }
            }
          }

          resources {
            requests = {
              memory = "512Mi"
              cpu = "250m"
            }
            limits = {
              memory = "1Gi"
              cpu = "500m"
            }
          }
        }
      }
    }
  }

  depends_on = [kubernetes_job_v1.db_init]
}

resource "kubernetes_service_v1" "analyser_jobmanager" {
  metadata {
    name = "analyser-jobmanager"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
  }

  spec {
    selector = {
      app = "analyser-jobmanager"
    }

    port {
      name = "rpc"
      port = 6123
      target_port = 6123
    }

    port {
      name = "web-ui"
      port = 8081
      target_port = 8081
    }
  }
}


# Deployment — Analyser TaskManager
resource "kubernetes_deployment_v1" "analyser_taskmanager" {
  metadata {
    name = "analyser-taskmanager"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
    labels = {
      app = "analyser-taskmanager"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "analyser-taskmanager"
      }
    }

    template {
      metadata {
        labels = {
          app = "analyser-taskmanager"
        }
      }

      spec {
        init_container {
          name = "wait-for-jobmanager"
          image = "busybox:1.36"
          command = [
            "sh", "-c",
            "i=0; while [ $i -lt 60 ]; do nc -z analyser-jobmanager 6123 && exit 0; i=$((i+1)); sleep 5; done; echo 'Timed out waiting for JobManager'; exit 1"
          ]
        }

        container {
          name = "taskmanager"
          image = "${local.ecr_repository_urls["transaction-analyser"]}:latest"

          # Use args only (not command) so Docker entrypoint runs and processes env vars
          args = ["taskmanager"]

          env {
            name = "JOB_MANAGER_RPC_ADDRESS"
            value = "analyser-jobmanager"
          }

          env {
            name = "TASK_MANAGER_NUMBER_OF_TASK_SLOTS"
            value = "2"
          }

          env {
            name = "ANTHROPIC_API_KEY"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "ANTHROPIC_API_KEY"
              }
            }
          }

          env {
            name = "KAFKA_BOOTSTRAP_SERVERS"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map_v1.app_config.metadata[0].name
                key = "KAFKA_BOOTSTRAP_SERVERS"
              }
            }
          }

          resources {
            requests = {
              memory = "512Mi"
              cpu = "250m"
            }
            limits = {
              memory = "1Gi"
              cpu = "500m"
            }
          }
        }
      }
    }
  }

  depends_on = [kubernetes_deployment_v1.analyser_jobmanager]
}

# Deployment — Storage Consumer
resource "kubernetes_deployment_v1" "storage_consumer" {
  metadata {
    name = "storage-consumer"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
    labels = {
      app = "storage-consumer"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "storage-consumer"
      }
    }

    template {
      metadata {
        labels = {
          app = "storage-consumer"
        }
      }

      spec {
        init_container {
          name = "wait-for-dependencies"
          image = "busybox:1.36"
          command = [
            "sh", "-c",
            "i=0; while [ $i -lt 60 ]; do nc -z ${local.msk_first_broker_host} 9092 && break; i=$((i+1)); sleep 5; done; [ $i -ge 60 ] && echo 'Timed out waiting for Kafka' && exit 1; i=0; while [ $i -lt 60 ]; do nc -z ${local.dashboard_db.address} 5432 && exit 0; i=$((i+1)); sleep 5; done; echo 'Timed out waiting for dashboard-db'; exit 1"
          ]
        }

        container {
          name = "storage-consumer"
          image = "${local.ecr_repository_urls["storage-consumer"]}:latest"

          env {
            name = "DATABASE_URL"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "DASHBOARD_DATABASE_URL"
              }
            }
          }

          env {
            name = "KAFKA_BOOTSTRAP_SERVERS"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map_v1.app_config.metadata[0].name
                key = "KAFKA_BOOTSTRAP_SERVERS"
              }
            }
          }

          env {
            name = "KAFKA_GROUP_ID"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map_v1.app_config.metadata[0].name
                key = "KAFKA_GROUP_ID"
              }
            }
          }

          resources {
            requests = {
              memory = "128Mi"
              cpu = "100m"
            }
            limits = {
              memory = "256Mi"
              cpu = "250m"
            }
          }
        }
      }
    }
  }

  depends_on = [kubernetes_job_v1.db_init]
}

# Deployment + Service — Dashboard
resource "kubernetes_deployment_v1" "dashboard" {
  metadata {
    name = "dashboard"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
    labels = {
      app = "dashboard"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "dashboard"
      }
    }

    template {
      metadata {
        labels = {
          app = "dashboard"
        }
      }

      spec {
        init_container {
          name = "wait-for-dashboard-db"
          image = "busybox:1.36"
          command = [
            "sh", "-c",
            "i=0; while [ $i -lt 60 ]; do nc -z ${local.dashboard_db.address} 5432 && exit 0; i=$((i+1)); sleep 5; done; echo 'Timed out waiting for dashboard-db'; exit 1"
          ]
        }

        container {
          name = "dashboard"
          image = "${local.ecr_repository_urls["dashboard"]}:latest"

          port {
            container_port = 8501
          }

          env {
            name = "DATABASE_URL"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.sanction_monitor.metadata[0].name
                key = "DASHBOARD_DATABASE_URL"
              }
            }
          }

          resources {
            requests = {
              memory = "256Mi"
              cpu = "100m"
            }
            limits = {
              memory = "512Mi"
              cpu = "250m"
            }
          }
        }
      }
    }
  }

  depends_on = [kubernetes_job_v1.db_init]
}

resource "kubernetes_service_v1" "dashboard" {
  metadata {
    name = "dashboard"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
  }

  spec {
    selector = {
      app = "dashboard"
    }

    port {
      port = 8501
      target_port = 8501
    }
  }
}

# Deployment + Service — Kafka UI
resource "kubernetes_deployment_v1" "kafka_ui" {
  metadata {
    name = "kafka-ui"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
    labels = {
      app = "kafka-ui"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "kafka-ui"
      }
    }

    template {
      metadata {
        labels = {
          app = "kafka-ui"
        }
      }

      spec {
        container {
          name = "kafka-ui"
          image = "provectuslabs/kafka-ui:latest"

          port {
            container_port = 8080
          }

          env {
            name = "KAFKA_CLUSTERS_0_NAME"
            value = "Sanction-Monitor"
          }

          env {
            name = "KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map_v1.app_config.metadata[0].name
                key = "KAFKA_BOOTSTRAP_SERVERS"
              }
            }
          }

          resources {
            requests = {
              memory = "256Mi"
              cpu = "100m"
            }
            limits = {
              memory = "512Mi"
              cpu = "250m"
            }
          }
        }
      }
    }
  }
}

resource "kubernetes_service_v1" "kafka_ui" {
  metadata {
    name = "kafka-ui"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
  }

  spec {
    selector = {
      app = "kafka-ui"
    }

    port {
      port = 8080
      target_port = 8080
    }
  }
}


# Ingress — ALB
resource "kubernetes_ingress_v1" "sanction_monitor" {
  metadata {
    name = "sanction-monitor-ingress"
    namespace = kubernetes_namespace_v1.sanction_monitor.metadata[0].name
    annotations = {
      "kubernetes.io/ingress.class" = "alb"
      "alb.ingress.kubernetes.io/scheme" = "internet-facing"
      "alb.ingress.kubernetes.io/target-type" = "ip"
      "alb.ingress.kubernetes.io/listen-ports" = "[{\"HTTP\": 80}]"
      "alb.ingress.kubernetes.io/healthcheck-path" = "/"
    }
  }

  spec {
    rule {
      http {
        path {
          path = "/start"
          path_type = "Prefix"
          backend {
            service {
              name = kubernetes_service_v1.transaction_generator.metadata[0].name
              port {
                number = 8080
              }
            }
          }
        }

        path {
          path = "/test-sequence"
          path_type = "Exact"
          backend {
            service {
              name = kubernetes_service_v1.transaction_generator.metadata[0].name
              port {
                number = 8080
              }
            }
          }
        }

        path {
          path = "/"
          path_type = "Prefix"
          backend {
            service {
              name = kubernetes_service_v1.dashboard.metadata[0].name
              port {
                number = 8501
              }
            }
          }
        }
      }
    }
  }

  depends_on = [helm_release.alb_controller]
}
