import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { CaseResponse, ErrorResponse, MetadataResponse } from '../models/case.models';

/**
 * Display-safe error shape exposed to components.
 * Never contains raw codes, stack traces, or internal payloads (AC-29).
 */
export interface CaseServiceError {
  /** Polish user-facing message safe to display in the UI. */
  displayMessage: string;
  /** Machine-readable code from ErrorResponse.code (for 422/503 routing logic). */
  code?: string;
  /** True when the operation can be retried without user action (503). */
  retriable: boolean;
  /** Per-field validation errors (400 only). */
  fieldErrors?: ErrorResponse['fieldErrors'];
}

const GENERIC_POLISH_ERROR =
  'Wystąpił nieoczekiwany błąd. Spróbuj ponownie lub skontaktuj się z obsługą.';

@Injectable({ providedIn: 'root' })
export class CaseService {
  constructor(private readonly http: HttpClient) {}

  /**
   * Fetch case-type and equipment-category options for the intake form.
   * GET /api/metadata
   */
  getMetadata(): Observable<MetadataResponse> {
    return this.http.get<MetadataResponse>('/api/metadata');
  }

  /**
   * Submit the intake form as multipart/form-data.
   * The caller is responsible for building FormData with the six contract fields:
   * caseType, category, modelName, purchaseDate, reason, image.
   * POST /api/cases → 201 CaseResponse
   */
  submitCase(formData: FormData): Observable<CaseResponse> {
    return this.http
      .post<CaseResponse>('/api/cases', formData)
      .pipe(catchError((err: HttpErrorResponse) => throwError(() => this.mapError(err))));
  }

  // ─── Private helpers ────────────────────────────────────────────────────────

  private mapError(err: HttpErrorResponse): CaseServiceError {
    const body = err.error as Partial<ErrorResponse> | null;

    // Try to extract a typed ErrorResponse from the backend body.
    if (body && typeof body === 'object' && typeof body.message === 'string') {
      const code = body.code ?? undefined;
      const retriable = err.status === 503;
      return {
        displayMessage: body.message,
        code,
        retriable,
        fieldErrors: body.fieldErrors,
      };
    }

    // Unexpected / non-JSON error — surface a generic Polish fallback.
    return {
      displayMessage: GENERIC_POLISH_ERROR,
      retriable: false,
    };
  }
}
