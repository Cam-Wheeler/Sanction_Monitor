terraform {
  backend "s3" {
    bucket = "sanction-monitor-tf-state"
    key = "app/terraform.tfstate"
    region = "eu-west-2"
    dynamodb_table = "sanction-monitor-tf-lock"
    encrypt = true
  }
}
