module "eks" {
  source = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name = var.cluster_name
  cluster_version = var.cluster_version

  vpc_id = var.vpc_id
  subnet_ids = var.subnet_ids

  cluster_endpoint_public_access = true

  # Enable OIDC provider for IRSA
  enable_irsa = true

  # Cluster addons
  cluster_addons = {
    coredns = { most_recent = true }
    kube-proxy = { most_recent = true }
    vpc-cni = { most_recent = true }
  }

  # Managed node group
  eks_managed_node_groups = {
    general = {
      instance_types = var.node_instance_types
      capacity_type = var.node_capacity_type

      min_size = var.node_min_size
      max_size = var.node_max_size
      desired_size = var.node_desired_size

      additional_security_group_ids = var.node_security_group_ids
    }
  }

  # Allow the current caller to administer the cluster
  enable_cluster_creator_admin_permissions = true

  tags = var.tags
}
