terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source = "hashicorp/kubernetes"
      version = "~> 2.35"
    }
    helm = {
      source = "hashicorp/helm"
      version = "~> 2.17"
    }
  }
}

provider "aws" {
  region = local.region
}

provider "kubernetes" {
  host = local.eks_endpoint
  cluster_ca_certificate = base64decode(local.eks_ca_data)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command = "aws"
    args = ["eks", "get-token", "--cluster-name", local.eks_cluster_name, "--region", local.region]
  }
}

provider "helm" {
  kubernetes {
    host = local.eks_endpoint
    cluster_ca_certificate = base64decode(local.eks_ca_data)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command = "aws"
      args = ["eks", "get-token", "--cluster-name", local.eks_cluster_name, "--region", local.region]
    }
  }
}
