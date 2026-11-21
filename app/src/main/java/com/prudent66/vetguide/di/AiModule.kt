package com.prudent66.vetguide.di

import com.prudent66.vetguide.data.ai.CloudAiProvider
import com.prudent66.vetguide.domain.ai.AiCatalog
import com.prudent66.vetguide.domain.ai.AiOrchestrator
import com.prudent66.vetguide.domain.ai.OnDeviceAiProvider
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

/** Routes Ktor network logging to Logcat under a dedicated filterable tag. */
private object KtorLogcatLogger : Logger {
    override fun log(message: String) {
        android.util.Log.d("VETGUIDE-HTTP", message)
    }
}

/**
 * AI-layer DI (roadmap Phase 5). Real providers now sit behind the §5.1
 * ladder — cloud tier (mode 1, routed through the secure Workers backend,
 * §8) then the on-device tier (mode 3, stubbed until the TFLite model lands).
 * When every provider is exhausted, the orchestrator answers with the mode-4
 * deterministic fallback. FakeAiProvider never enters production DI — it is
 * for fault-injection tests only (§7). Cloud analysis is always attempted:
 * the Settings toggle was removed so a stale preference can never suppress it.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAiHttpClient(): HttpClient = HttpClient(OkHttp) {
        // HttpTimeout.requestTimeoutMillis maps to OkHttp's call timeout, but
        // OkHttp's OWN read/write timeouts default to 10 s — a slow upload
        // that streams no bytes for >10 s was killed mid-flight. Set every
        // timeout explicitly against the shared 45 s per-attempt ceiling so a
        // stalled 2G/3G connection falls over instead of hanging for minutes.
        engine {
            config {
                connectTimeout(
                    AiCatalog.BACKEND_CONNECT_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS,
                )
                readTimeout(AiCatalog.BACKEND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                writeTimeout(AiCatalog.BACKEND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = AiCatalog.BACKEND_CONNECT_TIMEOUT_MS
            socketTimeoutMillis = AiCatalog.BACKEND_TIMEOUT_MS
            requestTimeoutMillis = AiCatalog.BACKEND_TIMEOUT_MS
        }
        install(Logging) {
            // Logcat under the "VETGUIDE-HTTP" tag (adb logcat -s VETGUIDE-HTTP).
            logger = KtorLogcatLogger
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
    ): AiOrchestrator = AiOrchestrator(
        providers = listOf(cloudProvider, onDeviceProvider),
    )
}
