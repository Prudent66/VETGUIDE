package com.prudent66.vetguide.data.ai

/**
 * Thrown by [CloudAiProvider] when the backend returns HTTP 429 (quota exhausted
 * across all keys). This allows the UI layer to show a specific, actionable
 * message before the orchestrator falls back to the offline engine.
 */
class AiQuotaExceededException(message: String = "All cloud analysis capacity exhausted — quota exceeded") : Exception(message)
