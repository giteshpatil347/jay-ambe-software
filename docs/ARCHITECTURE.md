# MaterialBook — Architecture & Standards (final)

Single-business, multi-user material purchase / dispatch / inventory / expense app.
React + TypeScript + Ant Design (web + mobile PWA) → Spring Boot 3 (Java 21) modular monolith → PostgreSQL 16 + local-disk file storage. INR only. One Docker image (Spring Boot serving the compiled SPA) + Postgres via Docker Compose.
Status of work: see `docs/PROGRESS.md`. Deviations from the original proposal: PROGRESS.md Decision Log.

## 1. Business rules (non-negotiable)
1. Direct save, no approval flow; users act only within their permissions.
2. IN increases stock, OUT decreases it. OUT can never exceed stock (DB `CHECK quantity >= 0` + row lock).
3. Edit = reverse old movements + post new ones, in one DB transaction. Cancel = reason required + reversal movements; original rows preserved; double-cancel rejected.
4. Cancelling an IN is rejected if that would make stock negative.
5. All writes audited (`audit_logs`, append-only). All money INR `NUMERIC(14,2)`, quantities `NUMERIC(14,3)`.
6. Numbers: `MIN-YYYY-00001`, `MOUT-…`, `EXP-…`, `PAY-…` from SQL `next_document_number()`.
7. **Entry units (D15)**: a product has exactly one unit (TON|KG|PIECE) and stock is counted in it. Material IN/OUT items may carry an optional `unit` the quantity was typed in; `material.UnitConverter` converts it to the product unit (1 TON = 1000 KG, scale 3, HALF_UP; PIECE only with PIECE; a result <= 0 or an unknown unit is a 422 on that item). Only the converted quantity is stored/stocked/reported; the rate is always per product unit, so `amount = stored quantity × rate` needs no rounding of the rate.

## 2. Modules (package-by-feature, `com.materialbook.*`)
`config, security, auth, users, roles, permissions, products, parties, employees, material, inventory, expenses, payments, reports, attachments, audit, common`.
Each module: `controller / service / repository / entity / dto` (mapper only if non-trivial; prefer records as DTOs + MapStruct-free manual mapping). **Cross-module calls go through the other module's `service` only, never its repository.** Only `inventory.StockService` may write `stock_balances` / `stock_movements`.

