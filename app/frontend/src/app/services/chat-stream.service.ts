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

          function pump(): void {
            reader
              .read()
              .then(({ done, value }) => {
                if (done) {
                  subscriber.complete();
                  return;
                }

                buffer += decoder.decode(value, { stream: true });

                // Split on double-newline SSE frame delimiter
                const frames = buffer.split('\n\n');
                // The last element may be an incomplete frame — keep it in buffer
                buffer = frames.pop()!;

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
                    subscriber.next({ type: 'done' });
                    subscriber.complete();
                    return;
                  } else if (eventType === 'error') {
                    subscriber.next({ type: 'error', message: dataVal });
                    subscriber.complete();
                    return;
                  }
                }

                pump();
              })
              .catch((err: unknown) => subscriber.error(err));
          }

          pump();
        })
        .catch((err: unknown) => subscriber.error(err));
    });
  }
}
