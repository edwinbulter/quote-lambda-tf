# Bootstrap uses local state
# This is intentional - the bootstrap creates the remote state infrastructure
terraform {
  backend "local" {
    path = "terraform.tfstate"
  }
}
