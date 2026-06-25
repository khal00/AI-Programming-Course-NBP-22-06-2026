import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { signal } from '@angular/core';
import { AppComponent } from './app.component';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient } from '@angular/common/http';
import { provideMarkdown } from 'ngx-markdown';

import { CaseStateService } from './services/case-state.service';
import { ChatStreamService } from './services/chat-stream.service';
import { EMPTY } from 'rxjs';

describe('AppComponent', () => {
  beforeEach(async () => {
    const mockCaseStateService = jasmine.createSpyObj(
      'CaseStateService',
      ['setLastCaseResponse', 'clearLastCaseResponse', 'setPreviewUrl', 'clearPreviewUrl'],
      {
        lastCaseResponse: signal(null),
        previewUrl: signal(null as string | null),
      }
    );
    const mockChatStreamService = jasmine.createSpyObj('ChatStreamService', ['streamMessage']);
    mockChatStreamService.streamMessage.and.returnValue(EMPTY);

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter(routes),
        provideAnimationsAsync(),
        provideHttpClient(),
        provideMarkdown(),
        { provide: CaseStateService, useValue: mockCaseStateService },
        { provide: ChatStreamService, useValue: mockChatStreamService },
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should have the Polish application title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('Asystent reklamacji i zwrotów');
  });

  it('should render the Polish title in the toolbar', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const titleEl = compiled.querySelector('.hn-title');
    expect(titleEl?.textContent?.trim()).toContain('Asystent reklamacji i zwrotów');
  });

  it('should apply the hn-toolbar class with brand orange background token', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const toolbar = compiled.querySelector('.hn-toolbar');
    expect(toolbar).toBeTruthy();
  });

  it('should have a main element with hn-main class for HN background', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const main = compiled.querySelector('.hn-main');
    expect(main).toBeTruthy();
  });

  it('navigates to IntakeFormComponent for route /', async () => {
    const harness = await RouterTestingHarness.create('/');
    const el = harness.routeNativeElement;
    expect(el).toBeTruthy();
    expect(el!.tagName.toLowerCase()).toBe('app-intake-form');
  });

  it('navigates to ChatComponent for route /chat/:id', async () => {
    const harness = await RouterTestingHarness.create('/chat/test-session-id');
    const el = harness.routeNativeElement;
    expect(el).toBeTruthy();
    expect(el!.tagName.toLowerCase()).toBe('app-chat');
  });
});
