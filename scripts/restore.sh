#!/usr/bin/env bash
# Restore a backup made by backup.sh: the database AND the matching uploads archive. DESTROYS current database contents
# and replaces the contents of the uploads folder.
# Usage: scripts/restore.sh backups/materialbook-<ts>.sql.gz[.enc]
#   The uploads archive materialbook-<ts>-uploads.tar.gz[.enc] next to it is restored too. Backups made before uploads were
#   included (no such archive) need RESTORE_SKIP_UPLOADS=1, which leaves the current uploads folder untouched.
set -euo pipefail
cd "$(dirname "$0")/.."

FILE="${1:?usage: $0 <backup.sql.gz | backup.sql.gz.enc>}"
# Encrypted backups (see backup.sh) need the same passphrase file: BACKUP_PASSPHRASE_FILE=/path scripts/restore.sh <file>.enc
[ -f "$FILE" ] || { echo "No such file: $FILE" >&2; exit 1; }

case "$FILE" in
  *.sql.gz.enc) ENC=".enc" ;;
  *.sql.gz)     ENC="" ;;
  *) echo "Expected materialbook-<ts>.sql.gz or .sql.gz.enc" >&2; exit 1 ;;
esac
UPFILE="${FILE%.sql.gz*}-uploads.tar.gz$ENC"
if [ -n "$ENC" ]; then
  : "${BACKUP_PASSPHRASE_FILE:?set BACKUP_PASSPHRASE_FILE to restore an encrypted backup}"
  decode() { openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -pass "file:$BACKUP_PASSPHRASE_FILE" -in "$1"; }
else
  decode() { cat "$1"; }
fi
plain() { decode "$FILE" | gunzip; }
# Prove the files are readable (right passphrase, not corrupt) BEFORE anything is dropped. The whole stream is read once
# (also verifies the gzip checksum). Do NOT shorten this to `plain | head -c 1`: with pipefail the early exit of `head`
# SIGPIPEs the decryptor and a good backup is reported as unreadable.
BYTES=$(plain | wc -c) || { echo "Backup cannot be read (wrong passphrase or corrupt file)" >&2; exit 1; }
[ "$BYTES" -gt 0 ] || { echo "Backup is empty" >&2; exit 1; }
WITH_UPLOADS=1
if [ "${RESTORE_SKIP_UPLOADS:-}" = "1" ]; then
  WITH_UPLOADS=0
else
  [ -f "$UPFILE" ] || { echo "Uploads archive not found: $UPFILE (set RESTORE_SKIP_UPLOADS=1 to restore the database only)" >&2; exit 1; }
  UBYTES=$(decode "$UPFILE" | gunzip | wc -c) || { echo "Uploads archive cannot be read (wrong passphrase or corrupt file)" >&2; exit 1; }
  [ "$UBYTES" -gt 0 ] || { echo "Uploads archive is empty" >&2; exit 1; }
fi

echo "This will DROP and recreate the public schema, then load $FILE."
[ "$WITH_UPLOADS" = 1 ] && echo "It will also REPLACE the contents of the uploads folder with $UPFILE."
read -r -p "Type 'restore' to continue: " ANSWER
[ "$ANSWER" = "restore" ] || { echo "Aborted."; exit 1; }

docker compose stop app
docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"'
plain | docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
if [ "$WITH_UPLOADS" = 1 ]; then
  # Runs as the app's own uid (10001) so restored files keep the right owner; archive modes (600/700) are preserved.
  decode "$UPFILE" | gunzip | docker compose run --rm --no-deps -T --entrypoint sh app -c 'find /app/uploads -mindepth 1 -maxdepth 1 -exec rm -rf {} + && tar -xf - -C /app/uploads --no-same-owner'
fi
docker compose start app
echo "Restore complete."
