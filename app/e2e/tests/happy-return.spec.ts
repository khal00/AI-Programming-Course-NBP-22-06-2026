/**
 * Scenario 1 – Happy path: clean RETURN submission.
 *
 * User submits a return case with a clean image (filename contains "clean-return").
 * MockLlmService returns APPROVE verdict.
 * Expected: navigates to chat, verdict chip shows "Zatwierdzone", first assistant
 * message is present, mandatory disclaimer visible on intake form.
 */
import { test, expect } from '@playwright/test';
import * as path from 'path';
import { IntakeFormPage } from '../pages/IntakeFormPage';
import { ChatPage } from '../pages/ChatPage';

const FIXTURES = path.join(__dirname, '..', 'fixtures');

test.describe('Happy path – RETURN (clean-return)', () => {
  test('should submit RETURN form and land on chat with APPROVE verdict', async ({ page }) => {
    const form = new IntakeFormPage(page);
    const chat = new ChatPage(page);

    // ── Step 1: Navigate to intake form ──────────────────────────────────────
    await form.goto();
    await expect(page).toHaveURL('/');
    await expect(page.locator('h1.intake-title')).toContainText('Zgłoszenie');

    // ── Step 2: Verify mandatory disclaimer is on the page ───────────────────
    const introText = await page.locator('.intake-intro').textContent();
    expect(introText).toContain('wstępna');
    expect(introText).toContain('niewiążąca');
    expect(introText).toContain('pracownik obsługi');

    // ── Step 3: Fill RETURN form ─────────────────────────────────────────────
    await form.selectCaseType('RETURN');
    await form.selectCategory(); // first available category
    await form.fillModelName('Samsung Galaxy S24');
    await form.selectPurchaseDate();
    await form.uploadImage(path.join(FIXTURES, 'clean-return.jpg'));

    // ── Step 4: Submit button should be enabled ──────────────────────────────
    await form.assertSubmitEnabled();

    // ── Step 5: Submit and wait for navigation to chat ───────────────────────
    await form.submitAndWaitForNavigation();
    await expect(page).toHaveURL(/\/chat\//);

    // ── Step 6: Assert verdict chip shows "Zatwierdzone" ─────────────────────
    await chat.assertVerdictLabel('Zatwierdzone');

    // ── Step 7: Assert first assistant message is present ────────────────────
    const chatPageObj = new ChatPage(page);
    await expect(chatPageObj.assistantBubbles.first()).toBeVisible({ timeout: 10_000 });
  });
});
