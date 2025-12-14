import { test, expect } from '@playwright/test';

test.describe('New Quote Button', () => {
  test('should fetch and display a new quote when New Quote button is clicked', async ({ page }) => {

    // Mock the API responses
    await page.route(/.*\/api\/v1\/quote$/, async (route) => {
      if (route.request().method() === 'GET') {
        // First request - initial quote
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 15,
            quoteText: 'Quote15',
            author: 'Author15',
            likes: 0
          })
        });
      } else if (route.request().method() === 'POST') {
        // Second request - new quote after button click
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 2,
            quoteText: 'Quote2',
            author: 'Author2',
            likes: 0
          })
        });
      } else {
        await route.continue();
      }
    });

    // Navigate to the app
    await page.goto('/', { waitUntil: 'load' });

    // Wait for the initial quote to appear
    await expect(page.locator('.quoteView p').first()).toContainText('Quote15');

    // Click the New Quote button
    await page.locator('.newQuoteButton').click();

    // Verify the new quote is displayed
    await expect(page.locator('.quoteView p').first()).toContainText('Quote2');
    await expect(page.locator('.quoteView p.author')).toContainText('Author2');

    // Verify the quote is properly formatted with quotes
    await expect(page.locator('.quoteView p').first()).toHaveText('"Quote2"');

    // Verify Next button is disabled (we're at the latest quote)
    await expect(page.locator('.nextButton')).toBeDisabled();

    // Verify all other buttons are enabled
    await expect(page.locator('.newQuoteButton')).toBeEnabled();
    await expect(page.locator('.likeButton')).toBeDisabled();
    await expect(page.locator('.previousButton')).toBeEnabled();
    await expect(page.locator('.firstButton')).toBeEnabled();
    await expect(page.locator('.lastButton')).toBeEnabled();
  });

  test('should only call API once when New Quote is clicked multiple times while loading', async ({ page }) => {
    let apiCallCount = 0;

    // Mock the API response with a delay
    await page.route(/.*\/api\/v1\/quote$/, async (route) => {
      if (route.request().method() === 'GET') {
        apiCallCount++;
        // Simulate slow API response
        await new Promise(resolve => setTimeout(resolve, 1000));
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 1,
            quoteText: 'Quote1',
            author: 'Author1',
            likes: 0
          })
        });
      } else if (route.request().method() === 'POST') {
        apiCallCount++;
        // Simulate slow API response for new quote
        await new Promise(resolve => setTimeout(resolve, 1000));
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 7,
            quoteText: 'Quote7',
            author: 'Author7',
            likes: 0
          })
        });
      } else {
        await route.continue();
      }
    });

    // Navigate to the app
    await page.goto('/', { waitUntil: 'load' });

    // Wait for the initial quote to load
    await expect(page.locator('.quoteView p').first()).toContainText('Quote1');

    // Reset counter after initial load
    apiCallCount = 0;

    // Click New Quote button 15 times rapidly
    for (let i = 0; i < 15; i++) {
      await page.locator('.newQuoteButton').click({ force: true });
    }
    
    // Wait for the new quote to appear
    await expect(page.locator('.quoteView p').first()).toContainText('Quote7');

    // Verify the API was only called once for the button clicks (POST request)
    expect(apiCallCount).toBe(1);

    // Verify the quote is displayed correctly
    await expect(page.locator('.quoteView p.author')).toContainText('Author7');
    await expect(page.locator('.quoteView p').first()).toHaveText('"Quote7"');
  });
});
