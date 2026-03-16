output "endpoint" {
  description = "RDS instance endpoint (host:port)"
  value = aws_db_instance.this.endpoint
}

output "address" {
  description = "RDS instance hostname"
  value = aws_db_instance.this.address
}

output "port" {
  description = "RDS instance port"
  value = aws_db_instance.this.port
}

output "db_name" {
  value = aws_db_instance.this.db_name
}

output "username" {
  value = aws_db_instance.this.username
}

output "password" {
  value = random_password.this.result
  sensitive = true
}
