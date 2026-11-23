# VETGUIDE Secure Backend (Cloudflare Workers)

Roadmap **§8** (Secure Backend & API Key Management) / docs/SECURITY.md:

```
Android APK → this Worker → AI providers
```

This Worker is the **single outbound caller** to AI providers and the only
place provider credentials exist. The APK never contains keys.

## Resilient multi-provider vision cascade (Phase 6)

`POST /analyze` validates the request shape and routes it through four tiers.
Every tier returns the same normalized `AiAnalysisData` schema, so the Android
app cannot tell which provider answered and a burst of 30+ testers does not
exhaust a single free-tier key.

| Tier | Providers | Behaviour on failure |
|------|-----------|----------------------|
| 1 | **Gemini** — every key × `gemini-2.5-flash` → `gemini-2.0-flash` → `gemini-2.5-flash-lite` | Starting key is **randomized per request** so concurrent users do not all hit key #1. Rotates to the next key/model on HTTP `429`/`503` (and `404` for retired models). |
| 2 | **Groq** — `qwen/qwen3.8-27b` (then `qwen/qwen3.6-27b`, Llama 4 Scout) | OpenAI-compatible vision chat completions; tries the next model on any provider-scoped error. |
| 3 | **OpenRouter** — free vision models (`nvidia/nemotron-3.5-super-vl:free`, `google/gemma-4-31b-it:free`, `qwen/qwen2.5-vl-72b-instruct:free`, `openrouter/free`) then **Mistral** `pixtral-12b-2409` | Same OpenAI-compatible flow; moves to the next model, then the next provider. |
| 4 | **Cloudflare Workers AI** — `@cf/meta/llama-3.2-11b-vision-instruct` via the `AI` binding | Used only when `env.AI` is bound and every HTTP provider failed. |

Additional resilience:

- **Time-boxed attempts** — each upstream call is capped at 20 s and the whole
  cascade shares a 40 s deadline, staying inside the Android client's 45 s
  ceiling. Gemini 2.5 models run with thinking disabled to cut latency.
- **Edge dedupe** — successful responses are cached for **1 hour** in
  `caches.default`, keyed by a SHA-256 of `image + prompt + clinicalContext`.
  Repeated test images return instantly without consuming upstream quota
  (`X-Vetguide-Cache: HIT`). Cache entries are per data center; Cloudflare
  recommends a custom domain/route for production Workers, so treat dedupe as
  a best-effort optimization (the code no-ops safely if the cache is absent).
- **Mock only when unconfigured** — if no key and no `AI` binding exists the
  Worker serves the clearly labeled mock (local/CI). Upstream failures always
  return explicit error JSON — never a silent mock — so the app's failover
  ladder can react (§5.2).

Images are forwarded as `data:image/jpeg;base64,...` content parts for the
OpenAI-compatible providers/Workers AI and as Gemini `inline_data` for tier 1.
Both raw base64 and data URLs are accepted.

### Response schema

Every provider is normalized to the app's `AiAnalysisData`
(`domain/ai/AiModels.kt`); `provider`/`model` are overwritten with the tier
that actually answered (never trusted from model prose):

```json
{
  "provider": "groq",
  "model": "qwen/qwen3.8-27b",
  "findings": ["..."],
  "suspectedConditions": [{ "name": "...", "confidence": 0.42 }],
  "recommendedNextSteps": ["..."]
}
```

Malformed/fenced/prose-wrapped model output is parsed defensively; if nothing
usable is produced the tier fails and the cascade continues. Empty
`suspectedConditions` is returned as-is — the app decides whether that means
"insufficient information" (§10).

### Request body

```json
{
  "imageBase64": "<optional base64 or data URL>",
  "prompt": "structured findings text",
  "clinicalContext": "<optional>"
}
```

Other endpoints: `GET /health` → `{ "status": "ok", "mode": "live|mock",
"providers": { ... booleans/counts only, no key material ... } }`.

## Secrets

Provider credentials are Workers secrets — never in `wrangler.toml`, never in
git (gitleaks scans the full history in CI):

```bash
npx wrangler secret put GEMINI_API_KEY       # "key1,key2,key3" — comma-separated
npx wrangler secret put GROQ_API_KEY
npx wrangler secret put OPENROUTER_API_KEY
npx wrangler secret put MISTRAL_API_KEY
```

Tier 4 needs no secret: `wrangler.toml` declares the Workers AI binding
(`[ai] binding = "AI"`). Enable Workers AI on the account, then accept Meta's
license once:

```bash
curl https://api.cloudflare.com/client/v4/accounts/$CLOUDFLARE_ACCOUNT_ID/ai/run/@cf/meta/llama-3.2-11b-vision-instruct \
  -X POST -H "Authorization: Bearer $CLOUDFLARE_AUTH_TOKEN" -d '{ "prompt": "agree" }'
```

Optional non-secret model overrides (comma-separated) live under `[vars]`:
`GEMINI_MODEL`, `GROQ_MODEL`, `OPENROUTER_MODEL`, `MISTRAL_MODEL`.

Secrets are stored encrypted server-side by Cloudflare and exposed to the
Worker only through its `Env` bindings. Rotation procedure is documented in
`docs/SECURITY.md`.

## Local development

```bash
cd backend
npm install
npm run typecheck   # tsc --noEmit against @cloudflare/workers-types
npm test            # compiles src/ and runs the cascade tests with mocked upstreams
npx wrangler dev
curl -X POST http://localhost:8787/analyze \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Dog, 4 months — vomiting; hemorrhagic diarrhea"}'
```

`npm test` requires no network or provider keys — it asserts key rotation,
model cascades, Groq/Workers AI fallback, edge dedupe, schema normalization,
and capacity-error mapping.

## Deploying to Cloudflare Workers (free tier)

```bash
cd backend
npm install
npx wrangler login
npx wrangler deploy
```

Wrangler prints the public URL. That URL is **public infrastructure, not a
secret** (§8) — after deploying, update `AiCatalog.BACKEND_BASE_URL` in the
Android app (`domain/ai/AiCatalog.kt`).

Free-tier limits (§7): Workers free plan allows 100,000 requests/day — verify
current numbers at the Cloudflare pricing page before relying on them.
Server-side usage caps are enforced in a later Phase 5 task.

## Next tasks in this folder (Phase 5, remaining)

- Server-side **free-tier quota guard** (§7): daily caps per provider, hard
  block on unapproved paid models, "unverifiable eligibility = unavailable".
- Provenance echo (§5.2): provider id, model id, latency, degraded flags so
  the app can persist them with the case record.
