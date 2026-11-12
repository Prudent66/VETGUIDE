package com.prudent66.vetguide.di

import com.prudent66.vetguide.data.ai.CloudAiProvider
import com.prudent66.vetguide.domain.ai.AiCatalog
import com.prudent66.vetguide.domain.ai.AiOrchestrator
import com.prudent66.vetguide.domain.ai.OnDeviceAiProvider
import com.prudent66.vetguide.domain.repository.SettingsKeys
import com.prudent66.vetguide.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Ktor's default HttpPlainText plugin force-appends "Accept-Charset: UTF-8"
 * to every request (KTOR-5616). Some strict middleboxes reject or stall on
 * it, and curl-based verification never sends it — so strip it right before
 * the engine sends: SendingRequest executes after every request modification
 * (including the default HttpPlainText append).
 */
private val StripAcceptCharset = createClientPlugin("StripAcceptCharset") {
    // SendingRequest runs after every request modification (including the
    // default HttpPlainText append), right before the engine sends.
    on(SendingRequest) { request, _ ->
        request.headers.remove(HttpHeaders.AcceptCharset)
    }
}

/**
 * AI-layer DI (roadmap Phase 5). Real providers now sit behind the §5.1
 * ladder — cloud tier (mode 1, routed through the secure Workers backend,
 * §8) then the on-device tier (mode 3, stubbed until the TFLite model lands).
 * When every provider is exhausted, the orchestrator answers with the mode-4
 * deterministic fallback. FakeAiProvider never enters production DI — it is
 * for fault-injection tests only (§7). The Settings "Cloud AI Analysis"
 * toggle gates the whole ladder: off = deterministic offline results only.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAiHttpClient(): HttpClient = HttpClient(OkHttp) {
        // HttpTimeout.requestTimeoutMillis maps to OkHttp's call timeout, but
        // OkHttp's OWN read/write timeouts default to 10 s — a Gemini vision
        // round-trip that streams no bytes for >10 s was killed mid-flight
        // (SocketTimeoutException) even with the generous request budget,
        // while curl from a PC (no read timeout) succeeded. Set them all
        // explicitly so the 90 s budget is real end to end.
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(AiCatalog.BACKEND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                writeTimeout(AiCatalog.BACKEND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis = AiCatalog.BACKEND_TIMEOUT_MS
            requestTimeoutMillis = AiCatalog.BACKEND_TIMEOUT_MS
        }
        install(Logging) {
            // DEFAULT writes via println → visible in Logcat (System.out tag).
            logger = Logger.DEFAULT
            // HEADERS shows the exact request line + headers sent to the
            // Worker (no body — never dump the base64 image to the log).
            level = LogLevel.HEADERS
        }
        install(StripAcceptCharset)
        defaultRequest {
            accept(ContentType.Application.Json)
        }
    }

    @Provides
    @Singleton
    fun provideCloudAiProvider(client: HttpClient): CloudAiProvider =
        CloudAiProvider(client = client)

    @Provides
    @Singleton
    fun provideOnDeviceAiProvider(): OnDeviceAiProvider = OnDeviceAiProvider()

    @Provides
    @Singleton
    fun provideAiOrchestrator(
        cloudProvider: CloudAiProvider,
        onDeviceProvider: OnDeviceAiProvider,
        settingsRepository: SettingsRepository,
    ): AiOrchestrator = AiOrchestrator(
        providers = listOf(cloudProvider, onDeviceProvider),
        cloudAiEnabled = {
            settingsRepository
                .observeBoolean(SettingsKeys.CLOUD_AI_ENABLED, default = false)
                .first()
        },
    )
}
