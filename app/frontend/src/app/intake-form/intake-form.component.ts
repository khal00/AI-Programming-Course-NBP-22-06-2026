import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-intake-form',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="placeholder-page">
      <h1>Formularz zgłoszenia reklamacji lub zwrotu</h1>
      <p class="placeholder-note">Formularz zostanie zaimplementowany w kolejnym kroku.</p>
    </div>
  `,
  styles: [`
    .placeholder-page {
      padding: 16px;
    }
    h1 {
      font-size: 14pt;
      font-weight: 700;
      margin: 0 0 8px;
    }
    .placeholder-note {
      color: #828282;
      font-size: 10pt;
    }
  `]
})
export class IntakeFormComponent {}
