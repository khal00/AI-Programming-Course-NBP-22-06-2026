# Frontend — Hardware Service Decision Copilot

Angular · Angular Material · ngx-markdown. Implements the UI in
[`docs/ADR/003-frontend.md`](../../docs/ADR/003-frontend.md). All user-facing text is **Polish**.

## Why this is a partial scaffold

The Angular CLI (`ng`) is not installed and this environment blocks the network commands needed to run
`ng new` / `npm install`. The reliable way to initialize an Angular workspace is the CLI itself (a
hand-written `angular.json`/`package.json` drifts and breaks). So this folder ships the pieces that are
stable to author by hand — the dev **proxy config** — plus the exact init steps below. Run them once to
materialize the workspace into this folder.

## Initialize the workspace (run once, needs network)

From `app/frontend`:

```bash
# 1) Create the Angular workspace in-place (standalone, routing, SCSS)
npx -y @angular/cli@latest new frontend \
  --directory . \
  --style=scss \
  --routing \
  --ssr=false \
  --standalone \
  --skip-git

# 2) Add Angular Material (pick a prebuilt theme + typography + animations)
npx -y @angular/cli@latest add @angular/material

# 3) Markdown rendering for agent messages
npm install ngx-markdown marked
```

Keep `proxy.conf.json` (already in this folder) and wire it into `ng serve`:

```bash
ng serve --proxy-config proxy.conf.json   # http://localhost:4200 -> backend :8080
```

> Tip: add `"proxyConfig": "proxy.conf.json"` to the `serve` options in `angular.json` so a plain
> `ng serve` uses it.

## What to build (per ADR-003)

- Routes: `/` → `IntakeFormComponent`, `/chat/:sessionId` → `ChatComponent`.
- `IntakeFormComponent` — reactive form with Material controls (radio case type, `mat-select` category,
  `matInput`, `mat-datepicker` with `max=today`, textarea with conditional-required reason, custom
  image upload with preview + client-side MIME/size validation). Submit → `POST /api/cases` (multipart).
- `ChatComponent` — message list, verdict chip (color per verdict), markdown-rendered agent messages,
  input, typing indicator, inline error/retry. First message comes from the submit response.
- `CaseService` — `submitCase()`, `getMetadata()`.
- `ChatStreamService` — `POST /api/cases/{id}/messages` consumed via **fetch + ReadableStream** (not
  `EventSource`, because the request carries a body); parses SSE `token`/`done`/`error`.

## Verification (per AGENTS.md)

```bash
npm test          # unit tests (form validation, services, streaming render)
ng build          # production build succeeds
```
