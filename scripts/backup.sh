#!/usr/bin/env bash
# Daily backup of the database (pg_dump) AND the uploaded files (tar of the app container's /app/uploads = ./uploads next to
# docker-compose.yml): gzip -> (optional AES-256 encryption) -> retention prune. Two files per run, same timestamp:
#   backups/materialbook-<ts>.sql.gz[.enc]          database
#   backups/materialbook-<ts>-uploads.tar.gz[.enc]  attachments + material images
# The uploads are read through the app container (their files are mode 600 owned by uid 10001, which the host user
# running this script usually cannot read). Copy backups/ off the server (rsync/rclone/object store of your choice).
# Cron example: 0 2 * * * cd /opt/material-book && ./scripts/backup.sh >> backups/backup.log 2>&1
#
# Encryption (recommended: dumps contain password hashes, audit trail and all business data):
#   BACKUP_PASSPHRASE_FILE=/path/to/file   file (mode 600, outside the repo) holding the passphrase; the file name, never the
#                                           passphrase, is on the command line, so it does not show up in `ps`.
# Decrypt manually:  openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -pass file:<passfile> -in X.sql.gz.enc | gunzip   (uploads: ... | tar -tzf -)
# (scripts/restore.sh does this itself when BACKUP_PASSPHRASE_FILE is set.)
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."

# Read only the keys we need from .env; never `source` it (a value with $(...) or backticks would be executed).
env_get() { [ -f .env ] && grep -E "^$1=" .env | tail -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//' || true; }
BACKUP_DIR="${BACKUP_DIR:-$(env_get BACKUP_DIR)}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-$(env_get BACKUP_RETENTION_DAYS)}"
BACKUP_PASSPHRASE_FILE="${BACKUP_PASSPHRASE_FILE:-$(env_get BACKUP_PASSPHRASE_FILE)}"

DIR="${BACKUP_DIR:-./backups}"
DAYS="${BACKUP_RETENTION_DAYS:-14}"
case "$DAYS" in (*[!0-9]*|'') echo "BACKUP_RETENTION_DAYS must be a number" >&2; exit 1;; esac
mkdir -p "$DIR"
chmod 700 "$DIR"

ENC=""
if [ -n "${BACKUP_PASSPHRASE_FILE:-}" ]; then
  [ -r "$BACKUP_PASSPHRASE_FILE" ] || { echo "Cannot read BACKUP_PASSPHRASE_FILE" >&2; exit 1; }
  [ -z "$(find "$BACKUP_PASSPHRASE_FILE" \( -perm -040 -o -perm -020 -o -perm -010 -o -perm -004 -o -perm -002 -o -perm -001 \) )" ] || { echo "BACKUP_PASSPHRASE_FILE must not be group/world accessible (chmod 600)" >&2; exit 1; }
  ENC=".enc"
else
  echo "WARNING: BACKUP_PASSPHRASE_FILE not set - backup is NOT encrypted at rest" >&2
fi
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DB_FILE="$DIR/materialbook-$STAMP.sql.gz$ENC"
UP_FILE="$DIR/materialbook-$STAMP-uploads.tar.gz$ENC"
trap 'rm -f "$DB_FILE.tmp" "$UP_FILE.tmp"' EXIT

# stdin -> $1.tmp, encrypted when a passphrase file is configured
sink() {
  if [ -n "$ENC" ]; then openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt -pass "file:$BACKUP_PASSPHRASE_FILE" > "$1.tmp"; else cat > "$1.tmp"; fi
}
dump_db() { docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' | gzip; }
dump_uploads() { docker compose run --rm --no-deps -T --entrypoint tar app -C /app/uploads -czf - .; }

dump_db | sink "$DB_FILE"
dump_uploads | sink "$UP_FILE"
mv "$DB_FILE.tmp" "$DB_FILE"
mv "$UP_FILE.tmp" "$UP_FILE"
chmod 600 "$DB_FILE" "$UP_FILE"
echo "Backup written: $DB_FILE"
echo "Backup written: $UP_FILE"

find "$DIR" \( -name 'materialbook-*.sql.gz' -o -name 'materialbook-*.sql.gz.enc' -o -name 'materialbook-*-uploads.tar.gz' -o -name 'materialbook-*-uploads.tar.gz.enc' \) -type f -mtime +"$DAYS" -delete
