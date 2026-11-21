// Triggering first automated Cloudflare build
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
 * vision model (1.5/2.0 flash candidates) with a strict JSON schema
 * instruction, and normalizes the output to the app's AiAnalysisData shape
 * (domain/ai/AiModels.kt). GEMINI_API_KEY may hold several comma-separated
 * keys; a key that returns 429 rotates out and the next one is tried. When no
 * key is set the Worker answers with the deterministic MOCK analysis instead —
 * useful for local testing (npx wrangler dev) and CI. Upstream failures return
 * explicit error JSON (never a silent mock), so the app's failover ladder can
 * react (§5.2).
 */

export interface Env {
  /**
   * One or more Gemini API keys, comma-separated (e.g. "key1,key2,key3").
   * Set via `wrangler secret put GEMINI_API_KEY` — never in wrangler.toml/repo (§8).
   */
  GEMINI_API_KEY?: string;
  /** Optional model override (non-secret var); defaults to the candidate list. */
  GEMINI_MODEL?: string;
}

/**
 * Gemini model candidates, tried in order per request. The 1.5 series is
 * retired for newer API keys (upstream HTTP 404), so the 2.x flash
 * generation is primary. A key-scoped `GEMINI_MODEL` var (wrangler.toml
 * [vars] — non-secret, §8) overrides the whole list.
 */
const DEFAULT_GEMINI_MODELS = ["gemini-1.5-flash", "gemini-2.0-flash-exp"];
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
  /** Optional vet-provided context, e.g. "Vomiting for 2 days". */
  clinicalContext?: string;
}

const JSON_HEADERS: HeadersInit = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
}

function buildPrompt(findings: string, clinicalContext?: string): string {
  const contextBlock: string[] = [];
  if (clinicalContext && clinicalContext.trim().length > 0) {
    contextBlock.push(
      "User Clinical Context: " + clinicalContext.trim(),
      "- If the user provides clinical context, prioritize visual findings " +
        "that align with that context, but remain objective.",
    );
  }
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
    "Field-veterinarian language rules (strict):",
    "1. Use simple, plain English. Avoid overly academic terms.",
    "2. If you MUST use a medical term, immediately explain it in brackets. " +
      "Example: 'Subcutaneous hematoma (blood-filled swelling under the skin)'.",
    "3. Prioritize practical, locally available treatments and prescriptions. " +
      "Assume the vet has basic field equipment (stethoscope, thermometer, syringes) " +
      "but NO advanced lab or imaging equipment.",
    "4. In the recommendedNextSteps array, provide actionable, field-ready treatments, " +
      "drug names (if applicable), and management advice, rather than advanced " +
      "diagnostic tests.",
    "",
    "Dosage rules (strict):",
    "1. If a treatment requires oral/systemic medication, provide standard veterinary " +
      "dosage ranges (e.g., mg/kg) for common drugs.",
    "2. CRITICAL SAFETY: Always remind the vet to verify the dosage based on the exact " +
      "animal weight, species, and current medical condition.",
    "",
    ...contextBlock,
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
      "Seed output — requires veterinary review; not a diagnosis",
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

  const { prompt, imageBase64, clinicalContext } = (parsed ?? {}) as AnalyzeRequestBody;
  if (typeof prompt !== "string" || prompt.trim().length === 0) {
    return json({ error: "'prompt' (non-empty string) is required" }, 400);
  }
  if (imageBase64 !== undefined && typeof imageBase64 !== "string") {
    return json({ error: "'imageBase64' must be a string when present" }, 400);
  }
  if (clinicalContext !== undefined && typeof clinicalContext !== "string") {
    return json({ error: "'clinicalContext' must be a string when present" }, 400);
  }

  const apiKeysRaw = env.GEMINI_API_KEY;
  if (!apiKeysRaw) {
    // Local/CI mode: no credentials configured — serve the labeled mock.
    return json(mockAnalysis(prompt), 200);
  }

  // Multi-key rotation: GEMINI_API_KEY may be "key1,key2,key3".
  // We try each key; on 429 we rotate to the next key. Only return 429
  // to the app if ALL keys are exhausted.
  const apiKeys = apiKeysRaw
    .split(",")
    .map((k) => k.trim())
    .filter((k) => k.length > 0);

  // TODO(Phase 6 remaining backend task): server-side free-tier eligibility
  // and daily usage caps per §7 — enforcement is never purely client-side.
  const parts: GeminiPart[] = [];
  if (imageBase64) {
    parts.push({ inline_data: { mime_type: "image/jpeg", data: imageBase64 } });
  }
  parts.push({ text: buildPrompt(prompt, clinicalContext) });

  const modelCandidates = env.GEMINI_MODEL ? [env.GEMINI_MODEL] : DEFAULT_GEMINI_MODELS;
  let lastError: { status: number; message: string } | null = null;

  for (const apiKey of apiKeys) {
    let upstream: Response | null = null;
    let activeModel = "";
    let keyLastStatus = 0;

    try {
      for (const model of modelCandidates) {
        const response = await fetch(`${GEMINI_BASE_URL}/${model}:generateContent`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": apiKey,
          },
          body: JSON.stringify({
            contents: [{ role: "user", parts }],
            generationConfig: { responseMimeType: "application/json", temperature: 0.0 },
          }),
        });
        keyLastStatus = response.status;
        if (response.ok) {
          upstream = response;
          activeModel = model;
          break;
        }
        // Model not available for this key (e.g. retired 1.5 series) → try next model.
        if (response.status === 404) {
          continue;
        }
        // Quota exceeded for this key → rotate to next key.
        if (response.status === 429) {
          lastError = { status: 429, message: `gemini upstream HTTP 429 (quota exhausted for key)` };
          break;
        }
        // Any other upstream status (5xx, 401, etc.) → surface immediately.
        return json({ error: `gemini upstream HTTP ${response.status}` }, response.status);
      }
    } catch {
      // Network failure for this key → try next key.
      lastError = { status: 502, message: "gemini upstream unreachable" };
      continue;
    }

    // If this key got a successful response, process it.
    if (upstream) {
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
      const normalized = normalizeGeminiOutput(rawOutput, activeModel);
      if (!normalized) {
        return json({ error: "gemini output failed schema validation" }, 502);
      }
      return json(normalized, 200);
    }

    // This key was exhausted (429 or all models 404). Continue to next key.
    if (!lastError && keyLastStatus === 404) {
      lastError = { status: 404, message: `no gemini model available for this key (all candidates 404)` };
    }
  }

  // All keys exhausted.
  if (lastError?.status === 429) {
    return json({ error: "AI service at maximum capacity — all keys exhausted" }, 429);
  }
  return json({ error: lastError?.message ?? "no gemini model available" }, 502);
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
