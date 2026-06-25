/**
 * Scenario 4 – Unreadable image.
 *
 * User submits a complaint with image filename "unreadable".
 * Backend returns HTTP 422 with a Polish error message.
 * Expected: stays on the intake form, submit error shown with
 * "Nie udało się przeanalizować zdjęcia. Prześlij wyraźniejsze zdjęcie urządzenia."
 * No navigation to chat.
 */
import { test, expect } from '@playwright/test';
import * as path from 'path';
import { IntakeFormPage } from '../pages/IntakeFormPage';

const FIXTURES = path.join(__dirname, '..', 'fixtures');

test.describe('Unreadable image → HTTP 422 error', () => {
  test('should show 422 error message and stay on intake form', async ({ page }) => {
    const form = new IntakeFormPage(page);

    // ── Step 1: Navigate to intake form ──────────────────────────────────────
    await form.goto();

    // ── Step 2: Fill RETURN form with unreadable image ───────────────────────
    await form.selectCaseType('RETURN');
    await form.selectCategory();
    await form.fillModelName('Lenovo ThinkPad X1');
    await form.selectPurchaseDate();
    await form.uploadImage(path.join(FIXTURES, 'unreadable.jpg'));

    // ── Step 3: Submit ────────────────────────────────────────────────────────
    await form.assertSubmitEnabled();
    await form.submit();

    // ── Step 4: Assert 422 error message appears ──────────────────────────────
    await form.assertSubmitError(
      'Nie udało się przeanalizować zdjęcia. Prześlij wyraźniejsze zdjęcie urządzenia.',
    );

    // ── Step 5: Assert we did NOT navigate to chat ────────────────────────────
    await expect(page).toHaveURL('/');
  });
});
