package com.prudent66.vetguide.domain.ai

/**
 * Builds the roadmap §5.1 mode-4 deterministic output (guided questionnaire +
 * offline Knowledge Base). Mode 4 is NOT an AiProvider, never runs through the
 * provider interface, always carries no AI provenance, and its output is
 * framed as rule-based placeholder support — never diagnostic (§10).
 */
object DeterministicFallbackBuilder {

    fun build(request: AiRequest): String = buildString {
        append("Offline mode — AI providers unavailable. ")
        append("Rule-based summary of the recorded findings (questionnaire + offline ")
        append("Knowledge Base) for: ")
        append(request.headline)
        append(" — ")
        append(request.findings)
        append(". Placeholder rule-based engine — requires veterinary review; ")
        append("the veterinarian remains responsible for all clinical decisions.")
    }
}
