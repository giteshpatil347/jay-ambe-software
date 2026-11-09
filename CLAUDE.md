# MaterialBook — instructions for any agent

1. Read `docs/PROGRESS.md` first (task board, protocol, decision log, next action). Then `docs/ARCHITECTURE.md`.
2. Claim a task in PROGRESS.md before coding; update it when done or partial. Do not work outside the task's listed files.
3. Do not generate code that already exists or is not required by the task's "Done when". Search first.
4. Only permissive-licensed dependencies (no AGPL/GPL). PDF = PDFBox + embedded Noto Sans.
5. Never edit applied Flyway migrations; add `V<n>__*.sql`.
6. Never commit secrets; UI must work at 360px and desktop widths.
