package com.prudent66.vetguide.domain.ai

/**
 * Mode-3 on-device tier placeholder (roadmap §5.1): the real TFLite/MediaPipe
 * classifier arrives in a later phase to keep the APK size small. For now the
 * tier always reports an explicit "not implemented" failure so the failover
 * ladder continues to the mode-4 deterministic fallback — it never fabricates
 * a result, and when real, it surfaces with a reduced-reliability banner (§5.1).
 */
class OnDeviceAiProvider(
    override val id: String = AiCatalog.ON_DEVICE_PROVIDER_ID,
) : AiProvider {

    override suspend fun analyze(request: AiRequest): AiAttempt = AiAttempt.Failure(
        "[$id] on-device inference is not implemented yet — TFLite classifier pending",
    )
}
