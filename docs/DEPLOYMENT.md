# Deployment

MaterialBook ships as **one application image**: a Spring Boot jar that contains the compiled web app (React SPA) and the API, plus PostgreSQL. There is no nginx container. The app serves `/` (the SPA, with deep-link fallback), `/assets/**`, `/api/**` and `/actuator/health` itself, with the security headers, compression and cache rules that nginx used to apply.

```
browser --HTTP(S)--> app (Spring Boot: SPA + API, port 8080 or 8443) --JDBC--> postgres (internal network only)
                      |-- ./uploads (bind mount, attachments)    |-- ./certs (bind mount, optional TLS key + cert)
```

## Build the image
```
docker build -t materialbook .          # build context = repo root; Dockerfile is at the root
```
Three stages: `node:22-alpine` (`npm ci && npm run build` in `frontend/`) -> `maven:3.9-eclipse-temurin-21` (the built `dist/` is copied to `src/main/resources/static/`, then `mvn package -DskipTests`, with a BuildKit Maven cache) -> `eclipse-temurin:21-jre` runtime (non-root uid/gid 10001, `/app/uploads`, health check). Never add `mvn dependency:go-offline` to the Dockerfile (it hung for 30+ minutes). `docker compose build` builds the same image.

## Run with docker compose (recommended)
```
cp .env.example .env && scripts/gen-secrets.sh     # paste the output (DB_PASSWORD, JWT_SECRET, BOOTSTRAP_ADMIN_PASSWORD) into .env
mkdir -p uploads                                    # (certs/ ships with the repo, empty unless you use built-in HTTPS) Linux: sudo chown 10001:10001 uploads && sudo chmod 700 uploads
docker compose up -d --build                        # services: app + postgres  ->  http://localhost:8080
```
Plain HTTP is the default. **The refresh cookie is `Secure`**, so with plain HTTP either use `http://localhost` (Chrome/Firefox accept Secure cookies there, Safari does not) or set `AUTH_COOKIE_SECURE=false` in `.env` (only when no TLS is involved at all). Without that, login succeeds but a page reload logs you out.

Services: `app` (published `${APP_PORT:-8080}:${SERVER_PORT:-8080}`, read-only root fs + tmpfs `/tmp`, `cap_drop: ALL`, `no-new-privileges`, 1 GB, `SPRING_PROFILES_ACTIVE=prod`) and `postgres` (volume `pgdata`, `internal` network with no outbound access, never published). `docker-compose.dev.yml` is Postgres only (loopback 5432) for running the backend/frontend on the host. `docker-compose.local.yml` is an empty no-op kept for old command lines.

### HTTPS option A: built-in TLS (Spring Boot PEM support)
Put `fullchain.pem` and `privkey.pem` in `./certs` (override with `CERTS_DIR`), then in `.env`:
```
APP_PORT=443
SERVER_PORT=8443
SERVER_SSL_ENABLED=true
SERVER_SSL_CERTIFICATE=/certs/fullchain.pem
SERVER_SSL_CERTIFICATE_PRIVATE_KEY=/certs/privkey.pem
AUTH_COOKIE_SECURE=true
```
TLS 1.3 + 1.2 only (`server.ssl.enabled-protocols`). HSTS (`max-age=31536000; includeSubDomains`) is sent on HTTPS responses. The container health check switches to `https://127.0.0.1:$SERVER_PORT` automatically (certificate not verified; it is the container probing itself). A dev certificate: `scripts/gen-dev-cert.sh [DNS:name IP:addr ...]` writes `certs/` (git-ignored, key mode 600). There is no HTTP->HTTPS redirect (only one port is served); if you need one, use option B. **Linux:** bind-mounted files keep their host owner, so the key (mode 600) must be owned by or readable to uid 10001: `sudo chown 10001 certs/privkey.pem`. Certificate renewal = replace the files and `docker compose restart app`.

