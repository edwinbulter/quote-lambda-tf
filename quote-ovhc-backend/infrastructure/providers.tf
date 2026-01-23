terraform {
  required_providers {
    ovh = {
      source  = "ovh/ovh"
      version = "~> 0.42.0"
    }
  }
}

provider "ovh" {
  endpoint = "ovh-eu"
  application_key    = var.ovh_application_key
  application_secret = var.ovh_application_secret
  consumer_key       = var.ovh_consumer_key
}

# Note: OpenStack provider removed - VM created manually in OVHcloud Manager
# This avoids authentication issues and provides better control over VM setup
