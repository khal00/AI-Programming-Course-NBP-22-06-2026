import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { CaseService, CaseServiceError } from './case.service';
import { CaseStateService } from './case-state.service';
import {
  CaseType,
  Verdict,
  MetadataResponse,
  CaseResponse,
  ErrorResponse,
} from '../models/case.models';

const MOCK_METADATA: MetadataResponse = {
  caseTypes: [
    { code: 'COMPLAINT', label: 'Reklamacja' },
    { code: 'RETURN', label: 'Zwrot' },
  ],
  categories: [
    { code: 'SMARTPHONES', label: 'Smartfony' },
    { code: 'LAPTOPS', label: 'Laptopy' },
  ],
};

const MOCK_CASE_RESPONSE: CaseResponse = {
  sessionId: 'session-abc-123',
  caseSummary: {
    caseType: CaseType.COMPLAINT,
    caseTypeLabel: 'Reklamacja',
    category: 'SMARTPHONES',
    categoryLabel: 'Smartfony',
    modelName: 'Galaxy S24',
    purchaseDate: '2024-03-01',
  },
  verdict: Verdict.APPROVE,
  firstMessage: '**Zatwierdzone** — reklamacja jest zasadna.',
};

function buildFormData(): FormData {
  const fd = new FormData();
  fd.append('caseType', CaseType.COMPLAINT);
  fd.append('category', 'SMARTPHONES');
  fd.append('modelName', 'Galaxy S24');
  fd.append('purchaseDate', '2024-03-01');
  fd.append('reason', 'Pęknięty ekran.');
  const file = new File(['img'], 'photo.jpg', { type: 'image/jpeg' });
  fd.append('image', file);
  return fd;
}

