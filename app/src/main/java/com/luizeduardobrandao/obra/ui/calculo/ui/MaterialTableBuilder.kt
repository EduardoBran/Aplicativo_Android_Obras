package com.luizeduardobrandao.obra.ui.calculo.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel
import com.luizeduardobrandao.obra.ui.calculo.utils.NumberFormatter
import java.util.*
import kotlin.math.ceil
import kotlin.math.max

/**
 * Construtor de tabelas de materiais para exibição no resultado
 */
class MaterialTableBuilder(
    private val context: Context,
    private val layoutInflater: LayoutInflater
) {
    // ✅ Helper de responsividade
    private val responsiveHelper = TableResponsiveHelper(context)

    /** Cria linha de cabeçalho da tabela */
    @SuppressLint("InflateParams")
    fun makeHeaderRow(): View {
        val row = layoutInflater.inflate(R.layout.item_material_row, null, false)

        // Background do header
        row.setBackgroundColor(ContextCompat.getColor(context, R.color.tableHeaderBg))
        // Padding responsivo
        row.setPadding(
            responsiveHelper.headerPaddingHorizontal, responsiveHelper.headerPaddingVertical,
            responsiveHelper.headerPaddingHorizontal, responsiveHelper.headerPaddingVertical
        )

        // Altura mínima responsiva
        row.minimumHeight = responsiveHelper.headerMinHeight

        val headerTextColor = ContextCompat.getColor(context, R.color.tableHeaderText)

        val tvItem = row.findViewById<TextView>(R.id.tvItem)
        val tvUnid = row.findViewById<TextView>(R.id.tvUnid)
        val tvQtdUsada = row.findViewById<TextView>(R.id.tvQtdUsada)
        val tvQtdComprar = row.findViewById<TextView>(R.id.tvQtdComprar)
        val tvObs = row.findViewById<TextView>(R.id.tvObservacao)
        // Coluna Item com texto responsivo
        tvItem.apply {
            text = context.getString(R.string.col_item)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(headerTextColor)
            responsiveHelper.setTextSizeSp(this, responsiveHelper.headerTextSize)
            letterSpacing = responsiveHelper.headerLetterSpacing
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL

            val params =
                layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToTop =
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom =
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            params.verticalBias = 0.5f
            layoutParams = params
        }
        tvUnid.visibility = View.GONE
        // Coluna Qtd com texto responsivo
        tvQtdUsada.apply {
            text = context.getString(R.string.col_qtd)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(headerTextColor)
            responsiveHelper.setTextSizeSp(this, responsiveHelper.headerTextSize)
            letterSpacing = responsiveHelper.headerLetterSpacing
            gravity = android.view.Gravity.CENTER

            val params =
                layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToTop =
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom =
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            params.verticalBias = 0.5f
            layoutParams = params
        }
        // Coluna Comprar com texto responsivo
        tvQtdComprar.apply {
            text = context.getString(R.string.col_comprar)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(headerTextColor)
            responsiveHelper.setTextSizeSp(this, responsiveHelper.headerTextSize)
            letterSpacing = responsiveHelper.headerLetterSpacing
            gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL

            val params =
                layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topToTop =
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom =
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            params.verticalBias = 0.5f
            layoutParams = params
        }
        tvObs.visibility = View.GONE
        // Atualizar guidelines
        updateRowGuidelines(row)

        return row
    }

    /** Cria linha de dados da tabela */
    @SuppressLint("InflateParams")
    fun makeDataRow(item: CalcRevestimentoViewModel.MaterialItem): View {
        val row = layoutInflater.inflate(R.layout.item_material_row, null, false)

        // Padding e altura mínima responsivos
        row.setPadding(
            responsiveHelper.rowPaddingHorizontal, responsiveHelper.rowPaddingVertical,
            responsiveHelper.rowPaddingHorizontal, responsiveHelper.rowPaddingVertical
        )
        row.minimumHeight = responsiveHelper.rowMinHeight

        val tvItem = row.findViewById<TextView>(R.id.tvItem)
        val tvUnid = row.findViewById<TextView>(R.id.tvUnid)
        val tvQtdUsada = row.findViewById<TextView>(R.id.tvQtdUsada)
        val tvQtdComprar = row.findViewById<TextView>(R.id.tvQtdComprar)
        val tvObs = row.findViewById<TextView>(R.id.tvObservacao)

        // Aplicar tamanhos de texto responsivos
        responsiveHelper.setTextSizeSp(tvItem, responsiveHelper.rowTextSize)
        responsiveHelper.setTextSizeSp(tvQtdUsada, responsiveHelper.rowTextSize)
        responsiveHelper.setTextSizeSp(tvQtdComprar, responsiveHelper.rowComprarTextSize)
        responsiveHelper.setTextSizeSp(tvObs, responsiveHelper.observacaoTextSize)

        tvItem.text = item.item
        tvUnid.visibility = View.GONE

        val obsLower = item.observacao?.lowercase(Locale.getDefault()) ?: ""

        val isRodapeMesmaPeca =
            item.item.equals("Rodapé", ignoreCase = true) && obsLower.contains("mesma peça")
        val isRodapePecaPronta =
            item.item.equals("Rodapé", ignoreCase = true) && obsLower.contains("peça pronta")

        val qtdStr = when {
            isRodapeMesmaPeca -> "${formatQtdRaw(item.qtd)}m²"
            isRodapePecaPronta -> "${formatQtdRaw(item.qtd)}m²"
            else -> {
                val unidade = item.unid.trim()
                val valor = formatQtdRaw(item.qtd)
                if (unidade.isNotEmpty()) "$valor$unidade" else valor
            }
        }
        tvQtdUsada.text = qtdStr
        tvQtdComprar.text = buildComprarCell(item)

        if (!item.observacao.isNullOrBlank()) {
            tvObs.text = item.observacao
            tvObs.visibility = View.VISIBLE

            // Margens e constraints da observação
            val obsParams =
                tvObs.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            obsParams.topMargin = responsiveHelper.observacaoMarginTop
            tvObs.layoutParams = obsParams
        } else {
            tvObs.visibility = View.GONE
        }

        // Atualizar guidelines
        updateRowGuidelines(row)

        return row
    }

    /** Atualiza guidelines de uma linha específica */
    private fun updateRowGuidelines(row: View) {
        val guidelineItemEnd =
            row.findViewById<androidx.constraintlayout.widget.Guideline>(R.id.guidelineItemEnd)
        val guidelineQtdEnd =
            row.findViewById<androidx.constraintlayout.widget.Guideline>(R.id.guidelineQtdEnd)

        guidelineItemEnd?.let { guideline ->
            val params =
                guideline.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.guidePercent = responsiveHelper.guidelineItemEnd
            guideline.layoutParams = params
        }

        guidelineQtdEnd?.let { guideline ->
            val params =
                guideline.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.guidePercent = responsiveHelper.guidelineQtdEnd
            guideline.layoutParams = params
        }
    }

    private fun formatQtdRaw(v: Double): String {
        // Sem arredondar pra inteiro.
        // Mostra:
        // - sem casas se for exato (18.0 -> "18")
        // - até 2 casas se tiver decimal (17.6 -> "17,6"; 1.3333 -> "1,33")
        val abs = kotlin.math.abs(v)
        return when {
            abs == kotlin.math.floor(abs) -> abs.toInt().toString()
            else -> {
                // limita casas só para não ficar 17.6000000001, mas sem jogar pra 18
                val s = String.format(Locale.getDefault(), "%.2f", v)
                s.trimEnd('0').trimEnd(',', '.')
            }
        }.replace('.', ',') // se quiser separador brasileiro
    }

    /** Constrói célula "Comprar" com extração inteligente de embalagens */
    fun buildComprarCell(item: CalcRevestimentoViewModel.MaterialItem): String {
        val alvo = max(0.0, item.qtd)
        if (alvo <= 0.0) return "0"

        val nome = item.item
        val unid = item.unid
        val obs = item.observacao?.lowercase(Locale.getDefault())

        // ============== BLOCO 1: RODAPÉ (casos especiais) ==============
        if (nome.equals("Rodapé", ignoreCase = true) &&
            obs?.contains("mesma peça") == true
        ) {
            return "Incluso"
        }

        if (nome.equals("Rodapé", ignoreCase = true) &&
            obs?.contains("peça pronta") == true
        ) {
            val match = Regex("""(\d+)\s+peças""").find(obs)
            val qtdPc = match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1)
            if (qtdPc != null) {
                return "$qtdPc pc"
            }
        }

        // ============== BLOCO 2: MATERIAIS ESPECÍFICOS (SEMPRE PRIMEIRO!) ==============

        // REJUNTE (1kg e 5kg)
        if (nome.contains("Rejunte", ignoreCase = true) &&
            unid.equals("kg", true)
        ) {
            val pack = bestPackCombo(alvo, listOf(5.0, 1.0))
            return pack.entries
                .filter { it.value > 0 }
                .sortedByDescending { it.key }
                .joinToString(" + ") { (size, count) ->
                    val sizeInt = size.toInt()
                    if (count == 1) "1 sc ${sizeInt}kg"
                    else "$count sc ${sizeInt}kg"
                }.ifEmpty { "0" }
        }

        // ESPAÇADORES E CUNHAS (50un e 100un)
        if ((nome.equals("Espaçadores", ignoreCase = true) ||
                    nome.equals("Cunhas", ignoreCase = true)) &&
            unid.equals("un", ignoreCase = true)
        ) {
            val qtdTotal = alvo.toInt().coerceAtLeast(0)
            if (qtdTotal <= 0) return "0un"

            val pack = bestPackCombo(qtdTotal.toDouble(), listOf(100.0, 50.0))
            return pack.entries
                .filter { it.value > 0 }
                .sortedByDescending { it.key }
                .joinToString(" + ") { (size, count) ->
                    val sizeInt = size.toInt()
                    if (count == 1) "1 pct ${sizeInt}un"
                    else "$count pct ${sizeInt}un"
                }.ifEmpty { "0un" }
        }

        // MASSA PVA (20kg)
        if (nome.contains("Massa PVA", ignoreCase = true) &&
            unid.equals("kg", ignoreCase = true)
        ) {
            val sacos20 = ceil(alvo / 20.0).toInt().coerceAtLeast(1)
            return if (sacos20 == 1) "1 sc 20kg" else "$sacos20 sc 20kg"
        }

        // COLA PARA PISO VINÍLICO (1kg, 4kg, 18kg)
        if (nome.contains("Cola", ignoreCase = true) &&
            unid.equals("kg", ignoreCase = true)
        ) {
            return buildColaComprar(alvo)
        }

        // ============== BLOCO 3: OUTROS MATERIAIS ESPECÍFICOS ==============

        // Pedra em m²
        if ((unid.equals("m²", true) || unid.equals("m2", true)) &&
            nome.contains("pedra", ignoreCase = true)
        ) {
            return NumberFormatter.format(alvo)
        }

        // Areia geral em m³
        if (nome.equals("Areia", ignoreCase = true) && unid.equals("m³", true)) {
            val areiaKg = alvo * 1400.0
            val sacos20 = ceil(areiaKg / 20.0).toInt().coerceAtLeast(1)
            return if (sacos20 == 1) "1 sc 20kg" else "$sacos20 sc 20kg"
        }

        // Argamassa colante
        if (nome.startsWith("Argamassa", ignoreCase = true) &&
            unid.equals("kg", true)
        ) {
            val n20 = ceil(alvo / 20.0).toInt()
            return if (n20 == 1) "1 sc 20kg" else "$n20 sc 20kg"
        }

        // Areia de assentamento
        if (nome.equals("Areia de assentamento", ignoreCase = true) &&
            unid.equals("m³", true)
        ) {
            val areiaKg = alvo * 1400.0
            val sacos20 = ceil(areiaKg / 20.0).toInt().coerceAtLeast(1)
            return if (sacos20 == 1) "1 sc 20kg" else "$sacos20 sc 20kg"
        }

        // Cimento
        if (nome.equals("Cimento", ignoreCase = true) &&
            unid.equals("kg", ignoreCase = true)
        ) {
            return buildCimentoComprar(alvo)
        }

        // Fixador Mecânico
        if (nome.contains("Fixador Mecânico", ignoreCase = true) &&
            unid.equals("un", ignoreCase = true)
        ) {
            val n = alvo.toInt().coerceAtLeast(0)
            return if (n <= 0) "0un" else "${n}un"
        }

        // PASTILHA
        if (nome.contains("Pastilha", ignoreCase = true) &&
            (unid.equals("m²", true) || unid.equals("m2", true)) &&
            obs != null
        ) {
            val mantasPorM2Match = Regex(
                pattern = """mantas por m²:\s*([\d.,]+)""",
                option = RegexOption.IGNORE_CASE
            ).find(obs)

            val mantasPorM2 = mantasPorM2Match
                ?.groupValues?.get(1)
                ?.replace(",", ".")
                ?.toDoubleOrNull()

            val totalPecas = Regex("""(\d+)\s+peças""")
                .find(obs)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()

            val totalMantas = mantasPorM2
                ?.let { ceil(alvo * it).toInt().coerceAtLeast(1) }

            when {
                totalMantas != null && totalPecas != null -> {
                    return "$totalMantas mantas ou $totalPecas pc"
                }

                totalMantas != null -> {
                    return "$totalMantas mantas"
                }

                totalPecas != null -> {
                    return "${totalPecas}pc"
                }
            }
        }

        // ============== BLOCO 4: HEURÍSTICAS GENÉRICAS (SEMPRE POR ÚLTIMO!) ==============

        if (obs?.contains("informativo") == true) return "—"

        // Mármore/Granito com "N peças"
        val isMGItem = nome.startsWith("Mármore", ignoreCase = true) ||
                nome.startsWith("Granito", ignoreCase = true)

        if (isMGItem &&
            (unid.equals("m²", true) || unid.equals("m2", true)) &&
            obs != null
        ) {
            Regex("""(\d+)\s+peças?""").find(obs)?.let { m ->
                return "${m.groupValues[1]} pc"
            }
        }

        // "72 peças (5 caixas)" → 5 cx
        if (obs != null) {
            Regex("""(\d+)\s+peças?\s*\((\d+)\s+caixas?\)""").find(obs)?.let {
                return "${it.groupValues[2]} cx"
            }

            // "72 peças" → 72 pc
            Regex("""(\d+)\s+peças?""").find(obs)?.let {
                return "${it.groupValues[1]} pc"
            }

            // "2x 50 kg + 1x 25 kg"
            if (obs.contains("+")) {
                val partes = mutableListOf<String>()
                Regex("""(\d+)\s*x\s*([\d.,]+)\s*(kg|l|un|unid|unidades)""")
                    .findAll(obs).forEach {
                        val (qtd, tam, u) = it.destructured
                        partes += "${qtd}×${tam}${u.uppercase()}"
                    }
                if (partes.isNotEmpty()) return partes.joinToString(" + ")
            }

            // "10 sacos de 20 kg"
            Regex("""(\d+)\s*(?:x|sacos?\s+de|pacotes?\s+de|baldes?\s+de)\s*([\d.,]+)\s*(kg|l|un|unid|unidades)""")
                .find(obs)?.let {
                    val (qtd, tam, u) = it.destructured
                    return "${qtd}× ${tam}${u.uppercase()}"
                }
        }

        // ============== FALLBACK ==============
        return NumberFormatter.format(alvo)
    }

    // ================= HELPERS INTERNOS =================

    private fun bestPackCombo(target: Double, sizes: List<Double>): Map<Double, Int> {
        if (target <= 0.0) return emptyMap()

        val sorted = sizes.sortedDescending()

        // Estratégia gulosa: usa o maior tamanho possível primeiro
        val result = mutableMapOf<Double, Int>()
        var remaining = target

        for (size in sorted) {
            if (remaining <= 0.0) break

            val count = (remaining / size).toInt()
            if (count > 0) {
                result[size] = count
                remaining -= count * size
            }
        }

        // Se sobrou resto, completa com o menor tamanho
        if (remaining > 0.0) {
            val smallest = sorted.last()
            val extraCount = ceil(remaining / smallest).toInt()
            result[smallest] = (result[smallest] ?: 0) + extraCount
        }

        return result
    }

    /** Empacota cola (kg) em potes de 1kg, 4kg e 18kg (máximo 2 tipos) */
    private fun buildColaComprar(colaKg: Double): String {
        val kg = colaKg.coerceAtLeast(0.0)
        if (kg <= 0.0) return "0"

        // Tenta combinações de 2 tamanhos apenas
        val sizes = listOf(18.0, 4.0, 1.0)

        // Estratégia: encontrar a melhor combinação de EXATAMENTE 2 tipos
        data class Combo(
            val tipo1: Double,
            val qtd1: Int,
            val tipo2: Double?,
            val qtd2: Int?,
            val total: Double,
            val sobra: Double
        )

        val combos = mutableListOf<Combo>()

        // Tentar todas as combinações de 2 tamanhos
        for (i in sizes.indices) {
            for (j in i + 1 until sizes.size) {
                val maior = sizes[i]
                val menor = sizes[j]

                // Quantos do maior?
                val maxMaior = ceil(kg / maior).toInt() + 2
                for (qtdMaior in 0..maxMaior) {
                    val restoMaior = kg - (qtdMaior * maior)
                    if (restoMaior <= 0.0) {
                        // Só o tamanho maior já resolve
                        if (qtdMaior > 0) {
                            combos += Combo(
                                maior,
                                qtdMaior,
                                null,
                                null,
                                qtdMaior * maior,
                                qtdMaior * maior - kg
                            )
                        }
                    } else {
                        // Completa com o menor
                        val qtdMenor = ceil(restoMaior / menor).toInt()
                        val total = (qtdMaior * maior) + (qtdMenor * menor)
                        if (total >= kg) {
                            combos += Combo(maior, qtdMaior, menor, qtdMenor, total, total - kg)
                        }
                    }
                }
            }
        }

        // Tentar apenas 1 tamanho
        for (size in sizes) {
            val qtd = ceil(kg / size).toInt()
            combos += Combo(size, qtd, null, null, qtd * size, qtd * size - kg)
        }

        // Escolher a melhor: menor sobra, depois menos embalagens
        val melhor = combos
            .filter { it.total >= kg }
            .minWithOrNull(compareBy({ it.sobra }, { (it.qtd1 + (it.qtd2 ?: 0)) }))
            ?: Combo(1.0, ceil(kg).toInt(), null, null, ceil(kg), 0.0)

        // Montar string
        val partes = mutableListOf<String>()

        if (melhor.qtd1 > 0) {
            val sizeInt = melhor.tipo1.toInt()
            partes += if (melhor.qtd1 == 1) "1 pt ${sizeInt}kg" else "${melhor.qtd1} pt ${sizeInt}kg"
        }

        if (melhor.tipo2 != null && melhor.qtd2 != null && melhor.qtd2 > 0) {
            val sizeInt = melhor.tipo2.toInt()
            partes += if (melhor.qtd2 == 1) "1 pt ${sizeInt}kg" else "${melhor.qtd2} pt ${sizeInt}kg"
        }

        return if (partes.isEmpty()) "0" else partes.joinToString(" + ")
    }

    /** Empacota o cimento (kg) em sacos de 25 kg e/ou 50 kg */
    private fun buildCimentoComprar(cimentoKg: Double): String {
        val kg = cimentoKg.coerceAtLeast(0.0)
        if (kg <= 0.0) return "0"

        return when {
            kg <= 25.0 -> {
                val sacos = ceil(kg / 25.0).toInt()
                if (sacos == 1) "1 sc 25kg" else "$sacos sc 25kg"
            }

            kg <= 50.0 -> {
                val sacos25 = ceil(kg / 25.0).toInt()
                val total25 = sacos25 * 25.0
                val sobra50 = 50.0 - kg
                val sobra25 = total25 - kg
                if (sobra50 <= sobra25) "1 sc 50kg"
                else if (sacos25 == 1) "1 sc 25kg" else "$sacos25 sc 25kg"
            }

            else -> {
                val sacos50 = (kg / 50.0).toInt()
                val resto = kg - sacos50 * 50.0
                val sacos25 = if (resto > 0) ceil(resto / 25.0).toInt() else 0

                val partes = mutableListOf<String>()
                if (sacos50 > 0)
                    partes += if (sacos50 == 1) "1 sc 50kg" else "$sacos50 sc 50kg"
                if (sacos25 > 0)
                    partes += if (sacos25 == 1) "1 sc 25kg" else "$sacos25 sc 25kg"

                if (partes.isEmpty()) {
                    val sacos = ceil(kg / 25.0).toInt()
                    if (sacos == 1) "1 sc 25kg" else "$sacos sc 25kg"
                } else {
                    partes.joinToString(" + ")
                }
            }
        }
    }
}