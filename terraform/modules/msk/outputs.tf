output "bootstrap_brokers_plaintext" {
  description = "Plaintext bootstrap broker connection string"
  value       = aws_msk_cluster.this.bootstrap_brokers
}

output "bootstrap_brokers_tls" {
  description = "TLS bootstrap broker connection string"
  value       = aws_msk_cluster.this.bootstrap_brokers_tls
}

output "cluster_arn" {
  value = aws_msk_cluster.this.arn
}

output "zookeeper_connect_string" {
  value = aws_msk_cluster.this.zookeeper_connect_string
}
