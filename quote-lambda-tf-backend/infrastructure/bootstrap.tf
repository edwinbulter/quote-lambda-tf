# DEPRECATED: Bootstrap resources have been moved to a shared configuration
# 
# This file is kept for reference only and will be removed in a future cleanup.
# The bootstrap resources (S3 bucket and DynamoDB table for Terraform state)
# are now managed centrally in:
#   ../quote-lambda-tf-frontend/infrastructure/bootstrap/
#
# All projects now share the same S3 bucket (edwinbulter-terraform-state)
# with different state keys for isolation.
#
# See: ../quote-lambda-tf-frontend/infrastructure/bootstrap/README.md
