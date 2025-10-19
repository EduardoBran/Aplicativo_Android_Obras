package com.luizeduardobrandao.obra.data.repository.impl

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.luizeduardobrandao.obra.BuildConfig
import com.luizeduardobrandao.obra.data.repository.AiSolutionRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private interface OpenAiApi {
    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") bearer: String,
        @Body body: ChatRequest
    ): ChatResponse
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens") val maxTokens: Int = 1500,
    @SerialName("temperature") val temperature: Double = 0.0,
    @SerialName("top_p") val topP: Double = 1.0,
    val seed: Int? = 12345,
)

@Serializable
private data class Message(
    val role: String,
    // usando "content" como lista de partes para suportar imagem + texto
    val content: List<ContentPart>
)

@Serializable
private data class ContentPart(
    val type: String, // "text" ou "image_url"
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ImageUrl? = null
)

@Serializable
private data class ImageUrl(
    val url: String
)

@Serializable
private data class ChatResponse(
    val choices: List<Choice>
)

@Serializable
private data class Choice(
    val message: ChoiceMessage
)

@Serializable
private data class ChoiceMessage(
    val role: String,
    val content: String
)

@Singleton
class ChatGptSolutionRepositoryImpl @Inject constructor() : AiSolutionRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val service: OpenAiApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openai.com/v1/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        retrofit.create(OpenAiApi::class.java)
    }

    override suspend fun ask(
        prompt: String,
        imageBytes: ByteArray?,
        imageMime: String?
    ): Result<String> = runCatching {

        val contentParts = mutableListOf(
            ContentPart(type = "text", text = prompt)
        )
        if (imageBytes != null && imageMime != null) {
            val base64 = Base64.getEncoder().encodeToString(imageBytes)
            val dataUrl = "data:$imageMime;base64,$base64"
            contentParts += ContentPart(
                type = "image_url",
                imageUrl = ImageUrl(url = dataUrl)
            )
        }

        val req = ChatRequest(
            model = "gpt-5-search-api",
            messages = listOf(
                Message(role = "user", content = contentParts)
            ),
            maxTokens = 1500,
            topP = 1.0,
            seed = 12345,
            temperature = 0.0,
        )

        val resp = service.chatCompletions(
            bearer = "Bearer ${BuildConfig.OPENAI_API_KEY}",
            body = req
        )
        resp.choices.firstOrNull()?.message?.content
            ?: error("Resposta vazia da IA")
    }
}