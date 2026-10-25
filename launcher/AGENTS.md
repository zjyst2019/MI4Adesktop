# Independent Android 6 Port

This is a separate, device-specific source project derived from CleanYourTV,
not a change intended for upstream. Preserve upstream attribution and LICENSE.

- Source baseline: upstream commit 56cd3e81c062244ea313899d5af8d75294f2daae.
- Minimum API 23 is intentional here; do not change the upstream checkout.
- No INTERNET permission, telemetry, root, firmware flashing or package removal.
- Build and lint locally; never commit APKs, credentials or device/session dumps.
- Keep stock Home enabled by default; Home takeover and accessibility helpers
  require a separate explicit user request, a baseline backup and verified undo.
  The owner authorized Home/boot compatibility on 2026-10-22. Preserve the stock
  recovery path until the replacement is runtime-verified; never bypass a
  firmware permission refusal.
- Installation and device testing require a separate user request. First test as
  an ordinary app with stock Home enabled; do not reuse upstream install scripts.
- Keep private data under ignored work/, backup/ or shots/ directories.
- Run launcher JVM tests, lintRelease and assembleRelease after changes. Lint
  NewApi and InlinedApi must remain errors, never suppress compatibility failures.
- Report API 23 static checks separately from real-device runtime verification.
