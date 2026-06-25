import { TestBed } from '@angular/core/testing';
import { ChatStreamService, StreamEvent } from './chat-stream.service';

// ─── Helpers ──────────────────────────────────────────────────────────────────

function makeStream(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    }
  });
}

// ─── Specs ────────────────────────────────────────────────────────────────────

describe('ChatStreamService', () => {
  let service: ChatStreamService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ChatStreamService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('token sequence: emits token then done', (done) => {
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve({
        ok: true,
        status: 200,
        body: makeStream('event: token\ndata: hello\n\nevent: done\ndata: [DONE]\n\n')
      } as Response)
    );

    const events: StreamEvent[] = [];
    service.streamMessage('sess-1', 'hello').subscribe({
      next: (e) => events.push(e),
      complete: () => {
        expect(events).toEqual([
          { type: 'token', text: 'hello' },
          { type: 'done' }
        ]);
        done();
      },
      error: done.fail
    });
  });

  it('error frame: emits error event and completes', (done) => {
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve({
        ok: true,
        status: 200,
        body: makeStream('event: error\ndata: Coś poszło nie tak\n\n')
      } as Response)
    );

    const events: StreamEvent[] = [];
    service.streamMessage('sess-1', 'test').subscribe({
      next: (e) => events.push(e),
      complete: () => {
        expect(events).toEqual([
          { type: 'error', message: 'Coś poszło nie tak' }
        ]);
        done();
      },
      error: done.fail
    });
  });

  it('split chunk: reassembles frame split across two chunks', (done) => {
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve({
        ok: true,
        status: 200,
        body: makeStream(
          'event: token\ndata: hel',
          'lo\n\nevent: done\ndata: [DONE]\n\n'
        )
      } as Response)
    );

    const events: StreamEvent[] = [];
    service.streamMessage('sess-1', 'test').subscribe({
      next: (e) => events.push(e),
      complete: () => {
        expect(events).toEqual([
          { type: 'token', text: 'hello' },
          { type: 'done' }
        ]);
        done();
      },
      error: done.fail
    });
  });

  it('HTTP 404: emits Polish session-not-found error', (done) => {
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve({ ok: false, status: 404, body: null } as unknown as Response)
    );

    const events: StreamEvent[] = [];
    service.streamMessage('sess-missing', 'test').subscribe({
      next: (e) => events.push(e),
      complete: () => {
        expect(events).toEqual([
          { type: 'error', message: 'Sesja nie istnieje lub wygasła. Rozpocznij nową sprawę.' }
        ]);
        done();
      },
      error: done.fail
    });
  });

  it('HTTP 400: emits Polish blank-message error', (done) => {
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve({ ok: false, status: 400, body: null } as unknown as Response)
    );

    const events: StreamEvent[] = [];
    service.streamMessage('sess-1', '').subscribe({
      next: (e) => events.push(e),
      complete: () => {
        expect(events).toEqual([
          { type: 'error', message: 'Wiadomość nie może być pusta.' }
        ]);
        done();
      },
      error: done.fail
    });
  });

  // ── DEF-001: robust EOF handling ──────────────────────────────────────────

  it('EOF without done frame: emits tokens then synthetic done on connection close', (done) => {
    // Simulate a reverse proxy dropping the terminal `done` SSE frame:
    // stream contains token frames but closes without sending `event: done`.
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve({
        ok: true,
        status: 200,
        body: makeStream(
          'event: token\ndata: Cześć\n\n',
          'event: token\ndata: świecie\n\n'
          // NOTE: NO trailing `event: done` frame — proxy dropped it
        )
      } as Response)
    );

    const events: StreamEvent[] = [];
    let completeCalled = 0;
    service.streamMessage('sess-1', 'hello').subscribe({
      next: (e) => events.push(e),
      complete: () => {
        completeCalled++;
        // Must have received the two tokens AND a synthetic done event
        expect(events).toEqual([
          { type: 'token', text: 'Cześć' },
          { type: 'token', text: 'świecie' },
          { type: 'done' }
        ]);
        // Completion must fire exactly once
        expect(completeCalled).toBe(1);
        done();
      },
      error: done.fail
    });
  });

  it('EOF without done frame: flushes buffer that lacks trailing \\n\\n', (done) => {
    // The last token frame has no trailing double-newline when the connection closes.
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve({
        ok: true,
        status: 200,
        body: makeStream(
          'event: token\ndata: pierwsza\n\n',
          'event: token\ndata: ostatnia'   // no trailing \n\n — cut off by proxy
        )
      } as Response)
    );

    const events: StreamEvent[] = [];
    service.streamMessage('sess-1', 'hello').subscribe({
      next: (e) => events.push(e),
      complete: () => {
        // Both tokens should be emitted; buffer flushed; synthetic done emitted
        expect(events).toEqual([
          { type: 'token', text: 'pierwsza' },
          { type: 'token', text: 'ostatnia' },
          { type: 'done' }
        ]);
        done();
      },
      error: done.fail
    });
  });

  it('error frame then EOF: does not emit done after error, completes exactly once', (done) => {
    // An `error` SSE frame arrives, then the connection closes (EOF).
    // The service must NOT emit a second completion (done event) after the error.
    spyOn(globalThis, 'fetch').and.returnValue(
      Promise.resolve({
        ok: true,
        status: 200,
        body: makeStream('event: error\ndata: Błąd serwera\n\n')
      } as Response)
    );

    const events: StreamEvent[] = [];
    let completeCalled = 0;
    service.streamMessage('sess-1', 'test').subscribe({
      next: (e) => events.push(e),
      complete: () => {
        completeCalled++;
        // Error is emitted, done is NOT
        expect(events).toEqual([
          { type: 'error', message: 'Błąd serwera' }
        ]);
        expect(completeCalled).toBe(1);
        done();
      },
      error: done.fail
    });
  });
});
