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
 * Routing: POST /analyze forwards { imageBase64?, prompt, clinicalContext? }
 * through a resilient vision cascade so a burst of testers never exhausts one
 * provider:
 *
 *   Tier 1  Gemini, every key × every model (2.5-flash → 2.0-flash →
 *           2.5-flash-lite), with a per-request randomized starting key so
 *           concurrent users do not all hammer key #1.
 *   Tier 2  Groq (OpenAI-compatible vision).
 *   Tier 3  OpenRouter (free vision models) then Mistral (pixtral-12b-2409).
 *   Tier 4  Cloudflare Workers AI binding (@cf/meta/llama-3.2-11b-vision-instruct)
 *           when `env.AI` is bound.
 *
 * Every provider response is normalized to the app's AiAnalysisData shape
 * (domain/ai/AiModels.kt), so Android cannot tell which tier answered.
 * Successful responses are deduplicated for one hour in `caches.default`
 * (keyed by a SHA-256 of image + prompt + clinical context) so repeated test
 * images do not consume upstream quota.
 *
 * Each upstream attempt is time-boxed and the whole chain shares a 40 s
 * deadline, keeping the Worker inside the Android client's 45 s ceiling.
 * When no provider is configured at all the Worker answers with the
 * deterministic MOCK analysis instead — useful for `npx wrangler dev` and CI.
 * Upstream failures return explicit error JSON (never a silent mock), so the
 * app's failover ladder can react (§5.2).
 */

export interface Env {
  /**
   * One or more Gemini API keys, comma-separated (e.g. "key1,key2,key3").
   * Set via `wrangler secret put GEMINI_API_KEY` — never in wrangler.toml/repo (§8).
   */
  GEMINI_API_KEY?: string;
  /** Optional Gemini model override (non-secret var); defaults to the cascade. */
  GEMINI_MODEL?: string;
  /** Groq API key (tier 2). `wrangler secret put GROQ_API_KEY`. */
  GROQ_API_KEY?: string;
  /** Optional comma-separated Groq vision models; defaults to the cascade. */
  GROQ_MODEL?: string;
  /** OpenRouter API key (tier 3). `wrangler secret put OPENROUTER_API_KEY`. */
  OPENROUTER_API_KEY?: string;
  /** Optional comma-separated OpenRouter vision models; defaults to free models. */
  OPENROUTER_MODEL?: string;
  /** Mistral API key (tier 3). `wrangler secret put MISTRAL_API_KEY`. */
  MISTRAL_API_KEY?: string;
  /** Optional comma-separated Mistral vision models; defaults to pixtral. */
  MISTRAL_MODEL?: string;
  /** Cloudflare Workers AI binding (tier 4), configured in wrangler.toml [ai]. */
  AI?: Ai;
  /** Non-secret environment label (wrangler.toml [vars]). */
  ENVIRONMENT?: string;
}

/**
 * Gemini model candidates, tried in order per key. The 2.5 generation is the
 * current free-tier workhorse; 2.0-flash and 2.5-flash-lite widen capacity
 * when the primary model is rate limited. A `GEMINI_MODEL` var overrides the
 * whole list.
 */
const DEFAULT_GEMINI_MODELS = [
  "gemini-2.5-flash",
  "gemini-2.0-flash",
  "gemini-2.5-flash-lite",
];
const GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

/**
 * Groq vision models. qwen/qwen3.8-27b is the current multimodal model;
 * the older Llama 4 Scout id is kept last for accounts that still expose it.
 */
const DEFAULT_GROQ_MODELS = [
  "qwen/qwen3.8-27b",
  "qwen/qwen3.6-27b",
  "meta-llama/llama-4-scout-17b-16e-instruct",
];
const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

/** Free OpenRouter vision models; openrouter/free auto-routes if all are busy. */
const DEFAULT_OPENROUTER_MODELS = [
  "nvidia/nemotron-3.5-super-vl:free",
  "google/gemma-4-31b-it:free",
  "qwen/qwen2.5-vl-72b-instruct:free",
  "openrouter/free",
];
const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

