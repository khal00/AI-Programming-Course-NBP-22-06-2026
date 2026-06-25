import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { IntakeFormComponent } from './intake-form.component';
import { CaseService, CaseServiceError } from '../services/case.service';
import { CaseStateService } from '../services/case-state.service';
import {
  CaseType,
  Verdict,
  MetadataResponse,
  CaseResponse,
} from '../models/case.models';

// ─── Mocks ────────────────────────────────────────────────────────────────────

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

const MOCK_RESPONSE: CaseResponse = {
  sessionId: 'sess-xyz',
  caseSummary: {
    caseType: CaseType.COMPLAINT,
    caseTypeLabel: 'Reklamacja',
    category: 'SMARTPHONES',
    categoryLabel: 'Smartfony',
    modelName: 'Galaxy S24',
    purchaseDate: '2024-03-01',
  },
  verdict: Verdict.APPROVE,
  firstMessage: '**Zatwierdzone**',
};

function createMockCaseService(): jasmine.SpyObj<CaseService> {
  const spy = jasmine.createSpyObj<CaseService>('CaseService', ['getMetadata', 'submitCase']);
  spy.getMetadata.and.returnValue(of(MOCK_METADATA));
  spy.submitCase.and.returnValue(of(MOCK_RESPONSE));
  return spy;
}

