/**
 * Scenario 10 – No internal error leakage.
 *
 * Submits cases that trigger backend errors (422 and 503) and asserts that
 * the UI does NOT expose stack traces, exception class names, raw JSON, or
 * other internal server details to the user.
 *
 * Covered by GlobalExceptionHandler which maps exceptions to clean Polish messages.
 */
import { test, expect } from '@playwright/test';
import * as path from 'path';
import { IntakeFormPage } from '../pages/IntakeFormPage';
import { ChatPage } from '../pages/ChatPage';

const FIXTURES = path.join(__dirname, '..', 'fixtures');

const FORBIDDEN_STRINGS = [
  'stackTrace',
  'StackTrace',
  'Exception',
  'at pl.nbp',
  'at org.spring',
  'at java.',
  'NullPointerException',
  'IllegalArgumentException',
  'RuntimeException',
  'HttpMessageNotReadableException',
  '{"code":',
  '"trace"',
  '"path":"/api',
  'Internal Server Error',
  'Whitelabel Error Page',
];

async function assertNoLeakage(page: import('@playwright/test').Page) {
  const bodyText = await page.locator('body').textContent();
  for (const forbidden of FORBIDDEN_STRINGS) {
    expect(bodyText, `Page must not contain "${forbidden}"`).not.toContain(forbidden);
  }
}

test.describe('No internal error leakage', () => {
  test('422 unreadable image error shows only clean Polish message', async ({ page }) => {
    const form = new IntakeFormPage(page);
    await form.goto();

    await form.selectCaseType('RETURN');
    await form.selectCategory();
    await form.fillModelName('Garmin Fenix 7');
    await form.selectPurchaseDate();
    await form.uploadImage(path.join(FIXTURES, 'unreadable.jpg'));
    await form.submit();

    // Wait for error to appear
    await form.assertSubmitError(
      'Nie udało się przeanalizować zdjęcia. Prześlij wyraźniejsze zdjęcie urządzenia.',
    );

    // Assert no internal details leaked
    await assertNoLeakage(page);
  });

  test('503 LLM unavailable error shows only clean Polish message', async ({ page }) => {
    const form = new IntakeFormPage(page);
    await form.goto();

    await form.selectCaseType('COMPLAINT');
    await form.selectCategory();
    await form.fillModelName('Philips Hue Bridge');
    await form.selectPurchaseDate();
    await form.fillReason('Urządzenie przestało działać po aktualizacji oprogramowania.');
    await form.uploadImage(path.join(FIXTURES, 'llm-fail.jpg'));
    await form.submit();

    // Wait for error
    await form.assertSubmitError(
      'Usługa AI jest chwilowo niedostępna. Spróbuj ponownie za chwilę.',
    );

    // Assert no internal details leaked
    await assertNoLeakage(page);
  });
});