const DEFAULT_MISTRAL_MODELS = ["pixtral-12b-2409"];
const MISTRAL_URL = "https://api.mistral.ai/v1/chat/completions";

const WORKERS_AI_VISION_MODEL = "@cf/meta/llama-3.2-11b-vision-instruct";

/** Edge dedupe TTL for identical image+prompt requests. */
const CACHE_TTL_SECONDS = 3600;
/** Whole cascade must finish before the Android client's 45 s ceiling. */
const TOTAL_BUDGET_MS = 40_000;
/** Hard cap for any single upstream attempt. */
const ATTEMPT_TIMEOUT_MS = 20_000;
const MAX_OUTPUT_TOKENS = 2048;

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

interface OpenAiContentPart {
  type?: string;
  text?: string;
  image_url?: { url?: string } | string;
}

interface OpenAiResponse {
  choices?: Array<{
    message?: {
      content?: string | Array<string | OpenAiContentPart>;
    };
  }>;
}

interface WorkersAiResponse {
  response?: string;
  result?: { response?: string };
  choices?: OpenAiResponse["choices"];
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
  prescriptions: string[];
}

interface AnalyzeRequestBody {
  imageBase64?: string;
  prompt: string;
  /** Optional vet-provided context, e.g. "Vomiting for 2 days". */
  clinicalContext?: string;
  /** Selected pathway: "Domestic" or "Wild". */
  animalCategory?: string;
  /** Exact selected animal, e.g. "African Wild Dog · Jackal / Fox / Wild Dog". */
  animalLabel?: string;
}

/** One failed upstream attempt, retained for diagnostics and error selection. */
interface AttemptFailure {
  provider: string;
  /** HTTP status, or 0 for a network/timeout failure. */
  status: number;
  message: string;
}

interface AttemptContext {
  deadline: number;
  failures: AttemptFailure[];
}

const JSON_HEADERS: HeadersInit = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
};

function json(data: unknown, status = 200, extraHeaders: HeadersInit = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...JSON_HEADERS, ...extraHeaders },
  });
}

