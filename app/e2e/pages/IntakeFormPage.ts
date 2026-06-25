import { type Page, type Locator, expect } from '@playwright/test';
import * as path from 'path';

/**
 * Page Object Model for the intake form at route '/'.
 */
export class IntakeFormPage {
  readonly page: Page;

  // Form field locators
  readonly complaintRadio: Locator;
  readonly returnRadio: Locator;
  readonly categorySelect: Locator;
  readonly modelNameInput: Locator;
  readonly purchaseDateInput: Locator;
  readonly datepickerToggle: Locator;
  readonly reasonTextarea: Locator;
  readonly dropZone: Locator;
  readonly fileInput: Locator;
  readonly submitButton: Locator;

  // Feedback locators
  readonly imageError: Locator;
  readonly submitError: Locator;
  readonly submitSpinner: Locator;
  readonly submitStatus: Locator;

  constructor(page: Page) {
    this.page = page;

    this.complaintRadio = page.locator('mat-radio-button[value="COMPLAINT"]');
    this.returnRadio = page.locator('mat-radio-button[value="RETURN"]');
    this.categorySelect = page.locator('mat-select[formcontrolname="category"]');
    this.modelNameInput = page.locator('input[formcontrolname="modelName"]');
    this.purchaseDateInput = page.locator('input[formcontrolname="purchaseDate"]');
    this.datepickerToggle = page.locator('mat-datepicker-toggle');
    this.reasonTextarea = page.locator('textarea[formcontrolname="reason"]');
    this.dropZone = page.locator('.drop-zone');
    this.fileInput = page.locator('input[type="file"]');
    this.submitButton = page.locator('[data-testid="submit-btn"]');

    this.imageError = page.locator('.field-error[role="alert"]');
    this.submitError = page.locator('.submit-error[role="alert"]');
    this.submitSpinner = page.locator('mat-spinner');
    this.submitStatus = page.locator('.submit-status');
  }

  async goto() {
    await this.page.goto('/');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Select COMPLAINT or RETURN case type by clicking the radio button.
   */
  async selectCaseType(type: 'COMPLAINT' | 'RETURN') {
    if (type === 'COMPLAINT') {
      await this.complaintRadio.click();
    } else {
      await this.returnRadio.click();
    }
  }

  /**
   * Select a category from the mat-select dropdown.
   * Clicks the select, waits for options panel, then clicks the first option
   * matching the given label substring (case-insensitive).
   */
  async selectCategory(labelSubstring?: string) {
    await this.categorySelect.click();
    // Wait for mat-options panel
    await this.page.waitForSelector('mat-option', { state: 'visible', timeout: 5000 });
    if (labelSubstring) {
      await this.page
        .locator('mat-option', { hasText: new RegExp(labelSubstring, 'i') })
        .first()
        .click();
    } else {
      // Pick first available option
      await this.page.locator('mat-option').first().click();
    }
  }

  /**
   * Fill the model name text input.
   */
  async fillModelName(name: string) {
    await this.modelNameInput.fill(name);
  }

  /**
   * Open the datepicker calendar and click a date within the current month.
   * Clicks the last available (non-disabled) day cell.
   * If dayOffset is provided (1-based), attempts to click that calendar day.
   */
  async selectPurchaseDate(dayOffset?: number) {
    await this.datepickerToggle.click();
    // Wait for datepicker popup
    await this.page.waitForSelector('.mat-datepicker-content', { state: 'visible', timeout: 5000 });

    if (dayOffset !== undefined) {
      // Click a specific day number in the calendar
      const dayCell = this.page
        .locator('.mat-calendar-body-cell-content')
        .filter({ hasText: new RegExp(`^${dayOffset}$`) })
        .first();
      await dayCell.click();
    } else {
      // Click the last non-disabled day in the current month (today or earlier)
      const enabledDays = this.page.locator(
        '.mat-calendar-body-cell:not(.mat-calendar-body-disabled) .mat-calendar-body-cell-content',
      );
      const count = await enabledDays.count();
      if (count > 0) {
        await enabledDays.nth(count - 1).click();
      }
    }

    // Wait for calendar to close
    await this.page.waitForSelector('.mat-datepicker-content', { state: 'hidden', timeout: 5000 });
  }

  /**
   * Fill the reason textarea.
   */
  async fillReason(reason: string) {
    await this.reasonTextarea.fill(reason);
  }

  /**
   * Upload an image file by setting it directly on the hidden file input.
   * This bypasses the click-on-drop-zone step.
   */
  async uploadImage(filePath: string) {
    await this.fileInput.setInputFiles(filePath);
  }

  /**
   * Attempt to upload a file by clicking the drop zone first, then setting files.
   * Some environments need the input to be visible/attached.
   */
  async uploadImageViaDropZone(filePath: string) {
    // The drop zone click triggers fileInput.click() in Angular,
    // but we can also set files directly on the hidden input.
    await this.fileInput.setInputFiles(filePath);
  }

  /**
   * Fill all fields required for a RETURN submission.
   */
  async fillReturnForm(opts: {
    category?: string;
    modelName?: string;
    imageFile?: string;
    reason?: string;
  }) {
    await this.selectCaseType('RETURN');
    await this.selectCategory(opts.category);
    await this.fillModelName(opts.modelName ?? 'Test Device Model X100');
    await this.selectPurchaseDate();
    if (opts.reason) {
      await this.fillReason(opts.reason);
    }
    if (opts.imageFile) {
      await this.uploadImage(opts.imageFile);
    }
  }

  /**
   * Fill all fields required for a COMPLAINT submission.
   */
  async fillComplaintForm(opts: {
    category?: string;
    modelName?: string;
    reason?: string;
    imageFile?: string;
  }) {
    await this.selectCaseType('COMPLAINT');
    await this.selectCategory(opts.category);
    await this.fillModelName(opts.modelName ?? 'Test Device Model X100');
    await this.selectPurchaseDate();
    await this.fillReason(opts.reason ?? 'Urządzenie nie włącza się po trzech dniach użytkowania.');
    if (opts.imageFile) {
      await this.uploadImage(opts.imageFile);
    }
  }

  /**
   * Click submit and wait for navigation or error.
   */
  async submit() {
    await this.submitButton.click();
  }

  /**
   * Submit form and wait until we navigate away from '/'.
   */
  async submitAndWaitForNavigation() {
    const navigationPromise = this.page.waitForURL(/\/chat\//, { timeout: 30_000 });
    await this.submitButton.click();
    await navigationPromise;
  }

  /**
   * Assert the submit button is disabled.
   */
  async assertSubmitDisabled() {
    await expect(this.submitButton).toBeDisabled();
  }

  /**
   * Assert the submit button is enabled.
   */
  async assertSubmitEnabled() {
    await expect(this.submitButton).toBeEnabled();
  }

  /**
   * Assert an image error message is shown.
   */
  async assertImageError(messageSubstring: string) {
    await expect(this.imageError).toBeVisible();
    await expect(this.imageError).toContainText(messageSubstring);
  }

  /**
   * Assert a submit-level error message is shown.
   */
  async assertSubmitError(messageSubstring: string) {
    await expect(this.submitError).toBeVisible({ timeout: 15_000 });
    await expect(this.submitError).toContainText(messageSubstring);
  }
}
