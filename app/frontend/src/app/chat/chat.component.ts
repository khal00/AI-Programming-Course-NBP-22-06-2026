import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="placeholder-page">
      <h1>Rozmowa z asystentem</h1>
      <p class="placeholder-note">
        Sesja: <strong>{{ sessionId }}</strong>
      </p>
      <p class="placeholder-note">Widok czatu zostanie zaimplementowany w kolejnym kroku.</p>
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
export class ChatComponent {
  sessionId = '';

  constructor(private route: ActivatedRoute) {
    this.sessionId = this.route.snapshot.paramMap.get('sessionId') ?? '';
  }
}
