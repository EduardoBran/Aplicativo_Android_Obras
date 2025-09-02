package com.luizeduardobrandao.obra.data.repository.impl

import android.content.Context
import android.util.Base64
import androidx.annotation.RawRes
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.AutoFillResult
import com.luizeduardobrandao.obra.data.model.TipoNota
import com.luizeduardobrandao.obra.data.repository.AiAutofillRepository
import com.luizeduardobrandao.obra.di.ChatCompletionRequest
import com.luizeduardobrandao.obra.di.ChatMessage
import com.luizeduardobrandao.obra.di.ContentPart
import com.luizeduardobrandao.obra.di.ImageUrl
import com.luizeduardobrandao.obra.di.OpenAiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementação que usa o endpoint /v1/chat/completions (modelo "gpt-4o") com visão.
 *
 * Observações:
 *  • Prompt vem de res/raw/prompt_nota_autofill.txt
 *  • Enviamos a imagem como data URL (base64).
 *  • Fazemos um parse tolerante do texto de resposta.
 */

class ChatGptAutofillRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: OpenAiService
) : AiAutofillRepository {

    companion object {
        private const val MODEL = "gpt-4o" // modelo com visão
        private val FIELD_ORDER = listOf(
            "Nome do Material",
            "Descrição",
            "Loja",
            "Tipo",
            "Data",
            "Valor"
        )
    }

    override suspend fun analyze(imageBytes: ByteArray, mime: String): Result<AutoFillResult> =
        withContext(Dispatchers.IO) {
            try {
                val prompt = readRawText(R.raw.prompt_nota_autofill)

                val dataUrl = buildDataUrl(imageBytes, mime)

                val messages = listOf(
                    ChatMessage(
                        role = "user",
                        content = listOf(
                            ContentPart(type = "text", text = prompt),
                            ContentPart(
                                type = "image_url",
                                imageUrl = ImageUrl(url = dataUrl, detail = "high")
                            )
                        )
                    )
                )

                val req = ChatCompletionRequest(
                    model = MODEL,
                    temperature = 0.0,
                    messages = messages
                )

                val resp = service.createChatCompletion(req)
                val raw = resp.choices.firstOrNull()?.message?.content?.trim().orEmpty()
                if (raw.isBlank()) return@withContext Result.failure(IllegalStateException("Resposta vazia do modelo."))

                val parsed = parseResponse(raw)
                Result.success(parsed)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    /* ───────────────────── helpers ───────────────────── */

    private fun readRawText(@RawRes id: Int): String =
        context.resources.openRawResource(id).bufferedReader().use { it.readText() }

    private fun buildDataUrl(bytes: ByteArray, mime: String): String {
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:$mime;base64,$b64"
    }

    /**
     * Parser tolerante do texto seguindo exatamente o formato exigido no prompt.
     * Exemplo esperado:
     *
     * - Nome do Material: ...
     * - Descrição: ...
     * - Loja: ...
     * - Tipo: Pintura, Elétrica
     * - Data: 12/08/2025
     * - Valor: 45,90
     */
    private fun parseResponse(text: String): AutoFillResult {
        // normaliza quebras e remove espaços extras nas bordas
        val lines = text
            .replace("\r\n", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Junta linhas “quebradas” de um mesmo campo, se houver
        val map = linkedMapOf<String, String>()
        var currentKey: String? = null
        val keyRegex = Regex("""^\s*[-•]?\s*(.+?):\s*(.*)$""") // "- Nome do Material: valor"

        for (line in lines) {
            val m = keyRegex.find(line)
            if (m != null) {
                val key = m.groupValues[1].trim()
                val value = m.groupValues[2].trim()
                currentKey = FIELD_ORDER.firstOrNull { eqLoose(it, key) } ?: key
                map[currentKey!!] = value
            } else if (currentKey != null) {
                // linha de continuação
                map[currentKey!!] = (map[currentKey!!]!!.trimEnd() + " " + line).trim()
            }
        }

        fun required(field: String): String =
            map[FIELD_ORDER.first { eqLoose(it, field) }]
                ?: error("Campo ausente na resposta: $field")

        val nome = required("Nome do Material")
        val descricao = required("Descrição")
        val loja = required("Loja")
        val tipoStr = required("Tipo")
        val data = required("Data")
        val valorStr = required("Valor")

        val tipos: Set<TipoNota> = tipoStr
            .split(',', ';', '/', '|')
            .map { it.trim() }
            .mapNotNull { TipoNota.fromLooseText(it) }
            .toSet()
            .ifEmpty { setOf(TipoNota.OUTROS) }

        val valorDouble = parseValorPtBr(valorStr)

        return AutoFillResult(
            nomeMaterial = nome,
            descricao = descricao,
            loja = loja,
            tipos = tipos,
            data = data,
            valor = valorDouble
        )
    }

    private fun parseValorPtBr(s: String): Double {
        // Tira R$, pontos de milhar e troca vírgula por ponto
        val cleaned = s
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")
            .replace(".", "")
            .replace(",", ".")
        return cleaned.toDoubleOrNull()
            ?: error("Valor inválido: $s")
    }

    private fun eqLoose(a: String, b: String): Boolean =
        normalize(a) == normalize(b)

    private fun normalize(s: String): String =
        s.lowercase()
            .replace("[áàâã]".toRegex(), "a")
            .replace("[éê]".toRegex(), "e")
            .replace("í".toRegex(), "i")
            .replace("[óôõ]".toRegex(), "o")
            .replace("ú".toRegex(), "u")
            .replace("ç".toRegex(), "c")
            .replace("""\s+""".toRegex(), "")
}