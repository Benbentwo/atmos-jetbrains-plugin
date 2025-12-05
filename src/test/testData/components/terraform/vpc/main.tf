# VPC Terraform Component
module "vpc" {
  source  = "cloudposse/vpc/aws"
  version = "2.0.0"

  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = var.enable_dns_hostnames
  enable_dns_support   = var.enable_dns_support

  context = module.this.context
}

module "subnets" {
  source  = "cloudposse/dynamic-subnets/aws"
  version = "2.0.0"

  availability_zones   = var.availability_zones
  vpc_id               = module.vpc.vpc_id
  igw_id               = [module.vpc.igw_id]
  nat_gateway_enabled  = var.enable_nat_gateway
  single_nat_gateway   = var.single_nat_gateway

  context = module.this.context
}
