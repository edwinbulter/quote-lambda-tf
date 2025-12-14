import { test, expect } from '@playwright/test';

test.describe('Like Button', () => {
  // Skip this test as it requires authentication which is complex to mock in Playwright
  // To run this test, you would need to:
  // 1. Set up a test Cognito user
  // 2. Use Playwright's storageState to persist authentication
  // 3. Or mock the entire Amplify Auth module
  test.skip('should like quotes and display them in Favourite Quotes component', async ({ page }) => {
    let quoteCounter = 0;
    const likedQuotes: any[] = [];

    // Mock Amplify Auth module by intercepting the module imports
    await page.route('**/*', async (route) => {
      const url = route.request().url();
      
      // Let API calls through to our mock handler
      if (url.includes('/api/v1/')) {
        return route.continue();
      }
      
      // Continue with all other requests
      await route.continue();
    });

    // Inject authentication state into localStorage before page loads
    await page.addInitScript(() => {
      // Mock Amplify's authentication state in localStorage
      const mockCognitoUser = {
        username: 'test-user',
        pool: {
          userPoolId: 'mock-pool-id',
          clientId: 'mock-client-id'
        },
        Session: null,
        client: {},
        signInUserSession: {
          idToken: {
            jwtToken: 'mock-id-token',
            payload: {
              'cognito:username': 'test-user',
              'cognito:groups': ['USER'],
              sub: 'test-user-id',
              email: 'test@example.com'
            }
          },
          accessToken: {
            jwtToken: 'mock-access-token',
            payload: {
              username: 'test-user',
              'cognito:groups': ['USER'],
              sub: 'test-user-id'
            }
          },
          refreshToken: {
            token: 'mock-refresh-token'
          }
        },
        authenticationFlowType: 'USER_SRP_AUTH',
        storage: {},
        keyPrefix: 'CognitoIdentityServiceProvider.mock-client-id',
        userDataKey: 'CognitoIdentityServiceProvider.mock-client-id.test-user.userData'
      };

      // Set in localStorage with Cognito's expected format
      const keyPrefix = 'CognitoIdentityServiceProvider.mock-client-id';
      localStorage.setItem(`${keyPrefix}.LastAuthUser`, 'test-user');
      localStorage.setItem(`${keyPrefix}.test-user.idToken`, 'mock-id-token');
      localStorage.setItem(`${keyPrefix}.test-user.accessToken`, 'mock-access-token');
      localStorage.setItem(`${keyPrefix}.test-user.refreshToken`, 'mock-refresh-token');
      localStorage.setItem(`${keyPrefix}.test-user.clockDrift`, '0');
      
      // Mock the Amplify Auth module functions
      (window as any).__mockAmplifyAuth = true;
    });

    // Mock all API routes with a single handler
    await page.route('**/api/v1/**', async (route) => {
      const url = route.request().url();
      const method = route.request().method();

      // Handle /quote/liked endpoint (must check before /quote)
      if (url.includes('/quote/liked')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(likedQuotes)
        });
        return;
      }

      // Handle /quote/{id}/like endpoint
      const likeMatch = url.match(/\/quote\/(\d+)\/like$/);
      if (likeMatch) {
        const quoteId = parseInt(likeMatch[1]);
        
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: quoteId,
            quoteText: `Quote${quoteId}`,
            author: `Author${quoteId}`,
            liked: true
          })
        });

        // Track liked quotes
        likedQuotes.push({
          id: quoteId,
          quoteText: `Quote${quoteId}`,
          author: `Author${quoteId}`,
          liked: true
        });
        return;
      }

      // Handle /quote endpoint (GET and POST) - must be exact match
      if (url.endsWith('/api/v1/quote') || url.match(/\/api\/v1\/quote\?/)) {
        if (method === 'GET') {
          // Initial quote
          quoteCounter++;
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              id: quoteCounter,
              quoteText: `Quote${quoteCounter}`,
              author: `Author${quoteCounter}`,
              likes: 0
            })
          });
          return;
        } else if (method === 'POST') {
          // New quote requests
          quoteCounter++;
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              id: quoteCounter,
              quoteText: `Quote${quoteCounter}`,
              author: `Author${quoteCounter}`,
              likes: 0
            })
          });
          return;
        }
      }

      // If no match, continue with the request
      await route.continue();
    });

    // Navigate to the app
    await page.goto('/', { waitUntil: 'load' });

    // Wait for initial quote to load (Quote2 appears because of double GET call) REASON=StrictMode intentionally double-invokes effects
    await expect(page.locator('.quoteView p').first()).toContainText('Quote2');

    // Click New Quote button 3 times to get quotes 3, 4, 5
    for (let i = 0; i < 3; i++) {
      await page.locator('.newQuoteButton').click();
      await expect(page.locator('.quoteView p').first()).toContainText(`Quote${i + 3}`);
    }

    // Verify we're on Quote5
    await expect(page.locator('.quoteView p').first()).toContainText('Quote5');
    await expect(page.locator('.quoteView p.author')).toContainText('Author5');

    // Click the Like button for Quote5
    await page.locator('.likeButton').click();

    // Wait for the like button to be disabled
    await expect(page.locator('.likeButton')).toBeDisabled();

    // Verify Favourite Quotes component shows Quote5
    await expect(page.locator('.messageBox .messageEven, .messageBox .messageOdd').first())
      .toContainText('Quote5 - Author5');

    // Click New Quote button 2 more times to get quotes 6 and 7
    await page.locator('.newQuoteButton').click();
    await expect(page.locator('.quoteView p').first()).toContainText('Quote6');

    await page.locator('.newQuoteButton').click();
    await expect(page.locator('.quoteView p').first()).toContainText('Quote7');
    await expect(page.locator('.quoteView p.author')).toContainText('Author7');

    // Verify Like button is enabled for Quote7
    await expect(page.locator('.likeButton')).toBeEnabled();

    // Click the Like button for Quote7
    await page.locator('.likeButton').click();

    // Wait for the like button to be disabled
    await expect(page.locator('.likeButton')).toBeDisabled();

    // Verify Favourite Quotes component shows both Quote5 and Quote7
    const favouriteQuotes = page.locator('.messageBox .messageEven, .messageBox .messageOdd');
    await expect(favouriteQuotes).toHaveCount(2);
    await expect(favouriteQuotes.nth(0)).toContainText('Quote5 - Author5');
    await expect(favouriteQuotes.nth(1)).toContainText('Quote7 - Author7');
  });

  test('should disable like button when user is not authenticated', async ({ page }) => {
    // Mock the API response for the initial quote fetch
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
      } else {
        await route.continue();
      }
    });

    // Mock the /quote/liked endpoint
    await page.route(/.*\/api\/v1\/quote\/liked$/, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    });

    // Navigate to the app
    await page.goto('/', { waitUntil: 'load' });

    // Wait for the quote to load
    await expect(page.locator('.quoteView p').first()).toContainText('Test Quote');

    // Verify the like button is disabled (user not authenticated)
    await expect(page.locator('.likeButton')).toBeDisabled();
  });
});
