output "repository_urls" {
  description = "Map of repository name to URL"
  value = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}

output "registry_id" {
  description = "The registry ID (AWS account ID)"
  value = length(aws_ecr_repository.this) > 0 ? values(aws_ecr_repository.this)[0].registry_id : null
}