### HTTPS option B: your own reverse proxy / load balancer terminates TLS
Keep `SERVER_SSL_ENABLED=false` (plain HTTP on 8080), set `FORWARD_HEADERS_STRATEGY=native` in `.env` (compose defaults to `none`, which ignores `X-Forwarded-*`) and publish it only to the proxy, e.g. `APP_PORT=127.0.0.1:8080` in `.env` (compose then publishes `127.0.0.1:8080:8080`; a proxy on another host needs the host's LAN address or a firewall rule instead). The proxy must:
* send `Host`, `X-Forwarded-Proto: https`, `X-Forwarded-For` (with `native`, Tomcat honours these only when the immediate peer is on a private/loopback address, `server.tomcat.remoteip.internal-proxies`; `application.yml` itself defaults to `native`, docker-compose.yml to `none`). Without `X-Forwarded-Proto` the app thinks it is on HTTP: no HSTS, and same-origin POSTs can be rejected as cross-origin. Optional safety net: `CORS_ALLOWED_ORIGIN=https://your.domain`.
* **overwrite**, not append, any client-supplied `X-Forwarded-For`/`Forwarded` (otherwise a client can pick its own IP for the login rate limit);
* allow request bodies of at least 12 MB (uploads) and keep `AUTH_COOKIE_SECURE=true`.
The API's per-IP rate limit (`/api/v1/auth/**`, 30/min) keys on the connection address as seen after that processing.

## Run the image on its own (`docker run`)
The image only needs a reachable Postgres 16. Verified example with its own network and Postgres container (replace the passwords):
```
docker build -t materialbook .
docker network create mbnet
docker run -d --name mbpg --network mbnet -e POSTGRES_DB=materialbook -e POSTGRES_USER=materialbook -e POSTGRES_PASSWORD=<db-pass> \
  -v mbpgdata:/var/lib/postgresql/data postgres:16-alpine
mkdir -p uploads        # Linux: sudo chown 10001:10001 uploads && sudo chmod 700 uploads
docker run -d --name materialbook --network mbnet -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod -e DB_URL=jdbc:postgresql://mbpg:5432/materialbook -e DB_USER=materialbook -e DB_PASSWORD=<db-pass> \
  -e JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')" -e BOOTSTRAP_ADMIN_MOBILE=9999999999 -e BOOTSTRAP_ADMIN_PASSWORD=<admin-pass> \
  -e AUTH_COOKIE_SECURE=false \
  -v "$PWD/uploads:/app/uploads" --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges \
  materialbook
```
(Generate `JWT_SECRET` once and keep it: a new value logs everybody out.) `AUTH_COOKIE_SECURE=false` is for plain HTTP only. For built-in HTTPS add `-p 443:8443 -e SERVER_PORT=8443 -e SERVER_SSL_ENABLED=true -e SERVER_SSL_CERTIFICATE=/certs/fullchain.pem -e SERVER_SSL_CERTIFICATE_PRIVATE_KEY=/certs/privkey.pem -v "$PWD/certs:/certs:ro"` (and drop `-p 8080:8080` and `AUTH_COOKIE_SECURE=false`). Note: published ports do not work for a container attached only to a compose `internal: true` network; that is why the example uses a normal network.

## Environment variables
| Variable | Default | Meaning |
|---|---|---|
| `DB_NAME` `DB_USER` `DB_PASSWORD` | materialbook / materialbook / required | Postgres credentials (compose builds `DB_URL` from `DB_NAME`) |
| `DB_URL` | set by compose | JDBC URL (required for `docker run`) |
| `JWT_SECRET` | required | HS256 secret, >= 32 bytes; the app refuses to start otherwise |
| `BOOTSTRAP_ADMIN_MOBILE` `_PASSWORD` `_NAME` | - | First Super Admin (created when none exists; password change forced) |
| `APP_PORT` | 8080 | Host port published by compose |
| `SERVER_PORT` | 8080 | Port inside the container (8443 for built-in HTTPS) |
| `SERVER_SSL_ENABLED` | false | Built-in HTTPS |
| `SERVER_SSL_CERTIFICATE` `SERVER_SSL_CERTIFICATE_PRIVATE_KEY` | - | PEM paths inside the container (`/certs/...`) |
| `CERTS_DIR` / `UPLOADS_DIR` | ./certs / ./uploads | Host folders mounted at `/certs` (read-only) and `/app/uploads` |
| `AUTH_COOKIE_SECURE` | true | `Secure` flag of the refresh cookie (prod profile defaults to true, now overridable) |
| `FORWARD_HEADERS_STRATEGY` | none (compose) | `native` behind your own TLS proxy; `none` when the port is reached directly (prevents `X-Forwarded-For` spoofing of the login rate limit) |
| `CORS_ALLOWED_ORIGIN` | blank in prod | Only for a front-end on another origin |
| `APP_STORAGE_DIR` | ./uploads (/app/uploads in Docker) | Upload folder |
| `DB_POOL_SIZE` | 10 | Hikari pool size |
| `BACKUP_DIR` `BACKUP_RETENTION_DAYS` `BACKUP_PASSPHRASE_FILE` | see `.env.example` | Used by `scripts/backup.sh` |

## What the app (not nginx) enforces now
Security headers on every response (CSP `default-src 'self'` ..., `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: same-origin`, `Permissions-Policy`, `Cross-Origin-Opener-Policy`, `Cross-Origin-Resource-Policy`, HSTS on HTTPS); the attachment/branding/product-image endpoints keep their own `default-src 'none'; sandbox`. Cache rules: `/assets/**` immutable 1 year; `index.html`, `sw.js`, `workbox-*.js`, manifest, icons `no-cache`. gzip for text/JSON/SVG. No `Server` header. Request header limit 16 KB, upload caps 10 MB file / 12 MB request, Tomcat swallow cap 13 MB. `/actuator/health` is public (status only); everything else under `/actuator`, Swagger and API docs are not served in prod. Per-IP limit on `/api/v1/auth/**` stays in the app.

**No longer provided** (add a reverse proxy/WAF/CDN if you want them): nginx request-rate zones for the rest of `/api` (30 req/s/IP) and the per-IP connection cap (100), slow-client timeouts tuned in nginx, the automatic HTTP->HTTPS redirect on port 80, and nginx's JSON 429 for auth bursts (the app answers 429 problem+json itself). Tomcat's own connection/thread limits and the 1 GB container limit remain.

## File storage (attachments, material images)
Files are stored on the server's local disk. The app writes to `app.storage.dir` (`APP_STORAGE_DIR`, default `./uploads`, resolved against the working directory). In Docker the working directory is `/app`, so files go to `/app/uploads`, bind-mounted from **`./uploads` next to the compose file** (`UPLOADS_DIR` to change). Layout `uploads/yyyy/MM/<uuid>` (no original names), files mode 600, folders 700, only ever served through the authenticated API. On Linux: `mkdir -p uploads && sudo chown 10001:10001 uploads && sudo chmod 700 uploads`. If the folder is not writable the app refuses to start (`File storage directory '...' cannot be created or is not writable`).

Operator notes: monitor free disk (10 MB per file, 10 files per record); uploads are NOT virus-scanned; the folder is part of the data: **always back it up with the database** and never restore only the DB onto an empty uploads folder (the content endpoint answers 404 for such orphan rows).

## Backups
`scripts/backup.sh` (cron daily) writes into `backups/`: `materialbook-<UTC>.sql.gz` (database, via `docker compose exec postgres pg_dump`) and `materialbook-<UTC>-uploads.tar.gz` (the uploads folder, read through the `app` container because the files are owned by uid 10001). With `BACKUP_PASSPHRASE_FILE` (mode-600 file) both become `.enc` (AES-256). Files older than `BACKUP_RETENTION_DAYS` are pruned. Copy `backups/` off the server yourself. `scripts/restore.sh backups/materialbook-<ts>.sql.gz[.enc]` stops `app`, restores DB and uploads after a typed confirmation, and starts `app` again (`RESTORE_SKIP_UPLOADS=1` for old DB-only backups). Test a restore periodically.

## Upgrade procedure
```
scripts/backup.sh                       # always first
git pull
docker compose up -d --build --remove-orphans     # rebuilds the image, recreates app only; Flyway migrates on start
docker compose ps                       # app + postgres healthy
```
Users pick up the new web app on their next load (`index.html`/`sw.js` are `no-cache`; the PWA updates itself). Roll back: check out the previous version, rebuild, restore the backup if a migration ran. **Migrating from the old 3-container (nginx) layout:** move `nginx/certs/*.pem` to `certs/`, add the HTTPS lines above (or keep nginx's role in your own proxy), run `docker compose up -d --build --remove-orphans` (removes the old `nginx`/`backend` containers; the `pgdata` volume and `uploads/` are untouched). The old `ALLOWED_ORIGIN` variable is no longer used.

## Without Docker (jar on a host with JDK 21)
```
cd frontend && npm ci && npm run build && cd ..
mkdir -p backend/src/main/resources/static && rm -rf backend/src/main/resources/static/* && cp -R frontend/dist/. backend/src/main/resources/static/
cd backend && mvn -B package -DskipTests          # target/materialbook-backend-*.jar now contains the web app
# run from the deployment directory (uploads/ is created there) with the variables above exported:
java -jar target/materialbook-backend-0.1.0-SNAPSHOT.jar
```
`backend/src/main/resources/static/` is a build artifact and git-ignored; rebuild the frontend and copy again after every UI change. Local development keeps the two-process flow: Postgres via `docker compose -f docker-compose.dev.yml up -d`, backend `mvn spring-boot:run` (no static files needed), frontend `npm run dev` (Vite on :5173 proxies `/api` to :8080; no CORS involved).

## Known limits
Linux bind-mount ownership (uploads, certs key vs uid 10001) is documented, not tested on a Linux host. A self-signed certificate stops the browser registering the PWA service worker (use a real certificate to test installability).