/** Trims and drops empties from a comma-separated secret/var list. */
function parseList(value?: string): string[] {
  if (!value) return [];
  return value
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

function unique(items: string[]): string[] {
  return Array.from(new Set(items));
}

/** `ENV_OVERRIDE` wins; otherwise the built-in cascade. */
function modelList(override: string | undefined, defaults: string[]): string[] {
  const parsed = parseList(override);
  return parsed.length > 0 ? parsed : defaults;
}

/**
 * Rotates a list by a random offset for each request. Concurrent testers then
 * start on different keys instead of all racing key #1.
 */
function rotate<T>(items: T[], start: number): T[] {
  if (items.length === 0) return [];
  const offset = ((start % items.length) + items.length) % items.length;
  return [...items.slice(offset), ...items.slice(0, offset)];
}

/** Accepts either raw base64 or a data URL and returns raw base64. */
function rawBase64(imageBase64: string | undefined): string | undefined {
  if (!imageBase64) return undefined;
  const trimmed = imageBase64.trim();
  if (!trimmed.startsWith("data:")) return trimmed;
  const comma = trimmed.indexOf(",");
  return comma >= 0 ? trimmed.slice(comma + 1) : undefined;
}

function imageDataUrl(base64: string): string {
  return `data:image/jpeg;base64,${base64}`;
}

function describeError(error: unknown): string {
  if (error instanceof Error) {
    if (error.name === "TimeoutError" || error.name === "AbortError") return "timeout";
    return error.message;
  }
  return String(error);
}

function buildPrompt(
  findings: string,
  clinicalContext?: string,
  animalCategory?: string,
  animalLabel?: string,
): string {
  const contextBlock: string[] = [];
  if (clinicalContext && clinicalContext.trim().length > 0) {
    contextBlock.push(
      "User Clinical Context: " + clinicalContext.trim(),
      "- If the user provides clinical context, prioritize visual findings " +
        "that align with that context, but remain objective.",
    );
  }
  const selected = animalLabel?.trim();
  if (selected) {
    contextBlock.push(
      "Selected animal (strict): " + selected +
        (animalCategory ? " — " + animalCategory + " pathway" : "") + ".",
      "- Identify and describe ONLY " + selected + ".",
      "- Restrict every finding, differential diagnosis and clinical description " +
        "to " + selected + ". Never diagnose or describe conditions of other species.",
      "- If the photo cannot be confirmed as " + selected + ", say so explicitly " +
        "and return an empty suspectedConditions list.",
    );
  }
  return [
    "You are decision support for a licensed veterinarian — never a diagnosis.",
    "Analyze the attached animal photo and the structured findings below.",
    "Respond ONLY with JSON exactly matching this schema:",
    '{"provider":string,"model":string,"findings":string[],' +
      '"suspectedConditions":[{"name":string,"confidence":number}],' +
      '"recommendedNextSteps":string[],"prescriptions":string[]}',
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
    "4. In the recommendedNextSteps array, provide ONLY non-pharmacological " +
      "supportive care and follow-up (isolation, hygiene, feeding, re-check). " +
      "Never repeat drug dosages there. Put every drug/injection prescription in " +
      "the prescriptions array instead.",
    "5. Each prescriptions item must include drug name, dose (prefer mg/kg and " +
      "total dose), route, frequency and duration. Use an empty array when no " +
      "medication is indicated. Never invent a prescription.",
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
    "",
    "Return raw JSON only — do not wrap it in markdown code fences.",
  ].join("\n");
}

function asStringArray(value: unknown, limit = 20): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is string => typeof item === "string")
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
    .slice(0, limit);
}

function asConfidence(value: unknown): number {
  const numeric = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numeric)) return 0;
  return Math.min(1, Math.max(0, numeric));
}

function normalizeConditions(value: unknown): SuspectedCondition[] {
  if (!Array.isArray(value)) return [];
  return value
    .flatMap((entry): SuspectedCondition[] => {
      if (!entry || typeof entry !== "object") return [];
      const candidate = entry as Record<string, unknown>;
      if (typeof candidate.name !== "string" || candidate.name.trim().length === 0) {
        return [];
      }
      return [{ name: candidate.name.trim(), confidence: asConfidence(candidate.confidence) }];
    })
    .slice(0, 5);
}

/**
 * Defensive normalization shared by every provider (§10): schema violations
 * must fail loudly, never become guesses. Returns null when nothing usable
 * was produced. Provider/model are always overwritten with the tier that
 * actually answered, regardless of what the model claims.
 */
function normalizeAnalysis(raw: unknown, provider: string, model: string): AiAnalysisData | null {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const candidate = raw as Record<string, unknown>;
  const findings = asStringArray(candidate.findings);
  const suspectedConditions = normalizeConditions(candidate.suspectedConditions);
  const recommendedNextSteps = asStringArray(candidate.recommendedNextSteps);
  const prescriptions = asStringArray(candidate.prescriptions);
  if (
    findings.length === 0 &&
    suspectedConditions.length === 0 &&
    recommendedNextSteps.length === 0 &&
    prescriptions.length === 0
  ) {
    return null;
  }
  return {
    provider,
    model,
    findings,
    suspectedConditions,
    recommendedNextSteps,
    prescriptions,
  };
}

/** Extracts the first balanced JSON object from model prose/code fences. */
function extractBalancedJson(text: string): string | null {
  const start = text.indexOf("{");
  if (start < 0) return null;
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let index = start; index < text.length; index += 1) {
    const char = text[index];
    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (char === "\\") {
        escaped = true;
      } else if (char === '"') {
        inString = false;
      }
      continue;
    }
    if (char === '"') {
      inString = true;
    } else if (char === "{") {
      depth += 1;
    } else if (char === "}") {
      depth -= 1;
      if (depth === 0) return text.slice(start, index + 1);
    }
  }
  return null;
}

