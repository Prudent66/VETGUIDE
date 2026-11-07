package com.prudent66.vetguide.domain.ai

/**
 * Scriptable provider for development and fault-injection tests (roadmap §5
 * acceptance): fails its first [failuresBeforeSuccess] calls, then succeeds.
 * Never ships wired into production DI — production uses real providers only.
 */
class FakeAiProvider(
    override val id: String,
    private val failuresBeforeSuccess: Int = 0,
    private val successText: String = "[$id] scripted analysis",
) : AiProvider {

    var callCount: Int = 0
        private set

    override suspend fun analyze(request: AiRequest): AiAttempt {
        callCount += 1
        return if (callCount <= failuresBeforeSuccess) {
            AiAttempt.Failure("[$id] simulated failure #$callCount")
        } else {
            AiAttempt.Success(successText)
        }
    }
}
