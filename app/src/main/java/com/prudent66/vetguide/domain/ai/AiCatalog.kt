package com.prudent66.vetguide.domain.ai

/**
 * Single source of AI provider configuration (roadmap §6): provider ids, the
 * secure backend endpoint, and the request budget. The backend URL is public
 * infrastructure, not a secret (§8) — provider keys live only server-side as
 * Workers secrets. Swapping which provider is primary must remain a config
 * change, never a code change (§6 acceptance).
 */
object AiCatalog {

    /** Mode-1 cloud tier — routed through the secure backend (§8), never direct. */
    const val CLOUD_PROVIDER_ID = "cloud-backend"

    /** Mode-3 on-device tier (TFLite/MediaPipe — stubbed in Phase 5, real model later). */
    const val ON_DEVICE_PROVIDER_ID = "on-device"

    /**
     * Live secure-backend origin (Cloudflare Workers). The GEMINI_API_KEY
     * lives server-side as an encrypted Workers secret — never here (§8).
     */
    const val BACKEND_BASE_URL = "https://vetguide-api.pman6665.workers.dev"

    /**
     * Roadmap §5.2: configurable provider timeout. 90 s — an image upload
     * through the Worker plus a Gemini vision round-trip on rural mobile
     * networks routinely exceeds 45 s (the live Worker logged 499
     * Client-Closed-Request at 45 s: the app hung up before Gemini finished).
     */
    const val BACKEND_TIMEOUT_MS = 90_000L
}