function parseModelJson(text: string): unknown | null {
  if (!text || text.trim().length === 0) return null;
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const candidate = (fenced ? fenced[1] : text).trim();
  const payload = extractBalancedJson(candidate) ?? candidate;
  try {
    return JSON.parse(payload);
  } catch {
    return null;
  }
}

function openAiContentText(data: OpenAiResponse | null): string {
  const content = data?.choices?.[0]?.message?.content;
  if (typeof content === "string") return content;
  if (Array.isArray(content)) {
    return content
      .map((part) => (typeof part === "string" ? part : part?.text ?? ""))
      .join("");
  }
  return "";
}

function workersAiText(raw: unknown): string {
  if (typeof raw === "string") return raw;
  if (!raw || typeof raw !== "object") return "";
  const candidate = raw as WorkersAiResponse;
  if (typeof candidate.response === "string") return candidate.response;
  if (typeof candidate.result?.response === "string") return candidate.result.response;
  return openAiContentText(candidate as OpenAiResponse);
}

/**
 * Mocked analysis — used only when NO provider credential/binding is
 * configured. Clearly labeled mock, never real clinical guidance (§9/§10).
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
      "Mock backend response — no provider keys configured on this Worker",
      "Seed output — requires veterinary review; not a diagnosis",
    ],
    prescriptions: [],
  };
}

function hasAnyProvider(env: Env): boolean {
  return (
    parseList(env.GEMINI_API_KEY).length > 0 ||
    !!env.GROQ_API_KEY?.trim() ||
    !!env.OPENROUTER_API_KEY?.trim() ||
    !!env.MISTRAL_API_KEY?.trim() ||
    !!env.AI
  );
}

/* -------------------------------------------------------------------------- */
/* Edge dedupe (caches.default)                                               */
/* -------------------------------------------------------------------------- */

interface EdgeCache {
  match(request: Request): Promise<Response | undefined>;
  put(request: Request, response: Response): Promise<void>;
}

function edgeCache(): EdgeCache | undefined {
  const globalCaches = (globalThis as { caches?: { default?: EdgeCache } }).caches;
  return globalCaches?.default;
}

async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

/** Cache key = SHA-256(image bytes + prompt + clinical context). */
async function requestHash(body: AnalyzeRequestBody, image: string | undefined): Promise<string> {
  return sha256Hex(
    [
      image ?? "", "\u0000", body.prompt, "\u0000", body.clinicalContext ?? "",
      "\u0000", body.animalCategory ?? "", "\u0000", body.animalLabel ?? "",
    ].join(""),
  );
}

function cacheKeyRequest(request: Request, hash: string): Request {
  const url = new URL(request.url);
  url.pathname = "/__vetguide_cache/analyze";
  url.search = `?h=${hash}`;
  return new Request(url.toString(), { method: "GET" });
}

async function readCache(request: Request, hash: string): Promise<Response | null> {
  const cache = edgeCache();
  if (!cache) return null;
  try {
    const hit = await cache.match(cacheKeyRequest(request, hash));
    if (!hit) return null;
    const body = await hit.text();
    return new Response(body, {
      status: 200,
      headers: {
        ...JSON_HEADERS,
        "Cache-Control": `public, max-age=${CACHE_TTL_SECONDS}`,
        "X-Vetguide-Cache": "HIT",
      },
    });
  } catch {
    return null;
  }
}

async function writeCache(
  request: Request,
  hash: string,
  data: AiAnalysisData,
): Promise<void> {
  const cache = edgeCache();
  if (!cache) return;
  try {
    await cache.put(
      cacheKeyRequest(request, hash),
      new Response(JSON.stringify(data), {
        status: 200,
        headers: {
          ...JSON_HEADERS,
          "Cache-Control": `public, max-age=${CACHE_TTL_SECONDS}`,
          "X-Vetguide-Cache": "MISS",
        },
      }),
    );
  } catch {
    // Cache writes are best-effort — never fail an otherwise good response.
  }
}

