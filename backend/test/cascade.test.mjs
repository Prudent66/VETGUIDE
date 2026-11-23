// Run with `npm test` from backend/: compiles src/index.ts to .test-build/
// and exercises the provider cascade with mocked upstreams (no network/keys).
import worker from "../.test-build/index.js";
import assert from "node:assert/strict";

const ALL_GEMINI = "https://generativelanguage.googleapis.com/v1beta/models";

function installCache() {
  const store = new Map();
  globalThis.caches = {
    default: {
      async match(req) {
        return store.get(typeof req === "string" ? req : req.url);
      },
      async put(req, res) {
        const key = typeof req === "string" ? req : req.url;
        const body = await res.text();
        store.set(key, new Response(body, { status: res.status, headers: res.headers }));
      },
    },
  };
  return store;
}

function validBody(provider) {
  return JSON.stringify({
    provider,
    model: "claimed-model",
    findings: ["  Mild erythema ", 42],
    suspectedConditions: [
      { name: " Otitis externa ", confidence: 0.82 },
      { name: "bad", confidence: "nope" },
    ],
    recommendedNextSteps: ["Clean ear canal", ""],
  });
}

const analyzeBody = JSON.stringify({
  prompt: "Dog, 4 months — vomiting",
  imageBase64: "data:image/jpeg;base64,QUJD",
  clinicalContext: "Vomiting 2 days",
});

function req() {
  return new Request("https://vetguide-api.test/analyze", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: analyzeBody,
  });
}

function geminiOk(text) {
  return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text }] } }] }), {
    status: 200,
  });
}

function gemini429() {
  return new Response(JSON.stringify({ error: "quota" }), { status: 429 });
}

function openAiOk(text) {
  return new Response(JSON.stringify({ choices: [{ message: { content: text } }] }), {
    status: 200,
  });
}

const tests = [];
function test(name, fn) {
  tests.push({ name, fn });
}

// 1) Gemini fully rate-limited -> Groq answers; all Gemini key/model pairs tried.
test("falls back from exhausted Gemini to Groq and normalizes", async () => {
  installCache();
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url, body: JSON.parse(init.body) });
    if (url.startsWith(ALL_GEMINI)) return gemini429();
    if (url.includes("api.groq.com")) return openAiOk("```json\n" + validBody("x") + "\n```");
    return new Response("nope", { status: 500 });
  };

  const env = {
    GEMINI_API_KEY: "k1, k2 ,k3,k2",
    GROQ_API_KEY: "groq-key",
  };
  const res = await worker.fetch(req(), env, {});
  const data = await res.json();

  assert.equal(res.status, 200);
  assert.equal(data.provider, "groq");
  assert.equal(data.model, "qwen/qwen3.8-27b");
  assert.deepEqual(data.findings, ["Mild erythema"]);
  assert.deepEqual(data.suspectedConditions, [
    { name: "Otitis externa", confidence: 0.82 },
    { name: "bad", confidence: 0 },
  ]);
  assert.deepEqual(data.recommendedNextSteps, ["Clean ear canal"]);

  const geminiCalls = calls.filter((c) => c.url.startsWith(ALL_GEMINI));
  assert.equal(geminiCalls.length, 9, "3 unique keys x 3 models");
  const geminiModels = geminiCalls.map((c) => c.url.split("/models/")[1].split(":")[0]);
  assert.ok(geminiModels.includes("gemini-2.5-flash"));
  assert.ok(geminiModels.includes("gemini-2.0-flash"));
  assert.ok(geminiModels.includes("gemini-2.5-flash-lite"));
  // thinking disabled only for 2.5 family
  for (const call of geminiCalls) {
    const model = call.url.split("/models/")[1].split(":")[0];
    const cfg = call.body.generationConfig;
    if (model.startsWith("gemini-2.5")) {
      assert.deepEqual(cfg.thinkingConfig, { thinkingBudget: 0 });
    } else {
      assert.equal(cfg.thinkingConfig, undefined);
    }
  }
  const groqCall = calls.find((c) => c.url.includes("api.groq.com"));
  assert.equal(groqCall.body.messages[0].content[1].image_url.url, "data:image/jpeg;base64,QUJD");
});

// 2) Second identical request is served from the edge cache.
test("deduplicates identical image+prompt responses for repeated testers", async () => {
  const store = installCache();
  let fetchCount = 0;
  globalThis.fetch = async () => {
    fetchCount += 1;
    return geminiOk(validBody("gemini"));
  };
  const env = { GEMINI_API_KEY: "k1" };

  const first = await worker.fetch(req(), env, {});
  const firstData = await first.json();
  assert.equal(first.headers.get("X-Vetguide-Cache"), "MISS");
  assert.equal(firstData.provider, "gemini");
  assert.equal(fetchCount, 1);

  const second = await worker.fetch(req(), env, {});
  const secondData = await second.json();
  assert.equal(second.headers.get("X-Vetguide-Cache"), "HIT");
  assert.equal(secondData.provider, "gemini");
  assert.equal(fetchCount, 1, "cache hit must not call upstream");
  assert.equal(store.size, 1);
});

