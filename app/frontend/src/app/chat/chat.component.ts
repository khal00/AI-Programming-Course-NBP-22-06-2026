import {
  Component,
  OnInit,
  OnDestroy,
  signal,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MarkdownModule } from 'ngx-markdown';

import { CaseStateService } from '../services/case-state.service';
import { ChatStreamService } from '../services/chat-stream.service';
import {
  CaseResponse,
  ChatMessageVM,
  VERDICT_LABELS,
  VERDICT_CHIP_COLORS,
  VerdictChipColor,
} from '../models/case.models';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MarkdownModule,
  ],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss',
})
export class ChatComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  readonly router = inject(Router);
  private readonly caseState = inject(CaseStateService);
  private readonly streamService = inject(ChatStreamService);
  private readonly fb = inject(FormBuilder);
  private readonly destroy$ = new Subject<void>();

  // ─── Signals ──────────────────────────────────────────────────────────────

  readonly sessionId = signal('');
  readonly caseResponse = signal<CaseResponse | null>(null);
  readonly messages = signal<ChatMessageVM[]>([]);
  readonly isStreaming = signal(false);

  // ─── Form ─────────────────────────────────────────────────────────────────

  readonly messageForm: FormGroup = this.fb.group({
    message: ['', [Validators.required, Validators.minLength(1)]],
  });

  // ─── Derived helpers (used in template) ───────────────────────────────────

  get verdictLabel(): string {
    const r = this.caseResponse();
    return r ? VERDICT_LABELS[r.verdict] : '';
  }

  get verdictColor(): VerdictChipColor {
    const r = this.caseResponse();
    return r ? VERDICT_CHIP_COLORS[r.verdict] : undefined;
  }

  get previewUrl(): string | null {
    return this.caseState.previewUrl();
  }

  // ─── Lifecycle ────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.sessionId.set(this.route.snapshot.paramMap.get('sessionId') ?? '');
    const response = this.caseState.lastCaseResponse();
    this.caseResponse.set(response);

    if (response) {
      this.messages.update((msgs) => [
        ...msgs,
        {
          role: 'assistant',
          text: response.firstMessage,
          streaming: false,
        },
      ]);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ─── Actions ──────────────────────────────────────────────────────────────

  sendMessage(): void {
    const text = (this.messageForm.get('message')!.value as string)?.trim();
    if (!text || this.isStreaming()) return;

    this.messageForm.reset();

    // Append user bubble
    this.messages.update((msgs) => [...msgs, { role: 'user', text, streaming: false }]);

    // Append empty streaming assistant bubble
    this.messages.update((msgs) => [
      ...msgs,
      { role: 'assistant', text: '', streaming: true },
    ]);
    this.isStreaming.set(true);
    this.messageForm.get('message')!.disable();

    this.streamService
      .streamMessage(this.sessionId(), text)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (event) => {
          if (event.type === 'token') {
            this.messages.update((msgs) => {
              const updated = [...msgs];
              const last = updated[updated.length - 1];
              updated[updated.length - 1] = { ...last, text: last.text + event.text };
              return updated;
            });
          } else if (event.type === 'done') {
            this.messages.update((msgs) => {
              const updated = [...msgs];
              updated[updated.length - 1] = {
                ...updated[updated.length - 1],
                streaming: false,
              };
              return updated;
            });
            this.isStreaming.set(false);
            this.messageForm.get('message')!.enable();
          } else if (event.type === 'error') {
            this.messages.update((msgs) => {
              const updated = [...msgs];
              const last = updated[updated.length - 1];
              updated[updated.length - 1] = {
                ...last,
                streaming: false,
                error: event.message,
              };
              return updated;
            });
            this.isStreaming.set(false);
            this.messageForm.get('message')!.enable();
          }
        },
        error: () => {
          this.messages.update((msgs) => {
            const updated = [...msgs];
            const last = updated[updated.length - 1];
            updated[updated.length - 1] = {
              ...last,
              streaming: false,
              error: 'Wystąpił błąd połączenia. Spróbuj ponownie.',
            };
            return updated;
          });
          this.isStreaming.set(false);
          this.messageForm.get('message')!.enable();
        },
      });
  }

  retryLastMessage(): void {
    const allMessages = this.messages();
    const lastUser = [...allMessages].reverse().find((m) => m.role === 'user');
    if (!lastUser) return;

    // Remove the errored assistant bubble
    this.messages.update((msgs) => msgs.slice(0, -1));
    // Remove last user bubble and re-send
    this.messages.update((msgs) => msgs.slice(0, -1));

    this.messageForm.get('message')!.setValue(lastUser.text);
    this.sendMessage();
  }

  goHome(): void {
    this.router.navigate(['/']);
  }
}