/* -------------------------------------------------------------------------- */
/* Provider cascade                                                           */
/* -------------------------------------------------------------------------- */

function attemptSignal(context: AttemptContext): AbortSignal {
  const remaining = context.deadline - Date.now();
  const budget = Math.max(1_000, Math.min(ATTEMPT_TIMEOUT_MS, remaining));
  return AbortSignal.timeout(budget);
}

function outOfTime(context: AttemptContext): boolean {
  return Date.now() >= context.deadline;
}

/**
 * Tier 1 — Gemini multi-key × multi-model cascade. The starting key is
 * randomized per request; for each key the models are tried in order on
 * 429/503 (and 404 for retired models). 2.5 models run with thinking disabled
 * to stay inside the client timeout.
 */
async function tryGemini(
  env: Env,
  parts: GeminiPart[],
  context: AttemptContext,
): Promise<AiAnalysisData | null> {
  const keys = unique(parseList(env.GEMINI_API_KEY));
  if (keys.length === 0) return null;
  const models = modelList(env.GEMINI_MODEL, DEFAULT_GEMINI_MODELS);
  const orderedKeys = rotate(keys, Math.floor(Math.random() * keys.length));

  for (const apiKey of orderedKeys) {
    let hardRequestError = false;
    for (const model of models) {
      if (outOfTime(context)) return null;

      const generationConfig: Record<string, unknown> = {
        responseMimeType: "application/json",
        temperature: 0,
      };
      if (model.startsWith("gemini-2.5")) {
        // Extraction task: skip hidden reasoning for latency.
        generationConfig.thinkingConfig = { thinkingBudget: 0 };
      }

      let response: Response;
      try {
        response = await fetch(`${GEMINI_BASE_URL}/${model}:generateContent`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": apiKey,
          },
          body: JSON.stringify({
            contents: [{ role: "user", parts }],
            generationConfig,
          }),
          signal: attemptSignal(context),
        });
      } catch (error) {
        context.failures.push({
          provider: "gemini",
          status: 0,
          message: `gemini network failure (${model}): ${describeError(error)}`,
        });
        continue;
      }

      if (response.ok) {
        const data = (await response.json().catch(() => null)) as GeminiResponse | null;
        const text = (data?.candidates?.[0]?.content?.parts ?? [])
          .map((part) => part.text ?? "")
          .join("");
        const normalized = normalizeAnalysis(parseModelJson(text), "gemini", model);
        if (normalized) return normalized;
        context.failures.push({
          provider: "gemini",
          status: 502,
          message: `gemini output failed schema validation (${model})`,
        });
        continue;
      }

      const status = response.status;
      context.failures.push({
        provider: "gemini",
        status,
        message: `gemini upstream HTTP ${status} (${model})`,
      });
      if (status === 404) continue; // model retired/unavailable → next model
      if (status === 429 || status === 503 || status >= 500) continue;
      if (status === 401 || status === 403) break; // key-scoped → next key
      // 400/413/422: the request itself is unacceptable; stop burning quota.
      hardRequestError = true;
      break;
    }
    if (hardRequestError) return null;
  }
  return null;
}

interface OpenAiProvider {
  provider: string;
  url: string;
  apiKey: string;
  models: string[];
  extraHeaders?: Record<string, string>;
}

/**
 * Tiers 2 & 3 — any OpenAI-compatible vision endpoint (Groq, OpenRouter,
 * Mistral). Images travel as `data:image/jpeg;base64,...` content parts.
 */
