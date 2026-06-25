import { Injectable, signal } from '@angular/core';
import { CaseResponse } from '../models/case.models';

/**
 * Signal-based state service that holds the last CaseResponse received from
 * POST /api/cases.  The ChatComponent reads firstMessage and verdict from this
 * signal without re-fetching (ADR-003).
 *
 * Also stores the optional image preview URL so the chat header can display
 * the uploaded photo thumbnail without re-reading the file.
 */
@Injectable({ providedIn: 'root' })
export class CaseStateService {
  private readonly _lastCaseResponse = signal<CaseResponse | null>(null);
  private readonly _previewUrl = signal<string | null>(null);

  /** Read-only signal accessor for the stored response. */
  readonly lastCaseResponse = this._lastCaseResponse.asReadonly();

  /** Read-only signal accessor for the optional image preview URL. */
  readonly previewUrl = this._previewUrl.asReadonly();

  setLastCaseResponse(response: CaseResponse): void {
    this._lastCaseResponse.set(response);
  }

  clearLastCaseResponse(): void {
    this._lastCaseResponse.set(null);
    this._previewUrl.set(null);
  }

  setPreviewUrl(url: string | null): void {
    this._previewUrl.set(url);
  }

  clearPreviewUrl(): void {
    this._previewUrl.set(null);
  }
}
