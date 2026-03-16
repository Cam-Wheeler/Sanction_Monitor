# Remote State — Layer 1 Infrastructure
data "terraform_remote_state" "infra" {
  backend = "s3"
  config = {
    bucket = "sanction-monitor-tf-state"
    key = "infra/terraform.tfstate"
    region = "eu-west-2"
  }
}

# Locals
locals {
  namespace = "sanction-monitor"

  # Infrastructure outputs
  region = data.terraform_remote_state.infra.outputs.region
  eks_endpoint = data.terraform_remote_state.infra.outputs.eks_cluster_endpoint
  eks_cluster_name = data.terraform_remote_state.infra.outputs.eks_cluster_name
  eks_ca_data = data.terraform_remote_state.infra.outputs.eks_cluster_ca_data
  vpc_id = data.terraform_remote_state.infra.outputs.vpc_id
  alb_role_arn = data.terraform_remote_state.infra.outputs.alb_controller_role_arn

  # ECR
  ecr_repository_urls = data.terraform_remote_state.infra.outputs.ecr_repository_urls

  # MSK
  msk_brokers = data.terraform_remote_state.infra.outputs.msk_bootstrap_brokers
  msk_first_broker_host = split(":", split(",", local.msk_brokers)[0])[0]

  # RDS
  sanctions_db = data.terraform_remote_state.infra.outputs.sanctions_db
  dashboard_db = data.terraform_remote_state.infra.outputs.dashboard_db

  sanctions_db_url = "postgres://${local.sanctions_db.username}:${local.sanctions_db.password}@${local.sanctions_db.address}:5432/${local.sanctions_db.db_name}"
  dashboard_db_url = "postgresql://${local.dashboard_db.username}:${local.dashboard_db.password}@${local.dashboard_db.address}:5432/${local.dashboard_db.db_name}"
}