async function tryOpenAiCompatible(
  config: OpenAiProvider,
  promptText: string,
  image: string | undefined,
  context: AttemptContext,
): Promise<AiAnalysisData | null> {
  if (!config.apiKey.trim()) return null;

  const content: Array<Record<string, unknown>> = [{ type: "text", text: promptText }];
  if (image) {
    content.push({ type: "image_url", image_url: { url: imageDataUrl(image) } });
  }

  for (const model of config.models) {
    if (outOfTime(context)) return null;
    let response: Response;
    try {
      response = await fetch(config.url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${config.apiKey}`,
          ...config.extraHeaders,
        },
        body: JSON.stringify({
          model,
          messages: [{ role: "user", content }],
          temperature: 0,
          max_tokens: MAX_OUTPUT_TOKENS,
        }),
        signal: attemptSignal(context),
      });
    } catch (error) {
      context.failures.push({
        provider: config.provider,
        status: 0,
        message: `${config.provider} network failure (${model}): ${describeError(error)}`,
      });
      continue;
    }

    if (response.ok) {
      const data = (await response.json().catch(() => null)) as OpenAiResponse | null;
      const normalized = normalizeAnalysis(
        parseModelJson(openAiContentText(data)),
        config.provider,
        model,
      );
      if (normalized) return normalized;
      context.failures.push({
        provider: config.provider,
        status: 502,
        message: `${config.provider} output failed schema validation (${model})`,
      });
      continue;
    }

    // Every non-OK status here is model/key scoped — try the next model.
    context.failures.push({
      provider: config.provider,
      status: response.status,
      message: `${config.provider} upstream HTTP ${response.status} (${model})`,
    });
  }
  return null;
}

/**
 * Tier 4 — Cloudflare Workers AI binding. Uses the OpenAI-style messages
 * payload supported by llama-3.2-11b-vision-instruct.
 */
async function tryWorkersAi(
  env: Env,
  promptText: string,
  image: string | undefined,
  context: AttemptContext,
): Promise<AiAnalysisData | null> {
  if (!env.AI || outOfTime(context)) return null;

  const content: Array<Record<string, unknown>> = [{ type: "text", text: promptText }];
  if (image) {
    content.push({ type: "image_url", image_url: { url: imageDataUrl(image) } });
  }

  try {
    const raw = await env.AI.run(WORKERS_AI_VISION_MODEL, {
      messages: [{ role: "user", content }],
      max_tokens: MAX_OUTPUT_TOKENS,
      temperature: 0,
    });
    const normalized = normalizeAnalysis(
      parseModelJson(workersAiText(raw)),
      "workers-ai",
      WORKERS_AI_VISION_MODEL,
    );
    if (normalized) return normalized;
    context.failures.push({
      provider: "workers-ai",
      status: 502,
      message: "workers-ai output failed schema validation",
    });
  } catch (error) {
    context.failures.push({
      provider: "workers-ai",
      status: 0,
      message: `workers-ai failure: ${describeError(error)}`,
    });
  }
  return null;
}

function isQuotaFailure(failure: AttemptFailure): boolean {
  return failure.status === 429 || failure.status === 401 || failure.status === 403;
}

function failureResponse(context: AttemptContext): Response {
  const failures = context.failures;
  if (failures.length === 0) {
    return json({ error: "Cloud analysis unavailable — no provider answered" }, 502);
  }
  const providers = unique(failures.map((failure) => failure.provider)).join(", ");
  const detail = failures
    .slice(0, 4)
    .map((failure) => failure.message)
    .join("; ");
  if (failures.every(isQuotaFailure)) {
    return json(
      {
        error: "Cloud analysis at maximum capacity — all provider keys exhausted",
        providers,
        detail,
      },
      429,
    );
  }
  return json(
    {
      error: `Cloud analysis unavailable — every provider failed (${providers})`,
      providers,
      detail,
    },
    502,
  );
}

async function handleAnalyze(request: Request, env: Env): Promise<Response> {
  let parsed: unknown;
  try {
    parsed = await request.json();
  } catch {
    return json({ error: "request body must be valid JSON" }, 400);
  }

  const { prompt, imageBase64, clinicalContext, animalCategory, animalLabel } =
    (parsed ?? {}) as AnalyzeRequestBody;
  if (typeof prompt !== "string" || prompt.trim().length === 0) {
    return json({ error: "'prompt' (non-empty string) is required" }, 400);
  }
  if (imageBase64 !== undefined && typeof imageBase64 !== "string") {
    return json({ error: "'imageBase64' must be a string when present" }, 400);
  }
  if (clinicalContext !== undefined && typeof clinicalContext !== "string") {
    return json({ error: "'clinicalContext' must be a string when present" }, 400);
  }
  if (animalCategory !== undefined && typeof animalCategory !== "string") {
    return json({ error: "'animalCategory' must be a string when present" }, 400);
  }
  if (animalLabel !== undefined && typeof animalLabel !== "string") {
    return json({ error: "'animalLabel' must be a string when present" }, 400);
  }

  if (!hasAnyProvider(env)) {
    // Local/CI mode: no credentials configured — serve the labeled mock.
    return json(mockAnalysis(prompt), 200);
  }

  const body: AnalyzeRequestBody = {
    prompt, imageBase64, clinicalContext, animalCategory, animalLabel,
  };
  const image = rawBase64(imageBase64);
  const hash = await requestHash(body, image);
  const cached = await readCache(request, hash);
  if (cached) return cached;

  // TODO(Phase 6 remaining backend task): server-side free-tier eligibility
  // and daily usage caps per §7 — enforcement is never purely client-side.
  const promptText = buildPrompt(prompt, clinicalContext, animalCategory, animalLabel);
  const parts: GeminiPart[] = [];
  if (image) {
    parts.push({ inline_data: { mime_type: "image/jpeg", data: image } });
  }
  parts.push({ text: promptText });

  const context: AttemptContext = {
    deadline: Date.now() + TOTAL_BUDGET_MS,
    failures: [],
  };

  const data =
    (await tryGemini(env, parts, context)) ??
    (await tryOpenAiCompatible(
      {
        provider: "groq",
        url: GROQ_URL,
        apiKey: env.GROQ_API_KEY ?? "",
        models: modelList(env.GROQ_MODEL, DEFAULT_GROQ_MODELS),
      },
      promptText,
      image,
      context,
    )) ??
    (await tryOpenAiCompatible(
      {
        provider: "openrouter",
        url: OPENROUTER_URL,
        apiKey: env.OPENROUTER_API_KEY ?? "",
        models: modelList(env.OPENROUTER_MODEL, DEFAULT_OPENROUTER_MODELS),
        extraHeaders: {
          "HTTP-Referer": "https://vetguide-api.pman6665.workers.dev",
          "X-Title": "VETGUIDE",
        },
      },
      promptText,
      image,
      context,
    )) ??
    (await tryOpenAiCompatible(
      {
        provider: "mistral",
        url: MISTRAL_URL,
        apiKey: env.MISTRAL_API_KEY ?? "",
        models: modelList(env.MISTRAL_MODEL, DEFAULT_MISTRAL_MODELS),
      },
      promptText,
      image,
      context,
    )) ??
    (await tryWorkersAi(env, promptText, image, context));

  if (data) {
    await writeCache(request, hash, data);
    return json(data, 200, {
      "Cache-Control": `public, max-age=${CACHE_TTL_SECONDS}`,
      "X-Vetguide-Cache": "MISS",
    });
  }

  return failureResponse(context);
}

export default {
  async fetch(request: Request, env: Env, _ctx: ExecutionContext): Promise<Response> {
    const path = new URL(request.url).pathname;

    if (path === "/health") {
      return json({
        status: "ok",
        backend: "vetguide",
        mode: hasAnyProvider(env) ? "live" : "mock",
        providers: {
          geminiKeys: parseList(env.GEMINI_API_KEY).length,
          groq: !!env.GROQ_API_KEY?.trim(),
          openrouter: !!env.OPENROUTER_API_KEY?.trim(),
          mistral: !!env.MISTRAL_API_KEY?.trim(),
          workersAi: !!env.AI,
        },
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
