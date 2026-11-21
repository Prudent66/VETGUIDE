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
     * Roadmap §5.2: per-provider timeout ceiling. 45 s covers an 800px JPEG
     * upload plus a vision round-trip on a weak 2G/3G link while guaranteeing
     * the failover ladder never appears to hang for minutes.
     */
    const val BACKEND_TIMEOUT_MS = 45_000L

    /** Connect ceiling for a single provider attempt (slow rural networks). */
    const val BACKEND_CONNECT_TIMEOUT_MS = 30_000L
}
