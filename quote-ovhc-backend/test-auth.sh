#!/bin/bash

echo "🚀 Testing JWT Authentication Implementation"
echo "=========================================="

# Start server in background
echo "Starting server..."
./quote-backend > server.log 2>&1 &
SERVER_PID=$!

# Wait for server to start
sleep 5

echo "Testing health endpoint..."
curl -s http://localhost:8080/health || echo "❌ Health check failed"

echo -e "\nTesting register endpoint..."
REGISTER_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -d '{"username":"testuser","email":"test@example.com","password":"test123"}' http://localhost:8080/api/v1/auth/register)
echo "Register response: $REGISTER_RESPONSE"

echo -e "\nTesting login endpoint..."
LOGIN_RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" -d '{"username":"testuser","password":"test123"}' http://localhost:8080/api/v1/auth/login)
echo "Login response: $LOGIN_RESPONSE"

echo -e "\nTesting seed admin endpoint..."
SEED_RESPONSE=$(curl -s "http://localhost:8080/api/v1/seed-users?code=YOUR_MASTER_KEY_HERE&username=admin&email=admin@example.com&password=admin123")
echo "Seed admin response: $SEED_RESPONSE"

# Clean up
echo -e "\n🛑 Stopping server..."
kill $SERVER_PID 2>/dev/null || true

echo "✅ Test completed! Check server.log for details."
