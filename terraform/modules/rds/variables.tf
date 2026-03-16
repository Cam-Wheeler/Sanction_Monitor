variable "identifier" {
  description = "RDS instance identifier"
  type = string
}

variable "engine_version" {
  description = "PostgreSQL engine version"
  type = string
  default = "16"
}

variable "instance_class" {
  description = "RDS instance class"
  type = string
  default = "db.t3.micro"
}

variable "allocated_storage" {
  description = "Allocated storage in GB"
  type = number
  default = 20
}

variable "db_name" {
  description = "Name of the database to create"
  type = string
}

variable "username" {
  description = "Master username"
  type = string
}

variable "subnet_ids" {
  description = "Subnet IDs for the DB subnet group"
  type = list(string)
}

variable "security_group_ids" {
  description = "Security group IDs"
  type = list(string)
}

variable "tags" {
  description = "Tags to apply to all resources"
  type = map(string)
  default = {}
}
