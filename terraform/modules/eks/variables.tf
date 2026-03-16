variable "cluster_name" {
  description = "Name of the EKS cluster"
  type = string
}

variable "cluster_version" {
  description = "Kubernetes version"
  type = string
  default = "1.31"
}

variable "vpc_id" {
  description = "VPC ID"
  type = string
}

variable "subnet_ids" {
  description = "Subnet IDs for the EKS cluster"
  type = list(string)
}

variable "node_instance_types" {
  description = "EC2 instance types for the node group"
  type = list(string)
  default = ["t3.medium"]
}

variable "node_capacity_type" {
  description = "Capacity type: ON_DEMAND or SPOT"
  type = string
  default = "ON_DEMAND"
}

variable "node_min_size" {
  description = "Minimum number of nodes"
  type = number
  default = 1
}

variable "node_max_size" {
  description = "Maximum number of nodes"
  type = number
  default = 3
}

variable "node_desired_size" {
  description = "Desired number of nodes"
  type = number
  default = 2
}

variable "node_security_group_ids" {
  description = "Additional security group IDs for nodes"
  type = list(string)
  default = []
}

variable "tags" {
  description = "Tags to apply to all resources"
  type = map(string)
  default = {}
}
