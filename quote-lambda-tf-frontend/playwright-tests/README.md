# Playwright Tests

## Overview

This directory contains end-to-end tests for the Quote application using Playwright.

## Test Files

- **`open-screen.spec.ts`** - Tests initial quote display
- **`click-new-quote.spec.ts`** - Tests fetching new quotes
- **`click-like.spec.ts`** - Tests like button functionality (requires authentication)

## Running Tests

```bash
# Run all tests
npm run test:e2e

# Run tests in headed mode (see browser)
npm run test:e2e -- --headed

# Run specific test file
npm run test:e2e -- click-new-quote.spec.ts
```

## Authentication in Tests

### Current Status

The like button test (`click-like.spec.ts`) is currently **skipped** because it requires authentication with AWS Cognito, which is complex to mock in Playwright tests.

### Options for Testing Authenticated Features

#### Option 1: Skip Authentication Tests (Current Approach)
- Use `test.skip()` to skip tests that require authentication
- Add a simpler test that verifies the button is disabled when not authenticated
- **Pros**: Simple, no setup required
- **Cons**: Doesn't test the actual like functionality

#### Option 2: Use Real Authentication with Storage State
1. Create a test Cognito user in your test environment
2. Create a setup script that logs in and saves the auth state:

```typescript
// auth.setup.ts
import { test as setup } from '@playwright/test';

setup('authenticate', async ({ page }) => {
  await page.goto('/');
  await page.locator('#sign-in-button').click();
  await page.locator('#username').fill('test-user');
  await page.locator('#password').fill('test-password');
  await page.locator('#sign-in-submit').click();
  
  // Wait for authentication to complete
  await page.waitForSelector('.userInitial');
  
  // Save authentication state
  await page.context().storageState({ path: 'playwright/.auth/user.json' });
});
```

3. Use the saved state in your tests:

```typescript
test.use({ storageState: 'playwright/.auth/user.json' });
```

**Pros**: Tests real authentication flow
**Cons**: Requires test Cognito user, slower tests

#### Option 3: Mock Amplify Auth Module
Mock the entire `aws-amplify/auth` module using Playwright's module mocking capabilities (requires additional setup with bundler configuration).

**Pros**: Fast, no real auth needed
**Cons**: Complex setup, may not catch auth-related bugs

### Recommended Approach

For this project, we recommend **Option 2** for comprehensive E2E testing:

1. Set up a dedicated test Cognito user
2. Use Playwright's `storageState` to persist authentication
3. Run authenticated tests in a separate test suite

## Test Structure

Each test follows this pattern:

1. **Mock API responses** - Use `page.route()` to intercept and mock API calls
2. **Navigate to app** - Load the application
3. **Perform actions** - Click buttons, fill forms, etc.
4. **Assert expectations** - Verify the UI state

## Example: Mocking API Responses

```typescript
await page.route(/.*\/api\/v1\/quote$/, async (route) => {
  if (route.request().method() === 'GET') {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 1,
        quoteText: 'Test Quote',
        author: 'Test Author',
        liked: false
      })
    });
  }
});
```

## Debugging Tests

```bash
# Run with debug mode
npm run test:e2e -- --debug

# Generate trace for failed tests
npm run test:e2e -- --trace on

# View trace
npx playwright show-trace trace.zip
```

## CI/CD Integration

Tests are configured to run in GitHub Actions. See `.github/workflows/playwright.yml` for the CI configuration.
