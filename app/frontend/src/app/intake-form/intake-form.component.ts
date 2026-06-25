import {
  Component,
  OnInit,
  OnDestroy,
  signal,
  inject,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatRadioModule } from '@angular/material/radio';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';

import { CaseService, CaseServiceError } from '../services/case.service';
import { CaseStateService } from '../services/case-state.service';
import { CaseType, Option } from '../models/case.models';

// ─── Validators ───────────────────────────────────────────────────────────────

/** Rejects dates in the future. */
const noFutureDateValidator: ValidatorFn = (control: AbstractControl) => {
  const value = control.value;
  if (!value) return null;
  const selected = new Date(value);
  selected.setHours(0, 0, 0, 0);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return selected > today ? { futureDate: true } : null;
};

// ─── Constants ────────────────────────────────────────────────────────────────

const MAX_IMAGE_BYTES = 10_485_760; // 10 MB
const ALLOWED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

@Component({
  selector: 'app-intake-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatRadioModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatChipsModule,
  ],
  templateUrl: './intake-form.component.html',
  styleUrl: './intake-form.component.scss',
})
export class IntakeFormComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly caseService = inject(CaseService);
  private readonly stateService = inject(CaseStateService);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  // ─── Signals ─────────────────────────────────────────────────────────────

  readonly categories = signal<Option[]>([]);
  readonly imageFile = signal<File | null>(null);
  readonly imagePreviewUrl = signal<string | null>(null);
  readonly imageError = signal<string | null>(null);
  readonly submitError = signal<string | null>(null);
  readonly isSubmitting = signal(false);
  readonly today = new Date();

  // ─── Form ─────────────────────────────────────────────────────────────────

  readonly form = this.fb.group({
    caseType: [CaseType.COMPLAINT as CaseType, Validators.required],
    category: ['', Validators.required],
    modelName: ['', [Validators.required, Validators.minLength(1)]],
    purchaseDate: [null as Date | null, [Validators.required, noFutureDateValidator]],
    reason: [''],
  });

  /** Derived state — recalculated in the template and onSubmit guard. */
  get isFormAndImageValid(): boolean {
    return this.form.valid && this.imageFile() !== null && this.imageError() === null;
  }

  // ─── Lifecycle ────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadMetadata();
    this.watchCaseTypeForReason();
    // Set initial validator state
    this.updateReasonValidator(this.form.get('caseType')!.value as CaseType);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ─── Public API (used by template and tests) ──────────────────────────────

  /** Called by the file input change event and CDK drop. */
  setImageFile(file: File | null): void {
    if (!file) {
      this.clearImage();
      return;
    }
    const error = this.validateImageFile(file);
    this.imageError.set(error);
    if (error) {
      this.imageFile.set(null);
      this.imagePreviewUrl.set(null);
      return;
    }
    this.imageFile.set(file);
    this.buildPreview(file);
  }

  clearImage(): void {
    this.imageFile.set(null);
    this.imagePreviewUrl.set(null);
    this.imageError.set(null);
  }

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.setImageFile(file);
    // Reset input so the same file can be re-selected after removal
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0] ?? null;
    this.setImageFile(file);
  }

  onSubmit(): void {
    if (!this.isFormAndImageValid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitError.set(null);
    this.isSubmitting.set(true);
    this.form.disable();

    const fd = this.buildFormData();
    this.caseService
      .submitCase(fd)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.stateService.setLastCaseResponse(response);
          this.stateService.setPreviewUrl(this.imagePreviewUrl());
          this.router.navigate(['/chat', response.sessionId]);
        },
        error: (err: CaseServiceError) => {
          this.submitError.set(err.displayMessage);
          this.isSubmitting.set(false);
          this.form.enable();
        },
      });
  }

  // ─── Private helpers ──────────────────────────────────────────────────────

  private loadMetadata(): void {
    this.caseService
      .getMetadata()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (meta) => this.categories.set(meta.categories),
      });
  }

  private watchCaseTypeForReason(): void {
    this.form
      .get('caseType')!
      .valueChanges.pipe(takeUntil(this.destroy$))
      .subscribe((type) => {
        this.updateReasonValidator(type as CaseType);
      });
  }

  private updateReasonValidator(caseType: CaseType): void {
    const reasonCtrl = this.form.get('reason')!;
    if (caseType === CaseType.COMPLAINT) {
      reasonCtrl.setValidators([Validators.required]);
    } else {
      reasonCtrl.clearValidators();
    }
    reasonCtrl.updateValueAndValidity();
  }

  private validateImageFile(file: File): string | null {
    if (!ALLOWED_MIME_TYPES.includes(file.type)) {
      return 'Akceptowane formaty zdjęcia: JPEG, PNG, WebP. Wybierz plik w jednym z tych formatów.';
    }
    if (file.size > MAX_IMAGE_BYTES) {
      return 'Plik jest zbyt duży. Maksymalny rozmiar zdjęcia to 10 MB.';
    }
    return null;
  }

  private buildPreview(file: File): void {
    const reader = new FileReader();
    reader.onload = (e) => {
      this.imagePreviewUrl.set(e.target?.result as string ?? null);
    };
    reader.readAsDataURL(file);
  }

  private buildFormData(): FormData {
    const val = this.form.getRawValue();
    const fd = new FormData();
    fd.append('caseType', val.caseType ?? '');
    fd.append('category', val.category ?? '');
    fd.append('modelName', (val.modelName ?? '').trim());
    // Format date as yyyy-MM-dd
    const d = val.purchaseDate as Date;
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    fd.append('purchaseDate', iso);
    if (val.reason) {
      fd.append('reason', val.reason);
    }
    fd.append('image', this.imageFile()!);
    return fd;
  }
}
