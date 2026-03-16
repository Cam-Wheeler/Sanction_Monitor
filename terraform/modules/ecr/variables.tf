variable "prefix" {
  description = "Prefix for ECR repository names"
  type = string
  default = "sanction-monitor"
}

variable "repository_names" {
  description = "List of repository names to create"
  type = list(string)
}

variable "max_image_count" {
  description = "Maximum number of images to keep per repository"
  type = number
  default = 5
}

variable "force_delete" {
  description = "Force delete repositories even if they contain images"
  type = bool
  default = true
}

variable "tags" {
  description = "Tags to apply to all resources"
  type = map(string)
  default = {}
}
