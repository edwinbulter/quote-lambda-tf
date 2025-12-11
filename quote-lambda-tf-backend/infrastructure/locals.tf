# Local values that derive from the Terraform workspace
locals {
  # Automatically set environment based on workspace
  # "default" workspace maps to "prod", all others use their workspace name
  environment = terraform.workspace == "default" ? "prod" : terraform.workspace
}
