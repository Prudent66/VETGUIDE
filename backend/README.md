# VETGUIDE Secure Backend (Cloudflare Workers)

Roadmap **§8** (Secure Backend & API Key Management) / docs/SECURITY.md:

```
Android APK → this Worker → AI providers
```

This Worker is the **single outbound caller** to AI providers and the only
place provider credentials exist. The APK never contains keys.

## Current state: Gemini routing with mock fallback (Phase 6)

`POST /analyze` validates the request shape and routes to **Gemini**
(`gemini-1.5-flash`) when the `GEMINI_API_KEY` secret is configured, enforcing
the `AiAnalysisData` JSON schema in the prompt and normalizing the model
output defensively. Without the secret the Worker answers with a **mocked**
response that mirrors the same schema — useful for `npx wrangler dev` and CI:

```json
{
  "provider": "gemini",
  "model": "gemini-1.5-flash",
  "findings": ["..."],
  "suspectedConditions": [{ "name": "...", "confidence": 0.42 }],
  "recommendedNextSteps": ["..."]
}
```
(mock mode uses `"provider": "mock"` / `"model": "mock-v1"`; upstream Gemini
failures return explicit error JSON with the upstream status — never a
silent mock — so the app's failover ladder can react per roadmap §5.2.)

Request body:

```json
{ "imageBase64": "<optional base64 string>", "prompt": "structured findings text" }
```

Other endpoints: `GET /health` → `{ "status": "ok" }`.

### Local test

```bash
npx wrangler dev
curl -X POST http://localhost:8787/analyze \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Dog, 4 months — vomiting; hemorrhagic diarrhea"}'
```

## Deploying to Cloudflare Workers (free tier)

1. Install the tooling locally (not committed to the repo):

   ```bash
   npm install -D wrangler
   npx wrangler login
   ```

2. Deploy:

   ```bash
   npx wrangler deploy
   ```

   Wrangler prints the public URL, e.g.
   `https://vetguide-backend.<your-subdomain>.workers.dev`.
   That URL is **public infrastructure, not a secret** (§8) — after deploying,
   update `AiCatalog.BACKEND_BASE_URL` in the Android app
   (`domain/ai/AiCatalog.kt`).

3. Secrets (when real provider routing lands — never in `wrangler.toml`, never
   in git; scanned by gitleaks in CI):

   ```bash
   npx wrangler secret put GEMINI_API_KEY
   ```

   Secrets are stored encrypted server-side by Cloudflare and exposed to the
   Worker only through its `Env` bindings. Rotation procedure is documented in
   `docs/SECURITY.md`.

4. Free-tier limits (§7): Workers free plan allows 100,000 requests/day —
   verify current numbers at the Cloudflare pricing page before relying on
   them. Server-side usage caps are enforced in a later Phase 5 task.

## Next tasks in this folder (Phase 5, remaining)

- Real provider routing: Gemini primary + one independent alternative, with
  structured-output schema validation (§6) and the roadmap §5.2 timeout.
- Server-side **free-tier quota guard** (§7): daily caps per provider, hard
  block on unapproved paid models, "unverifiable eligibility = unavailable".
- Provenance echo (§5.2): provider id, model id, latency, degraded flags so
  the app can persist them with the case record.
