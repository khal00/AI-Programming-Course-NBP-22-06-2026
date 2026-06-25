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
});
