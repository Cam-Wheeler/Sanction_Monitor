resource "aws_msk_configuration" "this" {
  name = "${var.cluster_name}-config"
  kafka_versions = [var.kafka_version]
  server_properties = <<-PROPERTIES
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    num.partitions=3
    log.retention.hours=72
  PROPERTIES
}

resource "aws_msk_cluster" "this" {
  cluster_name = var.cluster_name
  kafka_version = var.kafka_version
  number_of_broker_nodes = var.broker_count

  configuration_info {
    arn = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }

  broker_node_group_info {
    instance_type = var.broker_instance_type
    client_subnets = var.subnet_ids
    security_groups = var.security_group_ids

    storage_info {
      ebs_storage_info {
        volume_size = var.ebs_volume_size
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS_PLAINTEXT"
      in_cluster = true
    }
  }

  tags = var.tags
}
