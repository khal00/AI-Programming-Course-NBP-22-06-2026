/**
 * Scenario 9 – Unknown/missing session.
 *
 * Navigate directly to /chat/bogus-session-id-12345 without first submitting
 * the intake form. The Angular ChatComponent reads session state from
 * CaseStateService.lastCaseResponse() which returns null for direct navigation.
 * Expected: the ".no-session-notice" div is shown.
 */
import { test, expect } from '@playwright/test';
import { ChatPage } from '../pages/ChatPage';

test.describe('Direct navigation to unknown session', () => {
  test('should show no-session-notice when navigating directly to chat URL', async ({ page }) => {
    const chat = new ChatPage(page);

    // Navigate directly to a bogus session ID (no prior form submission)
    await chat.gotoSession('bogus-session-id-12345');

    // The component checks caseResponse() — null for direct navigation
    await chat.assertNoSessionNotice();

    // The main chat view should NOT be rendered
    await expect(page.locator('.chat-shell')).toBeHidden();
  });
});
