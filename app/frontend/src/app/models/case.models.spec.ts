import {
  CaseType,
  Verdict,
  VERDICT_LABELS,
  VERDICT_CHIP_COLORS,
  MetadataResponse,
  CaseResponse,
  ErrorResponse,
  ChatMessageVM,
  CaseSummary,
  FieldError,
  Option,
  CaseFormValue,
} from './case.models';

describe('CaseType enum', () => {
  it('should have COMPLAINT value', () => {
    expect(CaseType.COMPLAINT).toBe('COMPLAINT');
  });

  it('should have RETURN value', () => {
    expect(CaseType.RETURN).toBe('RETURN');
  });
});

describe('Verdict enum', () => {
  it('should have APPROVE value', () => {
    expect(Verdict.APPROVE).toBe('APPROVE');
  });

  it('should have REJECT value', () => {
    expect(Verdict.REJECT).toBe('REJECT');
  });

  it('should have NEEDS_INFO value', () => {
    expect(Verdict.NEEDS_INFO).toBe('NEEDS_INFO');
  });

  it('should have ESCALATE value', () => {
    expect(Verdict.ESCALATE).toBe('ESCALATE');
  });
});

describe('VERDICT_LABELS', () => {
  it('should map APPROVE to Polish label "Zatwierdzone"', () => {
    expect(VERDICT_LABELS[Verdict.APPROVE]).toBe('Zatwierdzone');
  });

  it('should map REJECT to Polish label "Odrzucone"', () => {
    expect(VERDICT_LABELS[Verdict.REJECT]).toBe('Odrzucone');
  });

  it('should map NEEDS_INFO to Polish label "Wymaga uzupełnienia"', () => {
    expect(VERDICT_LABELS[Verdict.NEEDS_INFO]).toBe('Wymaga uzupełnienia');
  });

  it('should map ESCALATE to Polish label "Eskalacja do specjalisty"', () => {
    expect(VERDICT_LABELS[Verdict.ESCALATE]).toBe('Eskalacja do specjalisty');
  });

  it('should have exactly 4 entries (one per verdict)', () => {
    expect(Object.keys(VERDICT_LABELS).length).toBe(4);
  });
});

describe('VERDICT_CHIP_COLORS', () => {
  it('should map APPROVE to "primary"', () => {
    expect(VERDICT_CHIP_COLORS[Verdict.APPROVE]).toBe('primary');
  });

  it('should map REJECT to "warn"', () => {
    expect(VERDICT_CHIP_COLORS[Verdict.REJECT]).toBe('warn');
  });

  it('should map NEEDS_INFO to "accent"', () => {
    expect(VERDICT_CHIP_COLORS[Verdict.NEEDS_INFO]).toBe('accent');
  });

  it('should map ESCALATE to undefined (neutral/default chip)', () => {
    expect(VERDICT_CHIP_COLORS[Verdict.ESCALATE]).toBeUndefined();
  });
});

describe('Type compatibility — contract JSON fixtures', () => {
  it('should accept a valid MetadataResponse from contract §1.3', () => {
    const metadata: MetadataResponse = {
      caseTypes: [
        { code: 'COMPLAINT', label: 'Reklamacja' },
        { code: 'RETURN', label: 'Zwrot' },
      ],
      categories: [
        { code: 'SMARTPHONES', label: 'Smartfony' },
        { code: 'LAPTOPS', label: 'Laptopy' },
        { code: 'TABLETS', label: 'Tablety' },
        { code: 'TVS', label: 'Telewizory' },
        { code: 'MONITORS', label: 'Monitory' },
        { code: 'HEADPHONES', label: 'Słuchawki' },
        { code: 'SMARTWATCHES', label: 'Smartwatche / opaski' },
        { code: 'GAME_CONSOLES', label: 'Konsole do gier' },
        { code: 'AUDIO', label: 'Sprzęt audio' },
        { code: 'SMALL_APPLIANCES', label: 'Drobne AGD' },
        { code: 'ACCESSORIES', label: 'Akcesoria' },
        { code: 'OTHER', label: 'Inne' },
      ],
    };
    expect(metadata.caseTypes.length).toBe(2);
    expect(metadata.categories.length).toBe(12);
  });

  it('should accept a valid CaseResponse from contract §1.1', () => {
    const caseSummary: CaseSummary = {
      caseType: CaseType.COMPLAINT,
      caseTypeLabel: 'Reklamacja',
      category: 'SMARTPHONES',
      categoryLabel: 'Smartfony',
      modelName: 'iPhone 15',
      purchaseDate: '2024-01-15',
    };
    const response: CaseResponse = {
      sessionId: 'abc-123',
      caseSummary,
      verdict: Verdict.APPROVE,
      firstMessage: '**Zatwierdzone** — urządzenie kwalifikuje się do reklamacji.',
    };
    expect(response.sessionId).toBe('abc-123');
    expect(response.verdict).toBe(Verdict.APPROVE);
    expect(response.caseSummary.caseType).toBe(CaseType.COMPLAINT);
  });

  it('should accept a valid ErrorResponse with fieldErrors from contract §2.1', () => {
    const fieldError: FieldError = { field: 'modelName', message: 'Pole jest wymagane.' };
    const error: ErrorResponse = {
      code: 'VALIDATION_ERROR',
      message: 'Wystąpiły błędy walidacji formularza.',
      fieldErrors: [fieldError],
    };
    expect(error.code).toBe('VALIDATION_ERROR');
    expect(error.fieldErrors!.length).toBe(1);
    expect(error.fieldErrors![0].field).toBe('modelName');
  });

  it('should accept an ErrorResponse without fieldErrors (422/503)', () => {
    const error: ErrorResponse = {
      code: 'IMAGE_UNREADABLE',
      message: 'Nie udało się przeanalizować zdjęcia. Prześlij wyraźniejsze zdjęcie urządzenia.',
    };
    expect(error.fieldErrors).toBeUndefined();
  });

  it('should accept a valid ChatMessageVM for assistant', () => {
    const msg: ChatMessageVM = {
      role: 'assistant',
      text: '**Zatwierdzone** — Twoja reklamacja wydaje się zasadna.',
      streaming: false,
    };
    expect(msg.role).toBe('assistant');
    expect(msg.streaming).toBeFalse();
  });

  it('should accept a ChatMessageVM with streaming=true and error', () => {
    const msg: ChatMessageVM = {
      role: 'assistant',
      text: '',
      streaming: false,
      error: 'Wystąpił błąd połączenia. Spróbuj ponownie.',
    };
    expect(msg.error).toBeDefined();
  });

  it('should accept a valid Option', () => {
    const option: Option = { code: 'SMARTPHONES', label: 'Smartfony' };
    expect(option.code).toBe('SMARTPHONES');
    expect(option.label).toBe('Smartfony');
  });

  it('should accept a CaseFormValue', () => {
    const formValue: CaseFormValue = {
      caseType: CaseType.COMPLAINT,
      category: 'LAPTOPS',
      modelName: 'ThinkPad X1',
      purchaseDate: new Date('2024-06-01'),
      reason: 'Ekran przestał działać.',
      imageFile: null,
    };
    expect(formValue.caseType).toBe(CaseType.COMPLAINT);
    expect(formValue.imageFile).toBeNull();
  });
});
