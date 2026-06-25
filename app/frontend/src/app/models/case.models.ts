/**
 * Shared API models — Hardware Service Decision Copilot.
 * Mirrors the backend DTO shapes from docs/contracts/api-contract.md.
 */

// ─── Enums ────────────────────────────────────────────────────────────────────

export enum CaseType {
  COMPLAINT = 'COMPLAINT',
  RETURN = 'RETURN',
}

export enum Verdict {
  APPROVE = 'APPROVE',
  REJECT = 'REJECT',
  NEEDS_INFO = 'NEEDS_INFO',
  ESCALATE = 'ESCALATE',
}

// ─── Verdict Polish labels ────────────────────────────────────────────────────

export const VERDICT_LABELS: Record<Verdict, string> = {
  [Verdict.APPROVE]: 'Zatwierdzone',
  [Verdict.REJECT]: 'Odrzucone',
  [Verdict.NEEDS_INFO]: 'Wymaga uzupełnienia',
  [Verdict.ESCALATE]: 'Eskalacja do specjalisty',
};

// ─── Verdict chip colors ──────────────────────────────────────────────────────

export type VerdictChipColor = 'primary' | 'accent' | 'warn' | undefined;

export const VERDICT_CHIP_COLORS: Record<Verdict, VerdictChipColor> = {
  [Verdict.APPROVE]: 'primary',
  [Verdict.REJECT]: 'warn',
  [Verdict.NEEDS_INFO]: 'accent',
  [Verdict.ESCALATE]: undefined,
};

// ─── Option (generic code+label pair) ────────────────────────────────────────

export interface Option {
  code: string;
  label: string;
}

// ─── Metadata ─────────────────────────────────────────────────────────────────

export interface MetadataResponse {
  caseTypes: Option[];
  categories: Option[];
}

// ─── Case response ────────────────────────────────────────────────────────────

export interface CaseSummary {
  caseType: CaseType;
  caseTypeLabel: string;
  category: string;
  categoryLabel: string;
  modelName: string;
  purchaseDate: string; // yyyy-MM-dd
}

export interface CaseResponse {
  sessionId: string;
  caseSummary: CaseSummary;
  verdict: Verdict;
  firstMessage: string; // Markdown
}

// ─── Error response ───────────────────────────────────────────────────────────

export interface FieldError {
  field: string;
  message: string; // Polish
}

export interface ErrorResponse {
  code: string;
  message: string; // Polish user-facing summary
  fieldErrors?: FieldError[];
}

// ─── Chat message view model ──────────────────────────────────────────────────

export interface ChatMessageVM {
  role: 'assistant' | 'user';
  text: string; // Markdown for assistant, plain for user
  streaming: boolean;
  error?: string; // Polish error message if streaming failed
}

// ─── Case form value ──────────────────────────────────────────────────────────

export interface CaseFormValue {
  caseType: CaseType;
  category: string;
  modelName: string;
  purchaseDate: Date;
  reason: string;
  imageFile: File | null;
}