// 3) Gemini succeeds after a retired model 404.
test("skips retired Gemini models (404) and uses the next candidate", async () => {
  installCache();
  const seen = [];
  globalThis.fetch = async (url) => {
    seen.push(url);
    if (url.includes("gemini-2.5-flash:")) return new Response("{}", { status: 404 });
    return geminiOk(validBody("gemini"));
  };

  const res = await worker.fetch(req(), { GEMINI_API_KEY: "k1" }, {});
  const data = await res.json();
  assert.equal(data.provider, "gemini");
  assert.equal(data.model, "gemini-2.0-flash");
  assert.equal(seen.length, 2);
});

// 4) All external providers exhausted -> 429 capacity error.
test("returns 429 capacity error when every provider is rate limited", async () => {
  installCache();
  globalThis.fetch = async () => new Response("{}", { status: 429 });
  const env = {
    GEMINI_API_KEY: "k1",
    GROQ_API_KEY: "g",
    OPENROUTER_API_KEY: "o",
    MISTRAL_API_KEY: "m",
  };
  const res = await worker.fetch(req(), env, {});
  const data = await res.json();
  assert.equal(res.status, 429);
  assert.match(data.error, /maximum capacity/);
  assert.match(data.providers, /gemini/);
  assert.match(data.providers, /groq/);
  assert.match(data.providers, /openrouter/);
  assert.match(data.providers, /mistral/);
});

// 5) Workers AI binding answers when no HTTP provider is configured.
test("uses the Workers AI binding as the last tier", async () => {
  installCache();
  let fetchCount = 0;
  globalThis.fetch = async () => {
    fetchCount += 1;
    return new Response("{}", { status: 500 });
  };
  const seen = [];
  const env = {
    AI: {
      async run(model, inputs) {
        seen.push({ model, inputs });
        return { response: validBody("workers") };
      },
    },
  };
  const res = await worker.fetch(req(), env, {});
  const data = await res.json();
  assert.equal(res.status, 200);
  assert.equal(data.provider, "workers-ai");
  assert.equal(data.model, "@cf/meta/llama-3.2-11b-vision-instruct");
  assert.equal(fetchCount, 0);
  assert.equal(seen[0].inputs.messages[0].content[1].image_url.url, "data:image/jpeg;base64,QUJD");
});

// 6) No credentials at all -> labeled mock (local/CI behaviour preserved).
test("serves the labeled mock when no provider is configured", async () => {
  installCache();
  globalThis.fetch = async () => {
    throw new Error("must not be called");
  };
  const res = await worker.fetch(req(), {}, {});
  const data = await res.json();
  assert.equal(res.status, 200);
  assert.equal(data.provider, "mock");
});

// 7) Health endpoint reports configured providers without leaking keys.
test("health reports provider availability only", async () => {
  const res = await worker.fetch(
    new Request("https://vetguide-api.test/health"),
    { GEMINI_API_KEY: "k1,k2", GROQ_API_KEY: "g", AI: { run: async () => ({}) } },
    {},
  );
  const data = await res.json();
  assert.equal(data.mode, "live");
  assert.deepEqual(data.providers, {
    geminiKeys: 2,
    groq: true,
    openrouter: false,
    mistral: false,
    workersAi: true,
  });
  assert.equal(JSON.stringify(data).includes("k1"), false);
});

// 8) Model override replaces the cascade.
test("honours GEMINI_MODEL override list", async () => {
  installCache();
  const seen = [];
  globalThis.fetch = async (url) => {
    seen.push(url);
    return new Response("{}", { status: 429 });
  };
  await worker.fetch(req(), { GEMINI_API_KEY: "k1,k2", GEMINI_MODEL: "custom-a,custom-b" }, {});
  assert.equal(seen.length, 4, "2 keys x 2 override models");
  assert.ok(seen.every((u) => u.includes("custom-a") || u.includes("custom-b")));
});

// 9) Fenced + prose JSON is parsed; non-JSON yields a failure, not a guess.
test("rejects unusable model output instead of guessing", async () => {
  installCache();
  globalThis.fetch = async () => geminiOk("I cannot help with that.");
  const res = await worker.fetch(req(), { GEMINI_API_KEY: "k1" }, {});
  assert.equal(res.status, 502);
  const data = await res.json();
  assert.match(data.error, /Cloud AI unavailable/);
});

let failed = 0;
for (const { name, fn } of tests) {
  try {
    await fn();
    console.log("PASS", name);
  } catch (error) {
    failed += 1;
    console.error("FAIL", name);
    console.error(error);
  }
}
console.log(failed === 0 ? "ALL TESTS PASSED" : `${failed} TEST(S) FAILED`);
process.exit(failed === 0 ? 0 : 1);
