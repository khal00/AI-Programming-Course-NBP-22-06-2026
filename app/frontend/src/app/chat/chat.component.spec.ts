import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideMarkdown } from 'ngx-markdown';
import { Subject, EMPTY } from 'rxjs';

import { ChatComponent } from './chat.component';
import { CaseStateService } from '../services/case-state.service';
import { ChatStreamService, StreamEvent } from '../services/chat-stream.service';
import { CaseResponse, CaseType, ChatMessageVM, Verdict } from '../models/case.models';

// ─── Helpers ──────────────────────────────────────────────────────────────────

function makeCaseResponse(): CaseResponse {
  return {
    sessionId: 'sess-123',
    verdict: Verdict.APPROVE,
    firstMessage: '**Witaj!** Twoja sprawa została rozpatrzona.',
    caseSummary: {
      caseType: CaseType.COMPLAINT,
      caseTypeLabel: 'Reklamacja',
      category: 'SMARTPHONES',
      categoryLabel: 'Smartfony',
      modelName: 'iPhone 15',
      purchaseDate: '2024-01-15',
    },
  };
}

// ─── Suite ────────────────────────────────────────────────────────────────────

describe('ChatComponent', () => {
  let fixture: ComponentFixture<ChatComponent>;
  let component: ChatComponent;
  let mockCaseStateService: jasmine.SpyObj<CaseStateService>;
  let mockChatStreamService: jasmine.SpyObj<ChatStreamService>;

  function setupTestBed(withResponse: CaseResponse | null = makeCaseResponse()): Promise<void> {
    mockCaseStateService = jasmine.createSpyObj(
      'CaseStateService',
      ['setLastCaseResponse', 'clearLastCaseResponse', 'setPreviewUrl', 'clearPreviewUrl'],
      {
        lastCaseResponse: signal(withResponse),
        previewUrl: signal(null as string | null),
      }
    );
    mockChatStreamService = jasmine.createSpyObj('ChatStreamService', ['streamMessage']);
    mockChatStreamService.streamMessage.and.returnValue(EMPTY);

    return TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [
        provideRouter([]),
        provideAnimationsAsync(),
        provideMarkdown(),
        { provide: CaseStateService, useValue: mockCaseStateService },
        { provide: ChatStreamService, useValue: mockChatStreamService },
      ],
    })
      .compileComponents()
      .then(() => {
        fixture = TestBed.createComponent(ChatComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
      });
  }

  beforeEach(() => TestBed.resetTestingModule());

  // ── 1. First message renders ─────────────────────────────────────────────

  it('renders the first assistant message from CaseResponse', async () => {
    await setupTestBed();
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const assistantBubble = el.querySelector('.assistant-bubble');
    expect(assistantBubble).toBeTruthy();
    // ngx-markdown renders the content into the element; check for text presence
    expect(el.textContent).toContain('Witaj!');
  });

  // ── 2. Verdict chip shows correct Polish label ───────────────────────────

  it('verdict chip shows Polish label "Zatwierdzone" for APPROVE verdict', async () => {
    await setupTestBed();
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Zatwierdzone');
  });

  // ── 3. No-session notice ─────────────────────────────────────────────────

  it('shows Polish no-session notice when lastCaseResponse is null', async () => {
    await setupTestBed(null);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Kontekst sesji jest niedostępny');
  });

  // ── 4. No-session restart link ───────────────────────────────────────────

  it('navigates to "/" when "Wróć do formularza" button is clicked', async () => {
    await setupTestBed(null);
    fixture.detectChanges();
    const routerSpy = spyOn(component['router'], 'navigate');
    const el: HTMLElement = fixture.nativeElement;
    const btn = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Wróć do formularza')
    );
    expect(btn).toBeTruthy();
    btn!.click();
    expect(routerSpy).toHaveBeenCalledWith(['/']);
  });

  // ── 5. Send appends user bubble ──────────────────────────────────────────

  it('sendMessage appends a user bubble to messages', fakeAsync(async () => {
    await setupTestBed();
    mockChatStreamService.streamMessage.and.returnValue(EMPTY);

    component.messageForm.get('message')!.setValue('Mam pytanie');
    component.sendMessage();
    tick();

    const userMessages = component.messages().filter((m: ChatMessageVM) => m.role === 'user');
    expect(userMessages.length).toBe(1);
    expect(userMessages[0].text).toBe('Mam pytanie');
  }));

  // ── 6. Streaming tokens accumulate ──────────────────────────────────────

  it('accumulates token events into the streaming assistant bubble', fakeAsync(async () => {
    await setupTestBed();
    const streamSubject = new Subject<StreamEvent>();
    mockChatStreamService.streamMessage.and.returnValue(streamSubject.asObservable());

    component.messageForm.get('message')!.setValue('Test');
    component.sendMessage();
    tick();

    streamSubject.next({ type: 'token', text: 'Hej' });
    streamSubject.next({ type: 'token', text: ' tam' });
    tick();

    const assistantMessages = component.messages().filter((m: ChatMessageVM) => m.role === 'assistant');
    const streamingBubble = assistantMessages.find((m: ChatMessageVM) => m.streaming);
    expect(streamingBubble?.text).toBe('Hej tam');
  }));

  // ── 7. Streaming done finalizes bubble ───────────────────────────────────

  it('marks bubble as not streaming after done event', fakeAsync(async () => {
    await setupTestBed();
    const streamSubject = new Subject<StreamEvent>();
    mockChatStreamService.streamMessage.and.returnValue(streamSubject.asObservable());

    component.messageForm.get('message')!.setValue('Test');
    component.sendMessage();
    tick();

    streamSubject.next({ type: 'token', text: 'Odpowiedź' });
    streamSubject.next({ type: 'done' });
    tick();

    const assistantMessages = component.messages().filter((m: ChatMessageVM) => m.role === 'assistant');
    // Last assistant message should not be streaming
    const lastAssistant = assistantMessages[assistantMessages.length - 1];
    expect(lastAssistant.streaming).toBeFalse();
    expect(component.isStreaming()).toBeFalse();
  }));

  // ── 8. Stream error shows inline ────────────────────────────────────────

  it('shows inline error on the streaming bubble after error event', fakeAsync(async () => {
    await setupTestBed();
    const streamSubject = new Subject<StreamEvent>();
    mockChatStreamService.streamMessage.and.returnValue(streamSubject.asObservable());

    component.messageForm.get('message')!.setValue('Test');
    component.sendMessage();
    tick();

    streamSubject.next({ type: 'error', message: 'Coś poszło nie tak.' });
    tick();

    const assistantMessages = component.messages().filter((m: ChatMessageVM) => m.role === 'assistant');
    const errorBubble = assistantMessages.find((m: ChatMessageVM) => m.error);
    expect(errorBubble?.error).toBe('Coś poszło nie tak.');
    expect(errorBubble?.streaming).toBeFalse();
  }));

  // ── 9. Markdown XSS sanitization ────────────────────────────────────────

  it('does not render a <script> tag from assistant message content', async () => {
    await setupTestBed();
    // Inject a message with a script tag
    component.messages.update((msgs: ChatMessageVM[]) => [
      ...msgs,
      { role: 'assistant' as const, text: "<script>alert('xss')</script>Safe text", streaming: false },
    ]);
    fixture.detectChanges();

    await fixture.whenStable();
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const scripts = el.querySelectorAll('script');
    expect(scripts.length).toBe(0);
  });
});
