# HTTPS Setup with Nginx Reverse Proxy + Let's Encrypt

## Overview

This document describes how to set up HTTPS for the Quote Backend using Nginx as a reverse proxy and Let's Encrypt for SSL certificates.

## Table of Contents

- [Architecture](#architecture)
- [Components](#components)
  - [Nginx Reverse Proxy](#1-nginx-reverse-proxy)
  - [Let's Encrypt (Certbot)](#2-let-encrypt-certbot)
  - [FreeDNS (afraid.org)](#3-freedns-afraidorg)
- [FreeDNS Setup Guide](#freedns-setup-guide)
  - [Overview](#overview-1)
  - [Step-by-Step FreeDNS Registration](#step-by-step-freedns-registration)
  - [Example FreeDNS Configuration](#example-freedns-configuration)
  - [Resulting Domain](#resulting-domain)
  - [FreeDNS Maintenance](#freedns-maintenance)
  - [Benefits of FreeDNS](#benefits-of-freedns)
- [Setup Instructions](#setup-instructions)
  - [Prerequisites](#prerequisites)
  - [Step 1: Install Nginx](#step-1-install-nginx)
  - [Step 2: Configure Nginx for Quote Backend](#step-2-configure-nginx-for-quote-backend)
  - [Step 3: Enable the Site](#step-3-enable-the-site)
  - [Step 4: Install Certbot for Let's Encrypt](#step-4-install-certbot-for-lets-encrypt)
  - [Step 5: Obtain SSL Certificate](#step-5-obtain-ssl-certificate)
  - [Step 6: Verify HTTPS Setup](#step-6-verify-https-setup)
- [Configuration Details](#configuration-details)
  - [Nginx Configuration Explained](#nginx-configuration-explained)
  - [After Let's Encrypt Setup](#after-lets-encrypt-setup)
- [Access URLs](#access-urls)
- [API Endpoints (HTTPS)](#api-endpoints-https)
- [Maintenance](#maintenance)
  - [Certificate Renewal](#certificate-renewal)
  - [Nginx Management](#nginx-management)
- [Troubleshooting](#troubleshooting)
  - [Common Issues](#common-issues)
- [Security Considerations](#security-considerations)
  - [Firewall Configuration](#firewall-configuration)
  - [Security Headers (Optional)](#security-headers-optional)
- [Automation](#automation)
  - [GitHub Actions Integration](#github-actions-integration)
- [Cost Analysis](#cost-analysis)
- [Recommendations](#recommendations)
- [Conclusion](#conclusion)

## Architecture

```
┌─────────────────┐
│   Internet      │
└─────────┬───────┘
          │ HTTPS (443)
          ▼
┌─────────────────┐
│   Nginx         │
│   Reverse Proxy │
│   (Port 80/443) │
└─────────┬───────┘
          │ HTTP (8080)
          ▼
┌─────────────────┐
│   Quote Backend │
│   Go Application│
│   (Port 8080)   │
└─────────────────┘
```

## Components

### 1. Nginx Reverse Proxy
- **Purpose**: Routes external traffic to the backend application
- **Ports**: Listens on 80 (HTTP) and 443 (HTTPS)
- **Backend**: Forwards to localhost:8080 (Quote Backend)

### 2. Let's Encrypt (Certbot)
- **Purpose**: Provides free SSL certificates
- **Automation**: Auto-renews certificates
- **Integration**: Works seamlessly with Nginx

### 3. FreeDNS (afraid.org)
- **Purpose**: Provides free subdomain names
- **Cost**: Completely free
- **Integration**: Works with Let's Encrypt for SSL certificates

## FreeDNS Setup Guide

### Overview

FreeDNS (afraid.org) provides free subdomain names that can point to your OVHcloud VM IP address. This allows you to have a proper domain name for HTTPS setup without purchasing a domain.

### Step-by-Step FreeDNS Registration

#### 1. Create FreeDNS Account
1. **Go to**: https://freedns.afraid.org/
2. **Click "Registry"** in the top menu
3. **Click "Sign up"** for a free account
4. **Fill in registration**:
   - Username (choose something memorable)
   - Email address (must be valid for verification)
   - Password
5. **Check your email** and verify your account

#### 2. Choose a Free Subdomain
1. **Log in** to your FreeDNS account
2. **Click "Registry"** in the top menu
3. **Click "Subdomains"**
4. **Browse available public domains** - you'll see hundreds like:
   - `mooo.com`
   - `ddns.net`
   - `servebeer.com`
   - `ignorelist.com`
   - And many more...

#### 3. Register Your Subdomain
1. **Find a domain you like** (e.g., `mooo.com`)
2. **Click the domain** to see available subdomains
3. **Choose your subdomain name** (e.g., `quote-ovhc-backend`)
4. **Click "Sign up"** next to your choice
5. **Select your domain** from the dropdown
6. **Click "Save"**

#### 4. Configure DNS A Record
1. **Go to "Subdomains"** in your FreeDNS account
2. **Find your subdomain** and click "Modify"
3. **Set the destination**:
   ```
   Type: A
   Destination: 51.255.60.246
   Wildcard: ❌ (unchecked - recommended)
   ```
4. **Click "Save"**

#### 5. Important: Wildcard Setting
- **Do NOT check the wildcard checkbox** for your Quote Backend
- **Wildcard enabled**: `*.quote-ovhc-backend.mooo.com → 51.255.60.246` (not recommended)
- **Wildcard disabled**: `quote-ovhc-backend.mooo.com → 51.255.60.246` (recommended)

#### 6. Test DNS Resolution
Wait 5-10 minutes for DNS propagation, then test:

```bash
# Test your new domain
nslookup quote-ovhc-backend.mooo.com

# Should return:
# Server:  [your DNS server]
# Address: 51.255.60.246

# Or use dig
dig quote-ovhc-backend.mooo.com +short
# Should return: 51.255.60.246
```

### Example FreeDNS Configuration

| Field | Value | Description |
|-------|-------|-------------|
| **Type** | A | DNS record type for IP address |
| **Subdomain** | quote-ovhc-backend | Your chosen subdomain name |
| **Domain** | mooo.com | FreeDNS public domain |
| **Destination** | 51.255.60.246 | Your OVHcloud VM IP address |
| **Wildcard** | ❌ (unchecked) | Only this specific subdomain |

### Resulting Domain

After setup, you'll have:
- **Free domain**: `quote-ovhc-backend.mooo.com`
- **Points to**: `51.255.60.246`
- **Cost**: Free
- **SSL ready**: Works with Let's Encrypt

### FreeDNS Maintenance

#### Account Requirements
- **Keep account active** - Login every few months
- **Valid email required** - For verification and recovery
- **Personal use only** - No commercial use allowed

#### Popular FreeDNS Domains
| Domain | Example | Notes |
|--------|---------|-------|
| `mooo.com` | `quote-ovhc-backend.mooo.com` | Very popular, reliable |
| `ddns.net` | `quote-ovhc-backend.ddns.net` | Dynamic DNS focus |
| `servebeer.com` | `quote-ovhc-backend.servebeer.com` | Fun name |
| `ignorelist.com` | `quote-ovhc-backend.ignorelist.com` | Tech-focused |

### Benefits of FreeDNS

- ✅ **Completely free** - No cost at all
- ✅ **Real domain name** - Works with Let's Encrypt
- ✅ **HTTPS support** - Full SSL certificates
- ✅ **Professional appearance** - Better than IP address
- ✅ **Easy setup** - No technical expertise needed
- ✅ **Multiple options** - Hundreds of public domains available

## Setup Instructions

### Prerequisites
- Ubuntu 22.04 VM
- Quote Backend running on port 8080
- Domain name (paid) OR FreeDNS subdomain (free)
- SSH access to the VM

### Step 1: Install Nginx
```bash
# Update package list
sudo apt update

# Install Nginx
sudo apt install nginx -y

# Start and enable Nginx
sudo systemctl start nginx
sudo systemctl enable nginx
```

### Step 2: Configure Nginx for Quote Backend

Create the Nginx configuration file:
```bash
sudo nano /etc/nginx/sites-available/quote-backend
```

Add the following configuration:
```nginx
server {
    listen 80;
    server_name 51.255.60.246;  # Replace with your domain if available
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Step 3: Enable the Site
```bash
# Create symbolic link to enable the site
sudo ln -s /etc/nginx/sites-available/quote-backend /etc/nginx/sites-enabled/

# Remove default site (optional)
sudo rm -f /etc/nginx/sites-enabled/default

# Test Nginx configuration
sudo nginx -t

# Restart Nginx
sudo systemctl restart nginx
```

### Step 4: Install Certbot for Let's Encrypt
```bash
# Install Certbot and Nginx plugin
sudo apt install certbot python3-certbot-nginx -y
```

### Step 5: Obtain SSL Certificate

#### Option A: With Domain Name (Recommended)
```bash
# Replace your-domain.com with your actual domain
sudo certbot --nginx -d your-domain.com
```

#### Option B: With IP Address (Limited)
```bash
# Note: Let's Encrypt typically requires a domain name
# This may not work with IP addresses only
sudo certbot --nginx --non-interactive --agree-tos --email admin@51.255.60.246 -d 51.255.60.246
```

### Step 6: Verify HTTPS Setup
```bash
# Test HTTP (should redirect to HTTPS)
curl -I http://51.255.60.246/health

# Test HTTPS
curl -I https://51.255.60.246/health
```

## Configuration Details

### Nginx Configuration Explained

```nginx
server {
    listen 80;                           # Listen for HTTP traffic
    server_name 51.255.60.246;          # Your domain or IP
    
    location / {
        proxy_pass http://localhost:8080;  # Forward to backend
        proxy_set_header Host $host;        # Pass original host
        proxy_set_header X-Real-IP $remote_addr;      # Pass real IP
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;  # Pass forwarded IP
        proxy_set_header X-Forwarded-Proto $scheme;   # Pass protocol (http/https)
    }
}
```

### After Let's Encrypt Setup

Certbot automatically updates your Nginx configuration to include SSL:

```nginx
server {
    server_name 51.255.60.246;
    root /var/www/html;
    index index.html index.htm index.nginx-debian.html;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    listen 443 ssl; # managed by Certbot
    ssl_certificate /etc/letsencrypt/live/51.255.60.246/fullchain.pem; # managed by Certbot
    ssl_certificate_key /etc/letsencrypt/live/51.255.60.246/privkey.pem; # managed by Certbot
    include /etc/letsencrypt/options-ssl-nginx.conf; # managed by Certbot
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem; # managed by Certbot
}
```

## Access URLs

| Protocol | URL | Port | Description |
|----------|-----|------|-------------|
| HTTP | `http://51.255.60.246` | 80 | Redirects to HTTPS |
| HTTPS | `https://51.255.60.246` | 443 | Secure access |
| Direct | `http://51.255.60.246:8080` | 8080 | Backend direct access |

## API Endpoints (HTTPS)

```bash
# Health check
curl https://51.255.60.246/health

# Get random quote
curl https://51.255.60.246/quote

# Get quote with exclusions
curl -X POST https://51.255.60.246/quote \
  -H "Content-Type: application/json" \
  -d "[1, 2, 3, 4, 5]"

# Debug endpoints
curl https://51.255.60.246/debug/quotes
curl https://51.255.60.246/debug/sql?query=SELECT%20*%20FROM%20quotes%20LIMIT%205
```

## Maintenance

### Certificate Renewal
Let's Encrypt certificates expire every 90 days. Certbot sets up automatic renewal:

```bash
# Check renewal timer
sudo systemctl list-timers | grep certbot

# Test renewal process
sudo certbot renew --dry-run
```

### Nginx Management
```bash
# Check Nginx status
sudo systemctl status nginx

# Test configuration
sudo nginx -t

# Reload configuration
sudo systemctl reload nginx

# View logs
sudo journalctl -u nginx -f
```

## Troubleshooting

### Common Issues

#### 1. SSL Certificate Not Obtained
**Problem**: Let's Encrypt requires a domain name, not just an IP address.
**Solution**: Use a domain name or consider self-signed certificates for IP-only access.

#### 2. 502 Bad Gateway
**Problem**: Nginx can't reach the backend.
**Solution**: Ensure Quote Backend is running on port 8080.

```bash
# Check if backend is running
sudo systemctl status quote-backend

# Check if port 8080 is listening
sudo netstat -tlnp | grep 8080
```

#### 3. Connection Refused
**Problem**: Nginx is not running or not listening on the correct ports.
**Solution**: Check Nginx status and configuration.

```bash
# Check Nginx status
sudo systemctl status nginx

# Check listening ports
sudo netstat -tlnp | grep nginx
```

#### 4. Certificate Renewal Fails
**Problem**: Automatic renewal not working.
**Solution**: Manually renew and check timer.

```bash
# Manual renewal
sudo certbot renew

# Check timer
sudo systemctl status certbot.timer
```

## Security Considerations

### 1. Firewall Configuration
```bash
# Allow HTTP and HTTPS
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Allow SSH (if not already allowed)
sudo ufw allow 22/tcp

# Enable firewall
sudo ufw enable
```

### 2. Security Headers (Optional)
Add security headers to Nginx configuration:

```nginx
server {
    # ... existing configuration ...

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;
    add_header Content-Security-Policy "default-src 'self' http: https: data: blob: 'unsafe-inline'" always;
}
```

## Automation

### GitHub Actions Integration

The HTTPS setup is automatically included in the GitHub Actions deployment workflow:

```yaml
# Setup HTTPS with Nginx and Let's Encrypt
- name: Setup HTTPS
  run: |
    # Install Nginx and Certbot
    sudo apt update
    sudo apt install nginx certbot python3-certbot-nginx -y
    
    # Configure Nginx
    # ... (configuration steps) ...
    
    # Obtain SSL certificate
    sudo certbot --nginx --non-interactive --agree-tos --email admin@51.255.60.246 -d 51.255.60.246
```

## Cost Analysis

### Free Option (Current Setup)
- **Nginx**: Free
- **Let's Encrypt**: Free
- **VM**: Existing cost
- **Total**: No additional cost

### Paid Alternative (OVH Load Balancer)
- **OVH Load Balancer**: €10-15/month
- **SSL Certificate**: Free (included)
- **DDoS Protection**: Included
- **High Availability**: Included

## Recommendations

### For Development/Testing
- ✅ **Use Nginx + Let's Encrypt** (free)
- ✅ **Good for learning and testing**
- ✅ **Full control over configuration**

### For Production
- 🤔 **Consider OVH Load Balancer** if budget allows
- ✅ **Better reliability and DDoS protection**
- ✅ **Managed service (less maintenance)**

## Conclusion

The Nginx reverse proxy + Let's Encrypt setup provides:
- ✅ **Free HTTPS** for your Quote Backend
- ✅ **Professional appearance** with SSL certificate
- ✅ **Better security** with encrypted traffic
- ✅ **Flexible configuration** options
- ✅ **Automated deployment** via GitHub Actions

This setup is perfect for development, testing, and small production deployments. For larger production workloads, consider upgrading to OVH's managed Load Balancer service.
