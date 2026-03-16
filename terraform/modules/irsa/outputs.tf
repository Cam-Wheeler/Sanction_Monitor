output "role_arn" {
  description = "ARN of the IAM role for the service account"
  value       = aws_iam_role.this.arn
}

output "role_name" {
  value = aws_iam_role.this.name
}
