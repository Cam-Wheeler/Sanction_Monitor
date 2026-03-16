variable "region" {
  description = "AWS region"
  type = string
  default = "eu-west-2"
}

variable "state_bucket_name" {
  description = "Name of the S3 bucket for Terraform state"
  type = string
  default = "sanction-monitor-tf-state"
}

variable "lock_table_name" {
  description = "Name of the DynamoDB table for state locking"
  type = string
  default = "sanction-monitor-tf-lock"
}