## 3. Database (optimized — implemented in V1–V3)
- 24 tables. FK columns all indexed; partial indexes on `status='POSTED'`; BRIN on `audit_logs.created_at`.
- **Stock**: `stock_balances(product_id PK, quantity CHECK>=0)` is the lock target; `stock_movements.quantity_delta` is signed; invariant `balance == SUM(delta)` (tested). Lock order: `ORDER BY product_id FOR UPDATE` (prevents deadlocks).
- `material_transaction_items.amount` = generated column. No `total_quantity`.
- Optimistic locking (`version`) on transactions and expenses → concurrent edit returns 409.
- Ledger (positive = party owes us): `opening_balance + Σ OUT − Σ IN − Σ RECEIVED + Σ PAID` over POSTED rows (corrected from the V1 comment, see D11).
- Lockout via `users.failed_login_count` + `locked_until`.
- **Expenses/employees (V9, D14)**: employee salary is MONTHLY; `employee_salary_revisions(employee_id, effective_from)` is the source of truth (salary on a date = latest revision with `effective_from <= date`, resolved by query at read time; `employees.monthly_salary` only mirrors today's value). Salary payment types: MONTHLY, ADHOC, ADVANCE, SETTLEMENT (no period/payment mode/voucher stored). Electricity expenses store `usage_type` + `usage_quantity` (hours or days) next to the rupee amount; the old usage_start/usage_end/units/rate columns are legacy and unused.
- Schema changes after V3: new Flyway files only (`V4__…`). **Never edit an applied migration.**

## 4. API conventions
- Base `/api/v1`, JSON, camelCase. Pagination `?page=0&size=20&sort=…`, response `{content,page,size,totalElements}`. Date filters `from`,`to` (ISO date).
- Errors: RFC 7807 `application/problem+json` with `code`, `message`, `fieldErrors[]`. 401/403/404/409(conflict, insufficient stock)/422(validation).
- Money & quantity serialized as **strings** (no float loss); computed amounts always server-side.
- Endpoints per original table plus: `POST /auth/logout`, `GET/POST /payments`, `GET /parties/{id}/ledger`, `GET /audit-logs`, `GET /attachments/{id}/content`, `GET /products/{id}/image`, `PUT /material-in/{id}` (edit), roles/permissions CRUD.
- Authorization: `@PreAuthorize("hasAuthority('MATERIAL_IN.CREATE')")` on every controller method; default deny.

## 5. Security design
- **Passwords**: BCrypt(12), min length 10, `password_change_required` on first login. 
- **Access token**: JWT HS256 (secret ≥256 bit from env), 10 min, claims `sub`, `perms[]`. Held **in memory** on the client only. Permission changes take effect ≤10 min (or on next refresh).
- **Refresh token**: random 256-bit, only SHA-256 stored, rotated every use, `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`. Reuse of a rotated token revokes the whole `family_id`. Refresh/logout require header `X-Requested-With: MaterialBook` (CSRF defence in depth); the rest of the API uses bearer tokens so CSRF is N/A.
- **Forced password change** (T19): while `password_change_required` is set, the access token carries no permissions, so only `/auth/me` and `/auth/change-password` work. Prod profile (`application-prod.yml`) disables Swagger/API docs. Details and all findings: `docs/SECURITY_REVIEW.md`.
- **Brute force**: 5 failed logins → `locked_until = now+15m`; plus per-IP rate limit on `/auth/*` (Bucket4j, Apache-2.0, in-memory is fine for single instance). Generic error message for bad user/password.
- **Authorization**: backend-enforced; frontend hides only for UX. Super Admin role assignable only by Super Admin; last Super Admin cannot be deactivated.
- **Dashboard data** (D13): `GET /reports/dashboard` is built per the caller's authorities. `DASHBOARD.FINANCIAL` (SUPER_ADMIN, ADMIN, ACCOUNTANT) gates expenses, receivable/payable and the recent-transactions list; `MATERIAL_IN.VIEW`/`MATERIAL_OUT.VIEW`/`INVENTORY.VIEW` gate the IN, OUT and low-stock figures. Hidden sections are absent from the JSON and their SQL is not run. The system role VIEWER (V8) holds only DASHBOARD.VIEW, MATERIAL_IN.VIEW, MATERIAL_OUT.VIEW, INVENTORY.VIEW, PRODUCT.VIEW.
- **Files**: stored on local disk under `app.storage.dir` (`APP_STORAGE_DIR`, default `./uploads` resolved against the application's working directory; in Docker `/app/uploads`, bind-mounted from `./uploads` next to `docker-compose.yml`). No S3/cloud (D13). Key = `yyyy/MM/<uuid>` (never the user's file name); `LocalFileStorage` resolves every key against the root and rejects `..`, absolute paths and symlinks leaving the root, writes atomically (temp file + atomic move), files 600 / dirs 700, no listing, fails fast at startup if the root is not writable. Allow-list JPG/PNG/PDF, ≤10 MB (material image ≤5 MB, JPG/PNG/WEBP), content verified by magic bytes (Apache Tika, Apache-2.0). Files are never served by URL: only `GET /attachments/{id}/content` and `GET /products/{id}/image` stream the bytes through the app (owner-VIEW / PRODUCT.VIEW; `nosniff`, `default-src 'none'; sandbox`, `Cache-Control: private, max-age=300`; the SPA fetches them as blobs so no token is ever in a URL); original name only in `Content-Disposition`. Delete removes the DB row first, then the file (an orphan file on failure is logged, never a dangling row).
- **Transport/headers** (in the app, D16): Spring Security writes on every response `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; ...; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: same-origin`, `Permissions-Policy`, `Cross-Origin-Opener-Policy/Resource-Policy: same-origin`, HSTS on HTTPS requests; controllers that stream user files set their own stricter CSP, which wins (tested). TLS is either built in (`SERVER_SSL_ENABLED`, Spring Boot PEM, TLS 1.2/1.3) or terminated by the operator's proxy (`forward-headers-strategy=native`). The SPA and the API share one origin, so CORS is not needed (`CORS_ALLOWED_ORIGIN` blank in prod). Static files: `/assets/**` immutable, `index.html`/`sw.js`/manifest `no-cache`, SPA fallback only for non-API paths without a file extension; unknown `/api/**` stay problem+json 401/404. No edge rate limiting beyond the in-app `/auth/**` limiter (see SECURITY_REVIEW section 8).
- **Data**: JPA/parameterized queries only; Bean Validation on every DTO; no secrets/tokens/passwords in logs; Actuator exposes only `health` (and not publicly). DB not publicly reachable; daily `pg_dump` + tar of the uploads folder (`scripts/backup.sh`), copied off-server by the operator.
- **Secrets**: env vars (or your platform secret store); `.env` git-ignored; `.env.example` only placeholders.

## 6. License policy (everything must be free for commercial use)
Allowed: Apache-2.0, MIT, BSD, EPL, MPL, SIL-OFL (fonts), PostgreSQL license. **Forbidden**: AGPL/GPL, SSPL, commercial/“community with limits” libs.
- PDF: **Apache PDFBox 3.x** + **Noto Sans Regular/Bold** embedded (`PDType0Font.load`, subset) so `₹` renders. Small in-house `PdfTableWriter` (header, repeating column headings, page numbers, totals) — no templating engine.
- CSV export: hand-written writer (escape `=`,`+`,`-`,`@` leading cells → CSV-injection safe).
- Enforce in CI: `license-maven-plugin` (backend) and `license-checker --onlyAllow` (frontend).
- Ant Design: MIT. TanStack Query, React Hook Form, Vite, vite-plugin-pwa: MIT. Flyway Community, Testcontainers, springdoc: Apache-2.0/MIT.

## 7. Frontend standards (web + mobile)
- Vite + React 18 + TypeScript (strict) + Ant Design 5 + TanStack Query + React Hook Form + Zod, installable PWA (`vite-plugin-pwa`, offline shell only — **never cache API responses or queue writes offline**).
- **Mobile-first responsive**: Grid breakpoints; sidebar `Layout.Sider` ≥ `lg`, bottom tab bar + `Drawer` menu < `lg`. Tables on ≥ `md`; **card lists on mobile** (`useBreakpoint`). Forms are single-column on mobile; modals become full-screen `Drawer`s. Min touch target 44 px; `inputMode="decimal"` on qty/rate; `viewport-fit=cover` + safe-area insets; no horizontal page scroll; test at 360×640 and 1280×800.
- Permission-driven menu and `<RequirePermission>` route guard; `usePermission().can()` for buttons. Currency via `Intl.NumberFormat('en-IN',{style:'currency',currency:'INR'})`.
- One reusable `TransactionForm` for IN/OUT (config prop). Material OUT shows live available stock per selected product and disables submit when exceeded (server still authoritative).
- Server state = TanStack Query only; no global store. Access token in memory; silent refresh on 401 with single in-flight refresh promise.

## 8. Testing standard
Backend: Testcontainers Postgres (no H2). Mandatory tests: Flyway applies cleanly; permission resolution; refresh reuse detection; concurrent OUT never oversells; edit/cancel reversal math; ledger formula; PDF generation smoke (₹ glyph present, non-empty). Frontend: Vitest + Testing Library for permission guard, `TransactionForm` totals, token refresh interceptor.

## 9. Deployment
One application image (root `Dockerfile`: node build of the SPA -> Maven package with the SPA in `src/main/resources/static` -> `eclipse-temurin:21-jre`, non-root uid/gid 10001, WORKDIR `/app`, `HEALTHCHECK` script for HTTP and HTTPS). `docker-compose.yml`: `app` (publishes `APP_PORT`:`SERVER_PORT`, default plain HTTP 8080; optional built-in HTTPS from `./certs`; read-only fs, `cap_drop: ALL`) and `postgres` (volume, internal network only, never published). Uploads: bind mount `./uploads:/app/uploads` (must be writable by uid 10001 on Linux). No nginx. Details: `docs/DEPLOYMENT.md`. Local dev: JDK 21 + `docker-compose.dev.yml` (Postgres) + `npm run dev` (Vite proxies `/api` to :8080).
