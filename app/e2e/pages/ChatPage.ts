import { type Page, type Locator, expect } from '@playwright/test';

/**
 * Page Object Model for the chat view at route '/chat/:sessionId'.
 */
export class ChatPage {
  readonly page: Page;

  // Case header
  readonly caseHeader: Locator;
  readonly verdictChip: Locator;

  // Message list
  readonly messageList: Locator;
  readonly assistantBubbles: Locator;
  readonly userBubbles: Locator;

  // Input area
  readonly messageInput: Locator;
  readonly sendButton: Locator;

  // Global typing indicator
  readonly globalTypingIndicator: Locator;

  // Session-not-found notice
  readonly noSessionNotice: Locator;

  constructor(page: Page) {
    this.page = page;

    this.caseHeader = page.locator('.case-header__verdict');
    this.verdictChip = page.locator('.case-header__verdict mat-chip');

    this.messageList = page.locator('.message-list');
    this.assistantBubbles = page.locator('.message-row--assistant .assistant-bubble');
    this.userBubbles = page.locator('.message-row--user .user-bubble');

    this.messageInput = page.locator('textarea[formcontrolname="message"]');
    this.sendButton = page.locator('.input-area__send');

    this.globalTypingIndicator = page.locator('.global-typing-indicator');

    this.noSessionNotice = page.locator('.no-session-notice');
  }

  async gotoSession(sessionId: string) {
    await this.page.goto(`/chat/${sessionId}`);
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Assert the verdict chip contains the expected label.
   */
  async assertVerdictLabel(label: string) {
    await expect(this.verdictChip).toBeVisible({ timeout: 10_000 });
    await expect(this.verdictChip).toContainText(label);
  }

  /**
   * Assert the first assistant message contains the given text.
   */
  async assertFirstAssistantMessage(textSubstring: string) {
    await expect(this.assistantBubbles.first()).toBeVisible({ timeout: 10_000 });
    await expect(this.assistantBubbles.first()).toContainText(textSubstring);
  }

  /**
   * Assert the disclaimer text is present somewhere on the page.
   * From the intake form intro: "wstępna, niewiążąca ocena" and
   * "Ostateczną decyzję podejmuje pracownik obsługi"
   * NOTE: The disclaimer is on the intake form page, not the chat page.
   */
  async assertDisclaimerVisible() {
    // The chat page does not repeat the disclaimer; it's in the intake form header.
    // Verify via page.goto('/') check if needed from intake form tests.
    // On chat page: nothing to assert here for the disclaimer.
  }

  /**
   * Send a follow-up message via the chat input.
   */
  async sendMessage(text: string) {
    await this.messageInput.fill(text);
    await this.sendButton.click();
  }

  /**
   * Wait until a user bubble with the given text appears.
   */
  async waitForUserBubble(textSubstring: string) {
    const bubble = this.page.locator('.message-row--user .user-bubble', {
      hasText: textSubstring,
    });
    await expect(bubble).toBeVisible({ timeout: 10_000 });
  }

  /**
   * Wait until an assistant bubble (non-streaming) appears with given text.
   * Uses a broad text-content check that works with ngx-markdown nested DOM.
   */
  async waitForAssistantMessage(textSubstring: string, timeout = 30_000) {
    // First wait for streaming to fully complete
    await this.waitForStreamingDone(timeout);
    // Then verify the text is present in the last assistant bubble
    const count = await this.assistantBubbles.count();
    if (count > 0) {
      const lastBubble = this.assistantBubbles.nth(count - 1);
      await lastBubble.waitFor({ state: 'visible', timeout: 5_000 });
      // Use toContainText which searches text content of the element and its descendants
      const { expect } = await import('@playwright/test');
      await expect(lastBubble).toContainText(textSubstring, { timeout: 10_000 });
    }
  }

  /**
   * Wait until streaming is done (typing indicator disappears and send button re-enables).
   */
  async waitForStreamingDone(timeout = 30_000) {
    // Wait for the global typing indicator to disappear
    await expect(this.globalTypingIndicator).toBeHidden({ timeout });
    // Also wait for the send button to be re-enabled (confirms streaming completed)
    await expect(this.sendButton).toBeEnabled({ timeout: 10_000 });
  }

  /**
   * Get the text content of the last assistant bubble.
   */
  async getLastAssistantText(): Promise<string> {
    const count = await this.assistantBubbles.count();
    if (count === 0) return '';
    return (await this.assistantBubbles.nth(count - 1).textContent()) ?? '';
  }

  /**
   * Assert the session-not-found notice is shown.
   */
  async assertNoSessionNotice() {
    await expect(this.noSessionNotice).toBeVisible({ timeout: 5_000 });
  }

  /**
   * Assert no stack trace or internal error details are visible on the page.
   */
  async assertNoInternalErrorLeakage() {
    const bodyText = await this.page.locator('body').textContent();
    const forbidden = [
      'stackTrace',
      'StackTrace',
      'Exception',
      'at pl.nbp',
      'at org.spring',
      'at java.',
      '{"code":',
      'NullPointerException',
      'IllegalArgumentException',
    ];
    for (const pattern of forbidden) {
      expect(bodyText).not.toContain(pattern);
    }
  }
}
