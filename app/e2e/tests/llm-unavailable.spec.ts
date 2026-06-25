/**
 * Scenario 6 – LLM service unavailable.
 *
 * User submits a complaint with image filename "llm-fail".
 * Backend returns HTTP 503.
 * Expected: stays on intake form, submit error shows
 * "Usługa AI jest chwilowo niedostępna. Spróbuj ponownie za chwilę."
 * No navigation to chat.
 */
import { test, expect } from '@playwright/test';
import * as path from 'path';
import { IntakeFormPage } from '../pages/IntakeFormPage';

const FIXTURES = path.join(__dirname, '..', 'fixtures');

test.describe('LLM unavailable → HTTP 503 error', () => {
  test('should show 503 error message and stay on intake form', async ({ page }) => {
    const form = new IntakeFormPage(page);

    // ── Step 1: Navigate to intake form ──────────────────────────────────────
    await form.goto();

    // ── Step 2: Fill COMPLAINT form with llm-fail image ──────────────────────
    await form.selectCaseType('COMPLAINT');
    await form.selectCategory();
    await form.fillModelName('Sony WH-1000XM5');
    await form.selectPurchaseDate();
    await form.fillReason('Słuchawki nie działają po tygodniu użytkowania.');
    await form.uploadImage(path.join(FIXTURES, 'llm-fail.jpg'));

    // ── Step 3: Submit ────────────────────────────────────────────────────────
    await form.assertSubmitEnabled();
    await form.submit();

    // ── Step 4: Assert 503 error message appears ──────────────────────────────
    await form.assertSubmitError(
      'Usługa AI jest chwilowo niedostępna. Spróbuj ponownie za chwilę.',
    );

    // ── Step 5: Assert we did NOT navigate to chat ────────────────────────────
    await expect(page).toHaveURL('/');

    // ── Step 6: Form should be re-enabled for retry ───────────────────────────
    await form.assertSubmitEnabled();
  });
});
