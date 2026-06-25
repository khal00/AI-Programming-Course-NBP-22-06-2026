import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

// ─── Public types ─────────────────────────────────────────────────────────────

export type StreamEvent =
  | { type: 'token'; text: string }
  | { type: 'done' }
  | { type: 'error'; message: string };

// ─── Service ─────────────────────────────────────────────────────────────────

/**
 * Streams SSE responses from POST /api/cases/{sessionId}/messages.
 *
 * Uses `fetch` + `ReadableStream` because `EventSource` cannot send a POST body
 * (ADR-003).  SSE frames are delimited by `\n\n`; partial frames are buffered
 * across `read()` calls.
 */
@Injectable({ providedIn: 'root' })
export class ChatStreamService {
  streamMessage(sessionId: string, text: string): Observable<StreamEvent> {
    return new Observable<StreamEvent>((subscriber) => {
      fetch(`/api/cases/${sessionId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text }),
      })
        .then((response) => {
          if (!response.ok) {
            if (response.status === 404) {
              subscriber.next({
                type: 'error',
                message: 'Sesja nie istnieje lub wygasła. Rozpocznij nową sprawę.',
              });
            } else {
              subscriber.next({
                type: 'error',
                message: 'Wiadomość nie może być pusta.',
              });
            }
            subscriber.complete();
            return;
          }

          const reader = response.body!.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          // Guard: tracks whether the stream has already been terminated
          // (via explicit `done`/`error` frame or connection EOF) so we never
          // call subscriber.complete() twice (DEF-001).
          let completed = false;

          function finalize(): void {
            if (completed) return;
            completed = true;
            subscriber.next({ type: 'done' });
            subscriber.complete();
          }

          function parseFrames(text: string): boolean {
            // Returns true if an explicit done/error frame was encountered and
            // the stream should stop pumping.
            const frames = text.split('\n\n');
            for (const frame of frames) {
              if (!frame.trim()) continue;

              let eventType = '';
              let dataVal = '';

              for (const line of frame.split('\n')) {
                if (line.startsWith('event: ')) {
                  eventType = line.slice(7);
                } else if (line.startsWith('data: ')) {
                  dataVal = line.slice(6);
                }
              }

              if (eventType === 'token') {
                subscriber.next({ type: 'token', text: dataVal });
              } else if (eventType === 'done') {
                completed = true;
                subscriber.next({ type: 'done' });
                subscriber.complete();
                return true;
              } else if (eventType === 'error') {
                completed = true;
                subscriber.next({ type: 'error', message: dataVal });
                subscriber.complete();
                return true;
              }
            }
            return false;
          }

          function pump(): void {
            reader
              .read()
              .then(({ done, value }) => {
                if (done) {
                  // EOF: flush any remaining buffered text for a final partial frame,
                  // then emit synthetic done if the stream was not already completed
                  // by an explicit done/error SSE event (DEF-001).
                  const remaining = buffer + decoder.decode(undefined, { stream: false });
                  buffer = '';
                  if (remaining.trim()) {
                    parseFrames(remaining);
                  }
                  finalize();
                  return;
                }

                buffer += decoder.decode(value, { stream: true });

                // Split on double-newline SSE frame delimiter.
                // The last element may be an incomplete frame — keep it in buffer.
                const frames = buffer.split('\n\n');
                buffer = frames.pop()!;
                const terminated = parseFrames(frames.join('\n\n'));
                if (!terminated) {
                  pump();
                }
              })
              .catch((err: unknown) => subscriber.error(err));
          }

          pump();
        })
        .catch((err: unknown) => subscriber.error(err));
    });
  }
}
