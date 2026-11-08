package com.prudent66.vetguide.domain.ai

/**
 * Scriptable provider for development and fault-injection tests (roadmap §5
 * acceptance): fails while [failuresBeforeSuccess] > 0 — each failure consumes
 * one from the counter, so tests can script isolated failures by flipping the
 * var between calls. Never ships wired into production DI — production uses
 * real providers only.
 */
class FakeAiProvider(
    override val id: String,
    /** Number of upcoming analyzed calls that fail; mutable for re-scripting. */
    var failuresBeforeSuccess: Int = 0,
    private val successText: String = "[$id] scripted analysis",
) : AiProvider {

    var callCount: Int = 0
        private set

    override suspend fun analyze(request: AiRequest): AiAttempt {
        callCount += 1
        return if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess -= 1
            AiAttempt.Failure("[$id] simulated failure #$callCount")
        } else {
            AiAttempt.Success(successText)
        }
    }
}
