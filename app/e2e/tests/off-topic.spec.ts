/**
 * Scenario 8 – Off-topic question in chat.
 *
 * After a successful submission, send an off-topic question (e.g. weather in Paris).
 * With a real LLM the system would refuse with a guardrail message.
 * With MockLlmService, the mock always returns the same response regardless of topic.
 *
 * MOCK LIMITATION: MockLlmService does not implement off-topic detection.
 * PROXY LIMITATION: Same SSE streaming completion issue as chat-streaming.spec.ts —
 * the typing indicator stays visible because the 'done' event is not forwarded
 * through the Angular CLI Vite dev server proxy.
 *
 * This test verifies:
 *   1. The off-topic message can be sent without a crash.
 *   2. A streaming assistant bubble appears (request reached the backend).
 *   3. No stream-error div is shown immediately.
 *
 * For full guardrail testing against a real LLM, set OPENROUTER_API_KEY.
 */
import { test, expect } from '@playwright/test';
import * as path from 'path';
import { IntakeFormPage } from '../pages/IntakeFormPage';
import { ChatPage } from '../pages/ChatPage';

const FIXTURES = path.join(__dirname, '..', 'fixtures');

test.describe('Off-topic chat message (mock limitation documented)', () => {
  test('should send off-topic message without error (mock returns default response)', async ({
    page,
  }) => {
    test.setTimeout(60_000);
    const form = new IntakeFormPage(page);
    const chat = new ChatPage(page);

    // ── Step 1: Submit clean RETURN to reach chat ─────────────────────────────
    await form.goto();
    await form.selectCaseType('RETURN');
    await form.selectCategory();
    await form.fillModelName('Bosch Drill GSB 18');
    await form.selectPurchaseDate();
    await form.uploadImage(path.join(FIXTURES, 'clean-return.jpg'));
    await form.submitAndWaitForNavigation();
    await expect(page).toHaveURL(/\/chat\//);

    // ── Step 2: Wait for initial assistant message ────────────────────────────
    await expect(chat.assistantBubbles.first()).toBeVisible({ timeout: 10_000 });

    // ── Step 3: Send off-topic question ──────────────────────────────────────
    const offTopicText = 'Jaka jest pogoda w Paryżu?';
    await chat.sendMessage(offTopicText);

    // ── Step 4: User bubble appears ───────────────────────────────────────────
    await chat.waitForUserBubble(offTopicText);

    // ── Step 5: New assistant streaming bubble appears ───────────────────────
    // This confirms the message was accepted by the backend (no 404/400 error)
    await expect(chat.assistantBubbles).toHaveCount(2, { timeout: 15_000 });

    // ── Step 6: No immediate stream-error visible ─────────────────────────────
    // Wait briefly to let any synchronous error appear
    const streamError = page.locator('.stream-error');
    // The stream is in progress — no error should be shown
    const errorCount = await streamError.count();
    expect(errorCount, 'No stream-error should appear for off-topic (mock responds to all)').toBe(0);

    // ── Step 7 (fixme): Wait for streaming done and verify response ───────────
    // FIXME: SSE proxy issue prevents stream completion from being detected.
    // See chat-streaming.spec.ts for full documentation.
    //
    // In mock mode: off-topic detection is NOT implemented.
    // The mock returns "Oto moja odpowiedź. Proszę o kontakt." for any input.
    // A real LLM would refuse with a domain-specific guardrail message.
  });
});
