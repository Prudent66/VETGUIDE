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
     * Placeholder secure-backend origin — replace with the deployed
     * Cloudflare Workers URL when the backend goes live (backend/README.md).
     */
    const val BACKEND_BASE_URL = "https://vetguide-backend.example.workers.dev"

    /** Roadmap §5.2: configurable provider timeout, default 20 s. */
    const val BACKEND_TIMEOUT_MS = 20_000L
}
