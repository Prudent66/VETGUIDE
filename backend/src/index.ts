/**
 * VETGUIDE secure backend (roadmap §8, Phase 6).
 *
 * Architecture (locked, docs/SECURITY.md):
 *   Android APK → this Cloudflare Worker → AI providers
 *
 * This Worker is the single outbound caller to AI providers and holds ALL
 * provider credentials as Workers secrets (encrypted at rest by Cloudflare).
 * Provider keys are never in this repo, never in wrangler.toml, and never in
 * the APK (§7/§8).
 *
 * Routing: POST /analyze forwards { imageBase64?, prompt } to the Gemini
 * vision model (gemini-1.5-flash) with a strict JSON schema instruction, and
 * normalizes the output to the app's AiAnalysisData shape
 * (domain/ai/AiModels.kt). When GEMINI_API_KEY is not set the Worker answers
 * with the deterministic MOCK analysis instead — useful for local testing
 * (npx wrangler dev) and CI. Upstream failures return explicit error JSON
 * (never a silent mock), so the app's failover ladder can react (§5.2).
 */

export interface Env {
  /** Set via `wrangler secret put GEMINI_API_KEY` — never in wrangler.toml/repo (§8). */
  GEMINI_API_KEY?: string;
}

const GEMINI_MODEL = "gemini-1.5-flash";
const GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

interface GeminiInlinePart {
  inline_data: { mime_type: string; data: string };
}

interface GeminiTextPart {
  text: string;
}

type GeminiPart = GeminiInlinePart | GeminiTextPart;

interface GeminiResponse {
  candidates?: Array<{
    content?: {
      parts?: Array<{ text?: string }>;
    };
  }>;
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

function buildPrompt(findings: string): string {
  return [
    "You are decision support for a licensed veterinarian — never a diagnosis.",
    "Analyze the attached animal photo and the structured findings below.",
    "Respond ONLY with JSON exactly matching this schema:",
    '{"provider":string,"model":string,"findings":string[],' +
      '"suspectedConditions":[{"name":string,"confidence":number}],' +
      '"recommendedNextSteps":string[]}',
    "- provider/model: identify yourself.",
    "- confidence: a 0..1 estimate; it is NOT a validated diagnostic probability.",
    "- suspectedConditions: ranked, max 5, keep names general (e.g. species-level).",
    "- If the image/findings are insufficient, return an EMPTY suspectedConditions list.",
    "",
    "Structured findings: " + findings,
  ].join("\n");
}

/**
 * Defensive normalization of the model output (§10): schema violations must
 * fail loudly, never become guesses. Returns null when the shape is unusable.
 */
function normalizeGeminiOutput(raw: unknown, model: string): AiAnalysisData | null {
  const candidate = raw as Partial<AiAnalysisData> | null;
  if (!candidate || !Array.isArray(candidate.findings) || !Array.isArray(candidate.suspectedConditions)) {
    return null;
  }
  const conditions = candidate.suspectedConditions
    .filter((c) => !!c && typeof c.name === "string" && typeof c.confidence === "number")
    .map((c) => ({ name: c.name, confidence: c.confidence as number }));
  return {
    provider: "gemini",
    model,
    findings: candidate.findings.filter((f) => typeof f === "string"),
    suspectedConditions: conditions,
    recommendedNextSteps: Array.isArray(candidate.recommendedNextSteps)
      ? candidate.recommendedNextSteps.filter((s) => typeof s === "string")
      : [],
  };
}

/**
 * Mocked analysis — used only when GEMINI_API_KEY is not configured.
 * Clearly labeled mock, never real clinical guidance (§9/§10: no AI-generated
 * clinical content without verified references).
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
      "Mock backend response — no GEMINI_API_KEY configured on this Worker",
      "Placeholder output — requires veterinary review; not a diagnosis",
    ],
  };
}

async function handleAnalyze(request: Request, env: Env): Promise<Response> {
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

  const apiKey = env.GEMINI_API_KEY;
  if (!apiKey) {
    // Local/CI mode: no credentials configured — serve the labeled mock.
    return json(mockAnalysis(prompt), 200);
  }

  // TODO(Phase 6 remaining backend task): server-side free-tier eligibility
  // and daily usage caps per §7 — enforcement is never purely client-side.
  const parts: GeminiPart[] = [];
  if (imageBase64) {
    parts.push({ inline_data: { mime_type: "image/jpeg", data: imageBase64 } });
  }
  parts.push({ text: buildPrompt(prompt) });

  let upstream: Response;
  try {
    upstream = await fetch(`${GEMINI_BASE_URL}/${GEMINI_MODEL}:generateContent`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
      },
      body: JSON.stringify({
        contents: [{ role: "user", parts }],
        generationConfig: { responseMimeType: "application/json", temperature: 0.2 },
      }),
    });
  } catch {
    return json({ error: "gemini upstream unreachable" }, 502);
  }

  if (!upstream.ok) {
    // Surface the upstream failure (429/5xx are mapped app-side so the
    // orchestrator ladder can fail over per §5.2). Never fall back silently.
    return json({ error: `gemini upstream HTTP ${upstream.status}` }, upstream.status);
  }

  const data = (await upstream.json()) as GeminiResponse;
  const text = (data.candidates?.[0]?.content?.parts ?? [])
    .map((p) => p.text ?? "")
    .join("");
  let rawOutput: unknown;
  try {
    rawOutput = JSON.parse(text);
  } catch {
    return json({ error: "gemini returned non-JSON output" }, 502);
  }
  const normalized = normalizeGeminiOutput(rawOutput, GEMINI_MODEL);
  if (!normalized) {
    return json({ error: "gemini output failed schema validation" }, 502);
  }
  return json(normalized, 200);
}

export default {
  async fetch(request: Request, env: Env, _ctx: ExecutionContext): Promise<Response> {
    const path = new URL(request.url).pathname;

    if (path === "/health") {
      return json({
        status: "ok",
        backend: "vetguide",
        mode: env.GEMINI_API_KEY ? "gemini" : "mock",
      });
    }

    if (path === "/analyze") {
      if (request.method !== "POST") {
        return json({ error: "method not allowed" }, 405);
      }
      return handleAnalyze(request, env);
    }

    return json({ error: "not found" }, 404);
  },
} satisfies ExportedHandler<Env>;
