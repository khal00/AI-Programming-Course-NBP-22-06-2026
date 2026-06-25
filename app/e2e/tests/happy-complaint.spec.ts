/**
 * Scenario 2 – Happy path: COMPLAINT with manufacturing defect image.
 *
 * User submits a complaint with image filename "damaged-complaint".
 * MockLlmService returns APPROVE verdict (manufacturing defect confirmed).
 * Expected: navigates to chat, verdict chip shows "Zatwierdzone".
 */
import { test, expect } from '@playwright/test';
import * as path from 'path';
import { IntakeFormPage } from '../pages/IntakeFormPage';
import { ChatPage } from '../pages/ChatPage';

const FIXTURES = path.join(__dirname, '..', 'fixtures');

test.describe('Happy path – COMPLAINT (damaged-complaint)', () => {
  test('should submit COMPLAINT form and land on chat with APPROVE verdict', async ({ page }) => {
    const form = new IntakeFormPage(page);
    const chat = new ChatPage(page);

    // ── Step 1: Navigate to intake form ──────────────────────────────────────
    await form.goto();
    await expect(page).toHaveURL('/');

    // ── Step 2: Fill COMPLAINT form ──────────────────────────────────────────
    await form.selectCaseType('COMPLAINT');
    await form.selectCategory(); // first available category
    await form.fillModelName('Apple MacBook Pro 14');
    await form.selectPurchaseDate();
    await form.fillReason(
      'Urządzenie wykazuje wadę produkcyjną — ekran ma martwą linię pikseli od pierwszego dnia.',
    );
    await form.uploadImage(path.join(FIXTURES, 'damaged-complaint.jpg'));

    // ── Step 3: Submit button should be enabled ──────────────────────────────
    await form.assertSubmitEnabled();

    // ── Step 4: Submit and wait for navigation to chat ───────────────────────
    await form.submitAndWaitForNavigation();
    await expect(page).toHaveURL(/\/chat\//);

    // ── Step 5: Assert verdict chip shows "Zatwierdzone" ─────────────────────
    await chat.assertVerdictLabel('Zatwierdzone');

    // ── Step 6: First assistant message visible ───────────────────────────────
    await expect(chat.assistantBubbles.first()).toBeVisible({ timeout: 10_000 });
  });
});