describe('CaseService', () => {
  let service: CaseService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CaseService,
        CaseStateService,
      ],
    });
    service = TestBed.inject(CaseService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // ─── getMetadata ──────────────────────────────────────────────────────────

  describe('getMetadata()', () => {
    it('should make a GET request to /api/metadata', () => {
      service.getMetadata().subscribe();
      const req = httpMock.expectOne('/api/metadata');
      expect(req.request.method).toBe('GET');
      req.flush(MOCK_METADATA);
    });

    it('should return typed MetadataResponse on 200', () => {
      let result: MetadataResponse | undefined;
      service.getMetadata().subscribe((r) => (result = r));
      const req = httpMock.expectOne('/api/metadata');
      req.flush(MOCK_METADATA);
      expect(result).toEqual(MOCK_METADATA);
      expect(result!.categories.length).toBe(2);
    });
  });

  // ─── submitCase ───────────────────────────────────────────────────────────

  describe('submitCase()', () => {
    it('should make a POST request to /api/cases', () => {
      service.submitCase(buildFormData()).subscribe();
      const req = httpMock.expectOne('/api/cases');
      expect(req.request.method).toBe('POST');
      req.flush(MOCK_CASE_RESPONSE, { status: 201, statusText: 'Created' });
    });

    it('should send FormData as the request body', () => {
      service.submitCase(buildFormData()).subscribe();
      const req = httpMock.expectOne('/api/cases');
      expect(req.request.body instanceof FormData).toBeTrue();
      req.flush(MOCK_CASE_RESPONSE, { status: 201, statusText: 'Created' });
    });

    it('should include the "caseType" field in FormData', () => {
      const fd = buildFormData();
      service.submitCase(fd).subscribe();
      const req = httpMock.expectOne('/api/cases');
      const body = req.request.body as FormData;
      expect(body.get('caseType')).toBe(CaseType.COMPLAINT);
      req.flush(MOCK_CASE_RESPONSE, { status: 201, statusText: 'Created' });
    });

    it('should include the "category" field in FormData', () => {
      const fd = buildFormData();
      service.submitCase(fd).subscribe();
      const req = httpMock.expectOne('/api/cases');
      const body = req.request.body as FormData;
      expect(body.get('category')).toBe('SMARTPHONES');
      req.flush(MOCK_CASE_RESPONSE, { status: 201, statusText: 'Created' });
    });

    it('should include the "modelName" field in FormData', () => {
      const fd = buildFormData();
      service.submitCase(fd).subscribe();
      const req = httpMock.expectOne('/api/cases');
      const body = req.request.body as FormData;
      expect(body.get('modelName')).toBe('Galaxy S24');
      req.flush(MOCK_CASE_RESPONSE, { status: 201, statusText: 'Created' });
    });

    it('should include the "purchaseDate" field in FormData', () => {
      const fd = buildFormData();
      service.submitCase(fd).subscribe();
      const req = httpMock.expectOne('/api/cases');
      const body = req.request.body as FormData;
      expect(body.get('purchaseDate')).toBe('2024-03-01');
      req.flush(MOCK_CASE_RESPONSE, { status: 201, statusText: 'Created' });
    });

    it('should include the "image" file field in FormData', () => {
      const fd = buildFormData();
      service.submitCase(fd).subscribe();
      const req = httpMock.expectOne('/api/cases');
      const body = req.request.body as FormData;
      expect(body.get('image')).toBeTruthy();
      req.flush(MOCK_CASE_RESPONSE, { status: 201, statusText: 'Created' });
    });

    it('should return typed CaseResponse on 201', () => {
      let result: CaseResponse | undefined;
      service.submitCase(buildFormData()).subscribe((r) => (result = r));
      const req = httpMock.expectOne('/api/cases');
      req.flush(MOCK_CASE_RESPONSE, { status: 201, statusText: 'Created' });
      expect(result).toEqual(MOCK_CASE_RESPONSE);
      expect(result!.sessionId).toBe('session-abc-123');
      expect(result!.verdict).toBe(Verdict.APPROVE);
    });

    // ─── Error mapping ─────────────────────────────────────────────────────

    it('should map 400 error to a Polish display message (from ErrorResponse.message)', () => {
      const errorBody: ErrorResponse = {
        code: 'VALIDATION_ERROR',
        message: 'Wystąpiły błędy walidacji formularza.',
        fieldErrors: [{ field: 'modelName', message: 'Pole jest wymagane.' }],
      };
      let caughtError: CaseServiceError | undefined;
      service.submitCase(buildFormData()).subscribe({
        error: (e: CaseServiceError) => (caughtError = e),
      });
      const req = httpMock.expectOne('/api/cases');
      req.flush(errorBody, { status: 400, statusText: 'Bad Request' });
      expect(caughtError).toBeDefined();
      expect(caughtError!.displayMessage).toBe('Wystąpiły błędy walidacji formularza.');
    });

    it('should map 422 IMAGE_UNREADABLE to a Polish display message', () => {
      const errorBody: ErrorResponse = {
        code: 'IMAGE_UNREADABLE',
        message: 'Nie udało się przeanalizować zdjęcia. Prześlij wyraźniejsze zdjęcie urządzenia.',
      };
      let caughtError: CaseServiceError | undefined;
      service.submitCase(buildFormData()).subscribe({
        error: (e: CaseServiceError) => (caughtError = e),
      });
      const req = httpMock.expectOne('/api/cases');
      req.flush(errorBody, { status: 422, statusText: 'Unprocessable Entity' });
      expect(caughtError!.displayMessage).toContain('wyraźniejsze zdjęcie');
      expect(caughtError!.code).toBe('IMAGE_UNREADABLE');
    });

    it('should map 503 LLM_UNAVAILABLE to a Polish display message', () => {
      const errorBody: ErrorResponse = {
        code: 'LLM_UNAVAILABLE',
        message: 'Usługa AI jest chwilowo niedostępna. Spróbuj ponownie za chwilę.',
      };
      let caughtError: CaseServiceError | undefined;
      service.submitCase(buildFormData()).subscribe({
        error: (e: CaseServiceError) => (caughtError = e),
      });
      const req = httpMock.expectOne('/api/cases');
      req.flush(errorBody, { status: 503, statusText: 'Service Unavailable' });
      expect(caughtError!.displayMessage).toContain('Spróbuj ponownie');
      expect(caughtError!.retriable).toBeTrue();
    });

    it('should surface a generic Polish fallback for unexpected non-Polish errors', () => {
      let caughtError: CaseServiceError | undefined;
      service.submitCase(buildFormData()).subscribe({
        error: (e: CaseServiceError) => (caughtError = e),
      });
      const req = httpMock.expectOne('/api/cases');
      req.flush('Internal Server Error', { status: 500, statusText: 'Internal Server Error' });
      expect(caughtError!.displayMessage).toBeTruthy();
      // Must be Polish — must not contain raw HTTP status text
      const msg = caughtError!.displayMessage;
      expect(msg).not.toContain('Internal Server Error');
    });

    it('should not expose raw code or payload in displayMessage', () => {
      const errorBody: ErrorResponse = {
        code: 'VALIDATION_ERROR',
        message: 'Wystąpiły błędy walidacji formularza.',
        fieldErrors: [{ field: 'modelName', message: 'Pole jest wymagane.' }],
      };
      let caughtError: CaseServiceError | undefined;
      service.submitCase(buildFormData()).subscribe({
        error: (e: CaseServiceError) => (caughtError = e),
      });
      const req = httpMock.expectOne('/api/cases');
      req.flush(errorBody, { status: 400, statusText: 'Bad Request' });
      expect(caughtError!.displayMessage).not.toContain('VALIDATION_ERROR');
    });
  });
});

describe('CaseStateService', () => {
  let stateService: CaseStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CaseStateService],
    });
    stateService = TestBed.inject(CaseStateService);
  });

  it('should have null lastCaseResponse initially', () => {
    expect(stateService.lastCaseResponse()).toBeNull();
  });

  it('should store a CaseResponse', () => {
    stateService.setLastCaseResponse(MOCK_CASE_RESPONSE);
    expect(stateService.lastCaseResponse()).toEqual(MOCK_CASE_RESPONSE);
  });

  it('should update the stored CaseResponse when set again', () => {
    stateService.setLastCaseResponse(MOCK_CASE_RESPONSE);
    const updated: CaseResponse = { ...MOCK_CASE_RESPONSE, sessionId: 'new-session' };
    stateService.setLastCaseResponse(updated);
    expect(stateService.lastCaseResponse()!.sessionId).toBe('new-session');
  });

  it('should clear the stored CaseResponse', () => {
    stateService.setLastCaseResponse(MOCK_CASE_RESPONSE);
    stateService.clearLastCaseResponse();
    expect(stateService.lastCaseResponse()).toBeNull();
  });
});
