/**
 * Scenario 3 – Contradictory information.
 *
 * User submits a complaint with image filename "contradiction".
 * MockLlmService returns HTTP 201 with NEEDS_INFO verdict and discrepancy note.
 * Expected: navigates to chat (HTTP 201 is a successful response),
 * verdict chip shows "Wymaga uzupełnienia".
 */
import { test, expect } from '@playwright/test';
import * as path from 'path';
import { IntakeFormPage } from '../pages/IntakeFormPage';
import { ChatPage } from '../pages/ChatPage';

const FIXTURES = path.join(__dirname, '..', 'fixtures');

test.describe('Contradiction → NEEDS_INFO verdict', () => {
  test('should submit form with contradiction image and show NEEDS_INFO verdict', async ({
    page,
  }) => {
    const form = new IntakeFormPage(page);
    const chat = new ChatPage(page);

    // ── Step 1: Navigate to intake form ──────────────────────────────────────
    await form.goto();

    // ── Step 2: Fill COMPLAINT form with contradiction image ─────────────────
    await form.selectCaseType('COMPLAINT');
    await form.selectCategory();
    await form.fillModelName('HP EliteBook 840');
    await form.selectPurchaseDate();
    await form.fillReason('Sprzęt się przegrzewa, ale nie widać żadnych fizycznych uszkodzeń.');
    await form.uploadImage(path.join(FIXTURES, 'contradiction.jpg'));

    // ── Step 3: Submit button should be enabled ──────────────────────────────
    await form.assertSubmitEnabled();

    // ── Step 4: Submit and wait for chat navigation (HTTP 201 = success) ─────
    await form.submitAndWaitForNavigation();
    await expect(page).toHaveURL(/\/chat\//);

    // ── Step 5: Assert verdict chip shows "Wymaga uzupełnienia" ──────────────
    await chat.assertVerdictLabel('Wymaga uzupełnienia');

    // ── Step 6: First assistant message is present ────────────────────────────
    await expect(chat.assistantBubbles.first()).toBeVisible({ timeout: 10_000 });
  });
});
