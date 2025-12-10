import { test, expect } from '@playwright/test';

test.describe('Quote Display', () => {
  test('should display mocked quote and author on the screen', async ({ page }) => {
    // Mock the API response for the initial quote fetch
    await page.route(/.*\/api\/v1\/quote$/, async (route) => {
      if (route.request().method() === 'GET') {
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
      } else {
        await route.continue();
      }
    });

    // Navigate to the app
    await page.goto('/', { waitUntil: 'load' });

    // Wait for the quote text to appear
    await expect(page.locator('.quoteView p').first()).toContainText('Quote15');

    // Verify the author is displayed
    await expect(page.locator('.quoteView p.author')).toContainText('Author15');

    // Verify the quote is properly formatted with quotes
    await expect(page.locator('.quoteView p').first()).toHaveText('"Quote15"');

    // Verify navigation buttons are disabled (only one quote in history)
    await expect(page.locator('.previousButton')).toBeDisabled();
    await expect(page.locator('.nextButton')).toBeDisabled();
    // Verify all other buttons are enabled
    await expect(page.locator('.newQuoteButton')).toBeEnabled();
    await expect(page.locator('.likeButton')).toBeEnabled();
    await expect(page.locator('.firstButton')).toBeEnabled();
    await expect(page.locator('.lastButton')).toBeEnabled();
  });
});
