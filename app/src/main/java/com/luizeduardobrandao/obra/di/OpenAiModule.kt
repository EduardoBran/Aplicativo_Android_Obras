package com.luizeduardobrandao.obra.di

import com.luizeduardobrandao.obra.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Módulo de rede para chamadas ao OpenAI (modelo 4o com visão).
 * Usa Retrofit + OkHttp + kotlinx.serialization.
 *
 * Observações:
 * - A chave é lida de BuildConfig.OPENAI_API_KEY (injetada via build.gradle).
 * - Converter JSON: kotlinx.serialization (plugin aplicado no módulo :app).
 * - Endpoint usado: /v1/chat/completions com content [text + image_url].
 */

@Module
@InstallIn(SingletonComponent::class)
object OpenAiModule {

    private const val OPENAI_BASE_URL = "https://api.openai.com/"
    private const val CONTENT_TYPE_JSON = "application/json"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = false
    }

    @Provides
    @Singleton
    fun provideOpenAiOkHttpClient(): OkHttpClient {
        val key = BuildConfig.OPENAI_API_KEY
        require(key.isNotBlank()) {
            "OPENAI_API_KEY não configurada. Verifique local.properties / variável de ambiente."
        }

        val authInterceptor = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .build()
            chain.proceed(req)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideOpenAiRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val converter = json.asConverterFactory(CONTENT_TYPE_JSON.toMediaType())
        return Retrofit.Builder()
            .baseUrl(OPENAI_BASE_URL)
            .client(client)
            .addConverterFactory(converter)
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAiService(retrofit: Retrofit): OpenAiService =
        retrofit.create(OpenAiService::class.java)
}

/* ============================
   Retrofit service + DTOs
   ============================ */

interface OpenAiService {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Body body: ChatCompletionRequest
    ): ChatCompletionResponse
}

/** Requisição para Chat Completions com visão (gpt-4o). */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val temperature: Double = 0.0,
    val messages: List<ChatMessage>
)

@Serializable
data class ChatMessage(
    val role: String, // "system" | "user" | "assistant"
    val content: List<ContentPart>
)

/**
 * Parte do conteúdo. Use:
 *  - type="text", text="..."
 *  - type="image_url", image_url={ url="data:image/jpeg;base64,..." }
 */
@Serializable
data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ImageUrl? = null
)

@Serializable
data class ImageUrl(
    val url: String,
    val detail: String? = "auto" // "low" | "high" | "auto"
)

/** Resposta resumida: pegamos apenas o content do 1º choice. */
@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: AssistantMessage
)

@Serializable
data class AssistantMessage(
    val role: String,
    val content: String
)