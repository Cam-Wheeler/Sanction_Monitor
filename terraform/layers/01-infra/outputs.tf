# ── Region ──
output "region" {
  value = var.region
}

# ── VPC ──
output "vpc_id" {
  value = module.vpc.vpc_id
}

# ── EKS ──
output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "eks_cluster_ca_data" {
  value = module.eks.cluster_certificate_authority_data
}

output "configure_kubectl" {
  value = "aws eks update-kubeconfig --name ${module.eks.cluster_name} --region ${var.region}"
}

# ── ECR ──
output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "ecr_registry" {
  description = "ECR registry hostname for docker login and image tagging"
  value = split("/", values(module.ecr.repository_urls)[0])[0]
}

# ── MSK ──
output "msk_bootstrap_brokers" {
  value = module.msk.bootstrap_brokers_plaintext
}

# ── RDS — Sanctions DB ──
output "sanctions_db" {
  description = "Sanctions database connection details"
  value = {
    address = module.rds_sanctions.address
    port = module.rds_sanctions.port
    db_name = module.rds_sanctions.db_name
    username = module.rds_sanctions.username
    password = module.rds_sanctions.password
  }
  sensitive = true
}

# ── RDS — Dashboard DB ──
output "dashboard_db" {
  description = "Dashboard database connection details"
  value = {
    address = module.rds_dashboard.address
    port = module.rds_dashboard.port
    db_name = module.rds_dashboard.db_name
    username = module.rds_dashboard.username
    password = module.rds_dashboard.password
  }
  sensitive = true
}

# ── IRSA ──
output "alb_controller_role_arn" {
  value = module.irsa_alb_controller.role_arn
}
