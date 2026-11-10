/**
 * VETGUIDE secure backend skeleton (roadmap §8, Phase 5).
 *
 * Architecture (locked, docs/SECURITY.md):
 *   Android APK → this Cloudflare Worker → AI providers
 *
 * This Worker is the single outbound caller to AI providers and holds ALL
 * provider credentials as Workers secrets (encrypted at rest by Cloudflare).
 * Provider keys are never in this repo, never in wrangler.toml, and never in
 * the APK (§7/§8).
 *
 * Current state: MOCK mode. POST /analyze accepts
 *   { "imageBase64"?: string, "prompt": string }
 * and returns a mocked response that mirrors the app's AiAnalysisData schema
 * (app/src/main/java/com/prudent66/vetguide/domain/ai/AiModels.kt). The real
 * provider routing (Gemini primary + failover, structured-output schema
 * enforcement, server-side free-tier quota guard per §7) lands in the
 * remaining Phase 5 backend tasks.
 */

export interface Env {
  // Provider credentials arrive as Workers secrets via `wrangler secret put`
  // and are read from here at real-routing time. Never hardcoded (§8).
  // Example (to be added with real routing):
  //   GEMINI_API_KEY: string;
}

interface SuspectedCondition {
  name: string;
  confidence: number;
}

/** Mirrors AiAnalysisData in the Android app (domain/ai/AiModels.kt). */
interface AiAnalysisData {
  provider: string;
  model: string;
  findings: string[];
  suspectedConditions: SuspectedCondition[];
  recommendedNextSteps: string[];
}

interface AnalyzeRequestBody {
  imageBase64?: string;
  prompt: string;
}

const JSON_HEADERS: HeadersInit = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
}

/**
 * Mocked analysis (Phase 5 skeleton). Deterministic placeholder content —
 * clearly labeled mock, never real clinical guidance (§9/§10 rule: no
 * AI-generated clinical content without verified references).
 */
function mockAnalysis(prompt: string): AiAnalysisData {
  return {
    provider: "mock",
    model: "mock-v1",
    findings: [`Received prompt: ${prompt}`],
    suspectedConditions: [
      { name: "Canine Parvovirus", confidence: 0.42 },
      { name: "Foreign-body gastritis", confidence: 0.28 },
    ],
    recommendedNextSteps: [
      "Mock backend response — real provider routing arrives in Phase 5",
      "Placeholder output — requires veterinary review; not a diagnosis",
    ],
  };
}

async function handleAnalyze(request: Request): Promise<Response> {
  let parsed: unknown;
  try {
    parsed = await request.json();
  } catch {
    return json({ error: "request body must be valid JSON" }, 400);
  }

  const { prompt, imageBase64 } = (parsed ?? {}) as AnalyzeRequestBody;
  if (typeof prompt !== "string" || prompt.trim().length === 0) {
    return json({ error: "'prompt' (non-empty string) is required" }, 400);
  }
  if (imageBase64 !== undefined && typeof imageBase64 !== "string") {
    return json({ error: "'imageBase64' must be a string when present" }, 400);
  }

  // TODO(Phase 5, remaining backend tasks): validate the structured-output
  // schema, enforce server-side free-tier eligibility and daily caps (§7),
  // then forward to the primary provider with its secret from Env.
  return json(mockAnalysis(prompt), 200);
}

export default {
  async fetch(request: Request, _env: Env, _ctx: ExecutionContext): Promise<Response> {
    const path = new URL(request.url).pathname;

    if (path === "/health") {
      return json({ status: "ok", backend: "vetguide", mode: "mock" });
    }

    if (path === "/analyze") {
      if (request.method !== "POST") {
        return json({ error: "method not allowed" }, 405);
      }
      return handleAnalyze(request);
    }

    return json({ error: "not found" }, 404);
  },
} satisfies ExportedHandler<Env>;
