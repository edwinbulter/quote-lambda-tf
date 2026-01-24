.PHONY: build run clean test

# Build the application
build:
	go build -o quote-backend ./cmd/quote-backend

# Run the application
run:
	go run ./cmd/quote-backend

# Build and run
start: build
	./quote-backend

# Clean build artifacts
clean:
	rm -f quote-backend
	rm -f quotes.db

# Run tests
test:
	go test ./...

# Development with auto-reload (requires 'air' tool)
dev:
	air -c .air.toml
