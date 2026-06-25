import { Injectable, signal } from '@angular/core';
import { CaseResponse } from '../models/case.models';

/**
 * Signal-based state service that holds the last CaseResponse received from
 * POST /api/cases.  The ChatComponent reads firstMessage and verdict from this
 * signal without re-fetching (ADR-003).
 */
@Injectable({ providedIn: 'root' })
export class CaseStateService {
  private readonly _lastCaseResponse = signal<CaseResponse | null>(null);

  /** Read-only signal accessor for the stored response. */
  readonly lastCaseResponse = this._lastCaseResponse.asReadonly();

  setLastCaseResponse(response: CaseResponse): void {
    this._lastCaseResponse.set(response);
  }

  clearLastCaseResponse(): void {
    this._lastCaseResponse.set(null);
  }
}