function createJpegFile(name = 'photo.jpg', sizeBytes = 1024): File {
  const bytes = new Uint8Array(sizeBytes);
  return new File([bytes], name, { type: 'image/jpeg' });
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Fill all required fields with valid values for a COMPLAINT.
 */
function fillValidComplaintForm(fixture: ComponentFixture<IntakeFormComponent>): void {
  const comp = fixture.componentInstance;
  comp.form.get('caseType')!.setValue(CaseType.COMPLAINT);
  comp.form.get('category')!.setValue('SMARTPHONES');
  comp.form.get('modelName')!.setValue('Galaxy S24');
  comp.form.get('purchaseDate')!.setValue(new Date('2024-03-01'));
  comp.form.get('reason')!.setValue('Pęknięty ekran.');
  comp.setImageFile(createJpegFile());
  fixture.detectChanges();
}

/**
 * Fill all required fields for a RETURN (no reason needed).
 */
function fillValidReturnForm(fixture: ComponentFixture<IntakeFormComponent>): void {
  const comp = fixture.componentInstance;
  comp.form.get('caseType')!.setValue(CaseType.RETURN);
  comp.form.get('category')!.setValue('SMARTPHONES');
  comp.form.get('modelName')!.setValue('Galaxy S24');
  comp.form.get('purchaseDate')!.setValue(new Date('2024-03-01'));
  comp.form.get('reason')!.setValue('');
  comp.setImageFile(createJpegFile());
  fixture.detectChanges();
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('IntakeFormComponent', () => {
  let fixture: ComponentFixture<IntakeFormComponent>;
  let comp: IntakeFormComponent;
  let mockCaseService: jasmine.SpyObj<CaseService>;
  let mockStateService: jasmine.SpyObj<CaseStateService>;
  let mockRouter: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    mockCaseService = createMockCaseService();
    mockStateService = jasmine.createSpyObj<CaseStateService>('CaseStateService', [
      'setLastCaseResponse',
      'clearLastCaseResponse',
      'setPreviewUrl',
      'clearPreviewUrl',
    ]);
    mockRouter = jasmine.createSpyObj<Router>('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [IntakeFormComponent, ReactiveFormsModule, NoopAnimationsModule],
      providers: [
        { provide: CaseService, useValue: mockCaseService },
        { provide: CaseStateService, useValue: mockStateService },
        { provide: Router, useValue: mockRouter },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(IntakeFormComponent);
    comp = fixture.componentInstance;
    fixture.detectChanges(); // ngOnInit → getMetadata
  });

  // ─── Form validity / submit button ───────────────────────────────────────

  it('should create the component', () => {
    expect(comp).toBeTruthy();
  });

  it('should call getMetadata on init to populate categories', () => {
    expect(mockCaseService.getMetadata).toHaveBeenCalledTimes(1);
  });

  it('should have 2 category options from metadata', () => {
    expect(comp.categories().length).toBe(2);
  });

  it('should have the submit button disabled when the form is empty', () => {
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="submit-btn"]') as HTMLButtonElement;
    expect(btn?.disabled).toBeTrue();
  });

  it('should enable the submit button when all required fields are valid (COMPLAINT)', () => {
    fillValidComplaintForm(fixture);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="submit-btn"]') as HTMLButtonElement;
    expect(btn?.disabled).toBeFalse();
  });

  it('should enable the submit button when all required fields are valid (RETURN)', () => {
    fillValidReturnForm(fixture);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('[data-testid="submit-btn"]') as HTMLButtonElement;
    expect(btn?.disabled).toBeFalse();
  });

  // ─── Reason conditional validator ────────────────────────────────────────

  it('should require "reason" when caseType is COMPLAINT', () => {
    comp.form.get('caseType')!.setValue(CaseType.COMPLAINT);
    comp.form.get('reason')!.setValue('');
    fixture.detectChanges();
    expect(comp.form.get('reason')!.valid).toBeFalse();
  });

  it('should not require "reason" when caseType is RETURN', () => {
    comp.form.get('caseType')!.setValue(CaseType.RETURN);
    comp.form.get('reason')!.setValue('');
    fixture.detectChanges();
    expect(comp.form.get('reason')!.valid).toBeTrue();
  });

  it('should flip the reason validator when caseType changes from COMPLAINT to RETURN', () => {
    comp.form.get('caseType')!.setValue(CaseType.COMPLAINT);
    comp.form.get('reason')!.setValue('');
    expect(comp.form.get('reason')!.valid).toBeFalse();

    comp.form.get('caseType')!.setValue(CaseType.RETURN);
    expect(comp.form.get('reason')!.valid).toBeTrue();
  });

  it('should flip the reason validator when caseType changes from RETURN to COMPLAINT', () => {
    comp.form.get('caseType')!.setValue(CaseType.RETURN);
    comp.form.get('reason')!.setValue('');
    expect(comp.form.get('reason')!.valid).toBeTrue();

    comp.form.get('caseType')!.setValue(CaseType.COMPLAINT);
    expect(comp.form.get('reason')!.valid).toBeFalse();
  });

  // ─── Future date validation ───────────────────────────────────────────────

  it('should mark purchaseDate invalid if the date is in the future', () => {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    comp.form.get('purchaseDate')!.setValue(tomorrow);
    fixture.detectChanges();
    expect(comp.form.get('purchaseDate')!.valid).toBeFalse();
  });

  it('should mark purchaseDate valid for today', () => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    comp.form.get('purchaseDate')!.setValue(today);
    fixture.detectChanges();
    expect(comp.form.get('purchaseDate')!.valid).toBeTrue();
  });

  // ─── Image validation ─────────────────────────────────────────────────────

  it('should reject a non-JPEG/PNG/WebP file with a Polish error message', () => {
    const pdfFile = new File([new Uint8Array(100)], 'doc.pdf', { type: 'application/pdf' });
    comp.setImageFile(pdfFile);
    fixture.detectChanges();
    expect(comp.imageError()).toBeTruthy();
    // Must be Polish — check for known Polish content
    expect(comp.imageError()).toContain('JPEG');
  });

  it('should reject a file larger than 10 MB (10 485 760 bytes) with a Polish error message', () => {
    const oversizedFile = new File([new Uint8Array(10_485_761)], 'big.jpg', { type: 'image/jpeg' });
    comp.setImageFile(oversizedFile);
    fixture.detectChanges();
    expect(comp.imageError()).toBeTruthy();
    expect(comp.imageError()).toContain('10');
  });

  it('should accept a JPEG file within 10 MB limit', () => {
    comp.setImageFile(createJpegFile('ok.jpg', 1024));
    fixture.detectChanges();
    expect(comp.imageError()).toBeNull();
  });

  it('should accept a PNG file', () => {
    const pngFile = new File([new Uint8Array(1024)], 'img.png', { type: 'image/png' });
    comp.setImageFile(pngFile);
    fixture.detectChanges();
    expect(comp.imageError()).toBeNull();
  });

  it('should accept a WebP file', () => {
    const webpFile = new File([new Uint8Array(1024)], 'img.webp', { type: 'image/webp' });
    comp.setImageFile(webpFile);
    fixture.detectChanges();
    expect(comp.imageError()).toBeNull();
  });

  it('should block submit (isFormAndImageValid=false) when no image is attached but the rest is valid', () => {
    comp.form.get('caseType')!.setValue(CaseType.RETURN);
    comp.form.get('category')!.setValue('SMARTPHONES');
    comp.form.get('modelName')!.setValue('Galaxy S24');
    comp.form.get('purchaseDate')!.setValue(new Date('2024-03-01'));
    // no image set — imageFile signal stays null
    fixture.detectChanges();
    // Full form validity (including image) must be false
    expect(comp.isFormAndImageValid).toBeFalse();
  });

  // ─── Category options come from metadata ─────────────────────────────────

  it('should populate categories from the mocked metadata call', () => {
    const cats = comp.categories();
    expect(cats.some((c) => c.code === 'SMARTPHONES')).toBeTrue();
    expect(cats.some((c) => c.label === 'Laptopy')).toBeTrue();
  });

  // ─── Submit → navigate to chat ────────────────────────────────────────────

  it('should navigate to /chat/:sessionId on successful submit', () => {
    fillValidComplaintForm(fixture);
    comp.onSubmit();
    expect(mockCaseService.submitCase).toHaveBeenCalledTimes(1);
    expect(mockStateService.setLastCaseResponse).toHaveBeenCalledWith(MOCK_RESPONSE);
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/chat', 'sess-xyz']);
  });

  it('should store the CaseResponse in CaseStateService on successful submit', () => {
    fillValidComplaintForm(fixture);
    comp.onSubmit();
    expect(mockStateService.setLastCaseResponse).toHaveBeenCalledWith(MOCK_RESPONSE);
  });

  // ─── Submit → error handling ─────────────────────────────────────────────

  it('should show a Polish error message when 400 is returned', () => {
    const err: CaseServiceError = {
      displayMessage: 'Wystąpiły błędy walidacji formularza.',
      code: 'VALIDATION_ERROR',
      retriable: false,
    };
    mockCaseService.submitCase.and.returnValue(throwError(() => err));
    fillValidComplaintForm(fixture);
    comp.onSubmit();
    fixture.detectChanges();
    expect(comp.submitError()).toBe('Wystąpiły błędy walidacji formularza.');
    expect(mockRouter.navigate).not.toHaveBeenCalled();
  });

  it('should show a re-upload hint on 422 IMAGE_UNREADABLE', () => {
    const err: CaseServiceError = {
      displayMessage: 'Nie udało się przeanalizować zdjęcia. Prześlij wyraźniejsze zdjęcie urządzenia.',
      code: 'IMAGE_UNREADABLE',
      retriable: false,
    };
    mockCaseService.submitCase.and.returnValue(throwError(() => err));
    fillValidComplaintForm(fixture);
    comp.onSubmit();
    fixture.detectChanges();
    expect(comp.submitError()).toContain('wyraźniejsze zdjęcie');
  });

  it('should show a retriable error message on 503 LLM_UNAVAILABLE', () => {
    const err: CaseServiceError = {
      displayMessage: 'Usługa AI jest chwilowo niedostępna. Spróbuj ponownie za chwilę.',
      code: 'LLM_UNAVAILABLE',
      retriable: true,
    };
    mockCaseService.submitCase.and.returnValue(throwError(() => err));
    fillValidComplaintForm(fixture);
    comp.onSubmit();
    fixture.detectChanges();
    expect(comp.submitError()).toContain('Spróbuj ponownie');
  });

  it('should not expose raw error codes in the displayed error message', () => {
    const err: CaseServiceError = {
      displayMessage: 'Wystąpiły błędy walidacji formularza.',
      code: 'VALIDATION_ERROR',
      retriable: false,
    };
    mockCaseService.submitCase.and.returnValue(throwError(() => err));
    fillValidComplaintForm(fixture);
    comp.onSubmit();
    fixture.detectChanges();
    const displayed = comp.submitError();
    expect(displayed).not.toContain('VALIDATION_ERROR');
  });

  it('should re-enable form controls after an error', () => {
    const err: CaseServiceError = {
      displayMessage: 'Błąd serwera.',
      retriable: false,
    };
    mockCaseService.submitCase.and.returnValue(throwError(() => err));
    fillValidComplaintForm(fixture);
    comp.onSubmit();
    fixture.detectChanges();
    expect(comp.form.enabled).toBeTrue();
  });
});
