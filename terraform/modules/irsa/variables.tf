variable "role_name" {
  description = "Name of the IAM role"
  type = string
}

variable "oidc_provider_arn" {
  description = "ARN of the EKS OIDC provider"
  type = string
}

variable "oidc_provider" {
  description = "OIDC provider URL (without https://)"
  type = string
}

variable "namespace" {
  description = "Kubernetes namespace"
  type = string
}

variable "service_account_name" {
  description = "Kubernetes service account name"
  type = string
}

variable "policy_arns" {
  description = "List of IAM policy ARNs to attach to the role"
  type = list(string)
}

variable "tags" {
  description = "Tags to apply to all resources"
  type = map(string)
  default = {}
}
