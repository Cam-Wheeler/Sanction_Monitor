resource "random_password" "this" {
  length = 24
  special = false
}

resource "aws_db_subnet_group" "this" {
  name = "${var.identifier}-subnet-group"
  subnet_ids = var.subnet_ids

  tags = var.tags
}

resource "aws_db_instance" "this" {
  identifier = var.identifier

  engine = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage = var.allocated_storage
  storage_type = "gp3"

  db_name = var.db_name
  username = var.username
  password = random_password.this.result

  db_subnet_group_name = aws_db_subnet_group.this.name
  vpc_security_group_ids = var.security_group_ids

  publicly_accessible = false
  multi_az = false
  skip_final_snapshot = true

  tags = var.tags
}
