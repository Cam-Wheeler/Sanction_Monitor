output "configure_kubectl" {
  value = "aws eks update-kubeconfig --name ${local.eks_cluster_name} --region ${local.region}"
}

output "namespace" {
  value = local.namespace
}
