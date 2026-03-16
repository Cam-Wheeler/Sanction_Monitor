variable "cluster_name" {
  description = "Name of the MSK cluster"
  type = string
}

variable "kafka_version" {
  description = "Apache Kafka version"
  type = string
  default = "3.7.x"
}

variable "broker_count" {
  description = "Number of broker nodes (must be multiple of AZ count)"
  type = number
  default = 3
}

variable "broker_instance_type" {
  description = "EC2 instance type for brokers"
  type = string
  default = "kafka.t3.small"
}

variable "ebs_volume_size" {
  description = "EBS volume size in GB per broker"
  type = number
  default = 20
}

variable "subnet_ids" {
  description = "Subnet IDs for broker placement (one per AZ)"
  type = list(string)
}

variable "security_group_ids" {
  description = "Security group IDs for the brokers"
  type = list(string)
}

variable "tags" {
  description = "Tags to apply to all resources"
  type = map(string)
  default = {}
}
