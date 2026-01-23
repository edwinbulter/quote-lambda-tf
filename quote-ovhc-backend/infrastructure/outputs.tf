# Combined outputs for easy access

output "deployment_summary" {
  description = "Summary of deployed infrastructure"
  value = {
    compute_instance   = "Manual VM creation (D2-2 Discovery)"
    cost_estimate      = "~€5.50/month (VM + storage)"
    vm_setup           = "Manual (OVHcloud Manager)"
    storage_setup      = "Manual (OVHcloud Manager)"
    database_approach   = "In-memory with S3 persistence"
    next_steps         = "Create VM manually, then deploy Go application"
  }
}

output "next_steps" {
  description = "Next steps for deployment"
  value = [
    "1. ✅ Terraform infrastructure ready (minimal)",
    "2. 🔄 Create VM manually in OVHcloud Manager (see documentation)",
    "3. 🔄 Create Object Storage manually via OVHcloud Manager",
    "4. 🔄 Connect to VM via SSH and deploy Go application",
    "5. 🔄 Implement in-memory database with S3 persistence in Go",
    "6. 📦 Build and deploy Go application to VM",
    "7. 🧪 Test the API endpoints",
    "8. 📊 Set up monitoring and logging",
  ]
}

output "vm_manual_setup_guide" {
  description = "Guide for manual VM setup"
  value = [
    "1. Go to OVHcloud Manager: https://www.ovh.com/auth/",
    "2. Navigate to Public Cloud -> Your Project (69f598c73ece43c293f49860d94adac0)",
    "3. Click 'COMPUTE > Instances' in the left menu",
    "4. Click 'Create instance'",
    "5. Configure: Name: quote-backend-vm, Region: GRA, Flavor: D2-2 (Discovery)",
    "6. Choose Image: Ubuntu 22.04 LTS",
    "7. Network: Public mode",
    "8. SSH Key: Import your public key (quote-app-key)",
    "9. Click 'Create instance'",
    "10. Note the public IP address for SSH access",
  ]
}

output "vm_deployment_instructions" {
  description = "Instructions for deploying the Go application to the VM"
  value = [
    "1. Connect to VM: ssh root@YOUR_VM_IP",
    "2. Update system: apt update && apt upgrade -y",
    "3. Install Go: wget https://go.dev/dl/go1.21.0.linux-amd64.tar.gz && tar -C /usr/local -xzf go1.21.0.linux-amd64.tar.gz",
    "4. Set PATH: echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc && source ~/.bashrc",
    "5. Create app directory: mkdir /opt/quote-backend && cd /opt/quote-backend",
    "6. Upload your Go application files",
    "7. Build app: go build -o quote-backend",
    "8. Set environment variables for S3:",
    "   - S3_ENDPOINT: https://s3.gra.cloud.ovh.net",
    "   - S3_REGION: GRA",
    "   - S3_BUCKET: quote-storage",
    "   - S3_ACCESS_KEY: [from Object Storage setup]",
    "   - S3_SECRET_KEY: [from Object Storage setup]",
    "9. Run app: ./quote-backend",
    "10. Test API: curl http://localhost:8080/quotes",
  ]
}

output "object_storage_setup_instructions" {
  description = "Instructions for manual Object Storage setup"
  value = [
    "1. Go to OVHcloud Manager: https://www.ovh.com/auth/",
    "2. Navigate to Public Cloud -> Your Project (${var.project_id})",
    "3. Click on 'Object Storage' in the left menu",
    "4. Click 'Create Object Storage'",
    "5. Configure: Name: quote-storage, Region: GRA, Type: Public",
    "6. Add User: Create user 'quote-app-user' with Object Storage Operator role",
    "7. Generate S3 credentials (Access Key + Secret Key)",
    "8. Click 'Create'",
    "9. Verify container 'quote-storage' in My Containers tab"
  ]
}

output "s3_credentials_setup" {
  description = "Instructions for S3 credentials setup"
  value = [
    "1. Go to OVHcloud Manager: https://www.ovh.com/auth/",
    "2. Navigate to Public Cloud -> Your Project",
    "3. Click on 'Object Storage' -> 'quote-storage'",
    "4. Click on 'Users' tab",
    "5. Find user 'quote-app-user' and view S3 credentials",
    "6. Save Access Key and Secret Key for Go application"
  ]
}

output "in_memory_db_implementation_guide" {
  description = "Guide for implementing in-memory database with S3 persistence"
  value = [
    "1. Use Go maps for in-memory storage: map[string]Quote",
    "2. Use AWS SDK for Go with OVHcloud S3 endpoint",
    "3. Implement JSON serialization for S3 objects",
    "4. Save data to S3 on changes (key: quotes.json)",
    "5. Load data from S3 on startup",
    "6. Use S3 for backup and persistence"
  ]
}

output "cost_optimization_notes" {
  description = "Notes about cost optimization"
  value = {
    instance_type    = "Discovery (D2-2) - shared resources"
    monthly_cost      = "~€5.49/month for VM"
    storage_cost      = "~€0.01/month for Object Storage"
    total_cost        = "~€5.50/month"
    competitive_with  = "AWS Lambda (€1.80-4.50/month)"
    tradeoffs         = "Shared resources, 99.95% SLA, no resizing"
    setup_method      = "Manual VM creation (avoids Terraform complexity)"
  }
}
