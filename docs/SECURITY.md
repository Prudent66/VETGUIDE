# VETGUIDE — Security Policy (skeleton, Phase 0)

This document implements roadmap §8 (Secure Backend & API Key Management) and §15 (Security & Release Preparation). It grows as phases complete.

## Architecture rule (locked)

```
Android Application (APK) → Secure VETGUIDE Backend (Cloudflare Workers) → AI Orchestrator → AI Providers
```

All AI provider calls route through the secure VETGUIDE backend (Cloudflare Workers — decision locked for Phase 4). The backend manages **all** private provider credentials. **The APK never contains private keys** — no keys in the APK, `local.properties`, client-side Settings, or encrypted client storage.

## Secret handling rules

1. **No provider credentials in the repository** — enforced by `gitleaks` in CI (`.github/workflows/pr.yml`) and a `.gitignore` audit (Phase 0 acceptance).
2. CI secrets (GitHub Secrets) must be **mock/test-only credentials** used exclusively against fake providers — never against billable endpoints (roadmap §7).
3. Backend secrets are stored as Cloudflare Workers secrets (encrypted at rest by Cloudflare), never in `wrangler.toml`, never in the repo.
4. **No paid accounts, credit cards, or billing-enabled projects** attach to any VETGUIDE API key without the owner's explicit written approval (roadmap §7).
5. Client-side free-tier/usage display may exist for transparency, but enforcement is server-side only (roadmap §7).

## Key rotation procedure

> To be completed at Phase 4 when the backend is stood up. Placeholder plan:
> 1. Invalidate the old provider key server-side (Cloudflare dashboard → Workers secrets update).
> 2. Issue/store the new key as a Workers secret — never on the client.
> 3. Verify the backend health endpoint (roadmap Phase 4 developer surface) shows the provider as available.
> 4. Audit `gitleaks` scan history to confirm old keys never entered the repo or device builds.

## Verification checklist (per release)

- [ ] `gitleaks` CI step green.
- [ ] Code review confirms no literal credentials anywhere in the repo.
- [ ] Release artifact scan confirms the APK contains no provider credentials (Phase 4+ acceptance).
- [ ] TLS only for backend↔device and backend↔provider traffic; responses validated (timeout, size caps, schema validation).
