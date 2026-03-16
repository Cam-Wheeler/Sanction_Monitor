locals {
  tags = {
    Project = var.project_name
    Environment = var.environment
    ManagedBy = "terraform"
  }

  cluster_name = var.project_name
  azs = ["${var.region}a", "${var.region}b", "${var.region}c"]
}

#  Stuff for the VPC
module "vpc" {
  source = "../../modules/vpc"

  name = "${var.project_name}-vpc"
  cidr = "10.0.0.0/16"
  azs = local.azs
  cluster_name = local.cluster_name

  public_subnets  = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  private_subnets = ["10.0.10.0/24", "10.0.20.0/24", "10.0.30.0/24"]

  tags = local.tags
}

# Security Groups
resource "aws_security_group" "eks_nodes" {
  name_prefix = "${var.project_name}-eks-nodes-"
  description = "Additional SG for EKS nodes"
  vpc_id = module.vpc.vpc_id

  # Allow all traffic within the security group (pod-to-pod)
  ingress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    self = true
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.tags
}

resource "aws_security_group" "msk" {
  name_prefix = "${var.project_name}-msk-"
  description = "Security group for MSK brokers"
  vpc_id = module.vpc.vpc_id

  ingress {
    description = "Kafka from EKS nodes"
    from_port = 9092
    to_port = 9092
    protocol = "tcp"
    security_groups = [aws_security_group.eks_nodes.id, module.eks.node_security_group_id]
  }

  # Also allow plaintext on 9094 (MSK default for PLAINTEXT listener)
  ingress {
    description = "Kafka plaintext from EKS nodes"
    from_port = 9094
    to_port = 9094
    protocol = "tcp"
    security_groups = [aws_security_group.eks_nodes.id, module.eks.node_security_group_id]
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.tags
}

resource "aws_security_group" "rds_sanctions" {
  name_prefix = "${var.project_name}-rds-sanctions-"
  description = "Security group for sanctions RDS"
  vpc_id = module.vpc.vpc_id

  ingress {
    description = "PostgreSQL from EKS nodes"
    from_port = 5432
    to_port = 5432
    protocol = "tcp"
    security_groups = [aws_security_group.eks_nodes.id, module.eks.node_security_group_id]
  }

  tags = local.tags
}

resource "aws_security_group" "rds_dashboard" {
  name_prefix = "${var.project_name}-rds-dashboard-"
  description = "Security group for dashboard RDS"
  vpc_id = module.vpc.vpc_id

  ingress {
    description = "PostgreSQL from EKS nodes"
    from_port = 5432
    to_port = 5432
    protocol = "tcp"
    security_groups = [aws_security_group.eks_nodes.id, module.eks.node_security_group_id]
  }

  tags = local.tags
}

# EKS
module "eks" {
  source = "../../modules/eks"

  cluster_name = local.cluster_name
  vpc_id = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnet_ids
  node_instance_types = ["t3.medium"]
  node_desired_size = 2
  node_min_size = 1
  node_max_size = 3

  node_security_group_ids = [aws_security_group.eks_nodes.id]

  tags = local.tags
}

# ECR
module "ecr" {
  source = "../../modules/ecr"

  repository_names = [
    "transaction-generator",
    "transaction-filter",
    "transaction-analyser",
    "storage-consumer",
    "dashboard",
    "db-init",
  ]

  tags = local.tags
}

# MSK (Kafka)
module "msk" {
  source = "../../modules/msk"

  cluster_name = "${var.project_name}-kafka"
  kafka_version = "3.7.x"
  broker_count = 3
  broker_instance_type = "kafka.t3.small"
  ebs_volume_size = 20
  subnet_ids = module.vpc.private_subnet_ids
  security_group_ids = [aws_security_group.msk.id]

  tags = local.tags
}

# RDS — Sanctions DB
module "rds_sanctions" {
  source = "../../modules/rds"

  identifier = "${var.project_name}-sanctions-db"
  db_name = "sanctions"
  username = "sanctions_user"
  instance_class = "db.t3.micro"
  allocated_storage = 20
  subnet_ids = module.vpc.private_subnet_ids
  security_group_ids = [aws_security_group.rds_sanctions.id]

  tags = local.tags
}

# RDS — Dashboard DB
module "rds_dashboard" {
  source = "../../modules/rds"

  identifier = "${var.project_name}-dashboard-db"
  db_name = "dashboard"
  username = "dashboard_user"
  instance_class = "db.t3.micro"
  allocated_storage = 20
  subnet_ids = module.vpc.private_subnet_ids
  security_group_ids = [aws_security_group.rds_dashboard.id]

  tags = local.tags
}


# IRSA — AWS Load Balancer Controller
resource "aws_iam_policy" "alb_controller" {
  name        = "${var.project_name}-alb-controller"
  description = "IAM policy for AWS Load Balancer Controller"

  # This is a simplified policy; the full policy is available at:
  # https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/main/docs/install/iam_policy.json
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "elasticloadbalancing:*",
          "ec2:Describe*",
          "ec2:AuthorizeSecurityGroupIngress",
          "ec2:RevokeSecurityGroupIngress",
          "ec2:CreateSecurityGroup",
          "ec2:DeleteSecurityGroup",
          "ec2:CreateTags",
          "ec2:DeleteTags",
          "iam:CreateServiceLinkedRole",
          "cognito-idp:DescribeUserPoolClient",
          "acm:ListCertificates",
          "acm:DescribeCertificate",
          "waf-regional:*",
          "wafv2:*",
          "shield:*",
          "tag:GetResources",
          "tag:TagResources",
        ]
        Resource = ["*"]
      },
    ]
  })

  tags = local.tags
}

module "irsa_alb_controller" {
  source = "../../modules/irsa"

  role_name = "${var.project_name}-alb-controller"
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_provider = module.eks.oidc_provider
  namespace = "kube-system"
  service_account_name = "aws-load-balancer-controller"
  policy_arns = [aws_iam_policy.alb_controller.arn]

  tags = local.tags
}
