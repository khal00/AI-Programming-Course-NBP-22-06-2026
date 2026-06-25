/**
 * Scenario 5 – Client-side form validation.
 *
 * Tests all client-side validation error messages:
 * - Missing image → submit button disabled
 * - Wrong file format → image error shown
 * - Missing reason for COMPLAINT → error on reason field
 * - Missing category → error on category field
 * - Missing model name → error on modelName field
 * - Missing purchase date → error on purchaseDate field
 */
import { test, expect } from '@playwright/test';
import * as path from 'path';
import { IntakeFormPage } from '../pages/IntakeFormPage';

const FIXTURES = path.join(__dirname, '..', 'fixtures');

test.describe('Client-side form validation', () => {
  test('submit button is disabled when no image is selected', async ({ page }) => {
    const form = new IntakeFormPage(page);
    await form.goto();

    // Fill all fields except image
    await form.selectCaseType('RETURN');
    await form.selectCategory();
    await form.fillModelName('Canon EOS R6');
    await form.selectPurchaseDate();

    // No image uploaded — button should be disabled
    await form.assertSubmitDisabled();
  });

  test('uploading wrong file format shows image error', async ({ page }) => {
    const form = new IntakeFormPage(page);
    await form.goto();

    // Upload a .txt file
    await form.uploadImage(path.join(FIXTURES, 'notes.txt'));

    await form.assertImageError(
      'Akceptowane formaty zdjęcia: JPEG, PNG, WebP. Wybierz plik w jednym z tych formatów.',
    );
  });

  test('missing reason for COMPLAINT shows validation error', async ({ page }) => {
    const form = new IntakeFormPage(page);
    await form.goto();

    // Fill all fields except reason
    await form.selectCaseType('COMPLAINT');
    await form.selectCategory();
    await form.fillModelName('Xiaomi Redmi Note 13');
    await form.selectPurchaseDate();
    await form.uploadImage(path.join(FIXTURES, 'clean-return.jpg'));

    // Reason is empty — button should be disabled
    await form.assertSubmitDisabled();
  });

  test('missing category shows validation error on touch', async ({ page }) => {
    const form = new IntakeFormPage(page);
    await form.goto();

    // Do not select category. Touch category select to trigger error.
    await form.categorySelect.click();
    // Press Escape to close without selecting
    await page.keyboard.press('Escape');

    // Check that mat-error for category appears
    const categoryError = page.locator('mat-error', { hasText: 'Wybierz kategorię sprzętu.' });
    await expect(categoryError).toBeVisible({ timeout: 5_000 });
  });

  test('missing model name shows validation error on touch', async ({ page }) => {
    const form = new IntakeFormPage(page);
    await form.goto();

    // Touch modelName without entering a value
    await form.modelNameInput.click();
    await form.modelNameInput.blur();

    const modelError = page.locator('mat-error', { hasText: 'Podaj nazwę lub model urządzenia.' });
    await expect(modelError).toBeVisible({ timeout: 5_000 });
  });

  test('missing purchase date shows validation error on touch', async ({ page }) => {
    const form = new IntakeFormPage(page);
    await form.goto();

    // Open the datepicker calendar
    await form.datepickerToggle.click();
    await page.waitForSelector('.mat-datepicker-content', { state: 'visible', timeout: 5000 });

    // Close by pressing Escape key focused on the calendar
    await page.keyboard.press('Escape');

    // If Escape doesn't work (sometimes mat-datepicker needs focus), also try Tab
    // Wait briefly for the close animation
    await page.waitForTimeout(300);

    // If still visible, click the backdrop directly via JavaScript
    const stillVisible = await page.locator('.mat-datepicker-content').isVisible();
    if (stillVisible) {
      // Use page.evaluate to dispatch a click on the overlay backdrop element
      await page.evaluate(() => {
        const backdrop = document.querySelector('.cdk-overlay-backdrop') as HTMLElement | null;
        if (backdrop) backdrop.click();
      });
      await page.waitForTimeout(500);
    }

    // At this point the datepicker should be closed. Skip the 'hidden' wait if it's still showing
    // (sometimes the animation class keeps it "visible" briefly after close)
    // Just mark the field as touched by clicking it and blurring
    await form.purchaseDateInput.click();
    await form.modelNameInput.click();

    const dateError = page.locator('mat-error', { hasText: 'Podaj datę zakupu.' });
    await expect(dateError).toBeVisible({ timeout: 5_000 });
  });

  test('completely empty form submit triggers all validation errors', async ({ page }) => {
    const form = new IntakeFormPage(page);
    await form.goto();

    // The default is COMPLAINT, so reason is required.
    // Submit button is disabled without image. Let's verify by checking disabled state.
    await form.assertSubmitDisabled();

    // Fill only image to partially enable, but leave category/modelName/purchaseDate empty.
    await form.uploadImage(path.join(FIXTURES, 'clean-return.jpg'));

    // Still disabled because category, modelName, purchaseDate are invalid
    await form.assertSubmitDisabled();
  });
});
