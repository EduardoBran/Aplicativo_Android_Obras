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

    /**
     * Cria linha de cabeçalho da tabela
     */
    @SuppressLint("InflateParams")
    fun makeHeaderRow(): View {
        val row = layoutInflater.inflate(R.layout.item_material_row, null, false)

        // Background do header
        row.setBackgroundColor(ContextCompat.getColor(context, R.color.tableHeaderBg))

        // Padding responsivo
        row.setPadding(
            responsiveHelper.headerPaddingHorizontal,
            responsiveHelper.headerPaddingVertical,
            responsiveHelper.headerPaddingHorizontal,
            responsiveHelper.headerPaddingVertical
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

        // ✅ Atualizar guidelines
        updateRowGuidelines(row)

        return row
    }

    /**
     * Cria linha de dados da tabela
     */
    @SuppressLint("InflateParams")
    fun makeDataRow(item: CalcRevestimentoViewModel.MaterialItem): View {
        val row = layoutInflater.inflate(R.layout.item_material_row, null, false)

        // Padding e altura mínima responsivos
        row.setPadding(
            responsiveHelper.rowPaddingHorizontal,
            responsiveHelper.rowPaddingVertical,
            responsiveHelper.rowPaddingHorizontal,
            responsiveHelper.rowPaddingVertical
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

    /**
     * Atualiza guidelines de uma linha específica
     */
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

    /**
     * Constrói célula "Comprar" com extração inteligente de embalagens
     */
    fun buildComprarCell(item: CalcRevestimentoViewModel.MaterialItem): String {
        val alvo = max(0.0, item.qtd)
        if (alvo <= 0.0) return "0"

        val nome = item.item
        val unid = item.unid
        val obs = item.observacao?.lowercase(Locale.getDefault())

        // Rodapé MESMA PEÇA → mostrar "Incluso" (m² extra já está somado no piso)
        if (nome.equals("Rodapé", ignoreCase = true) &&
            obs?.contains("mesma peça") == true
        ) {
            return "Incluso"
        }

        // Rodapé PEÇA PRONTA → usa "Peça pronta • [q] peças." para montar "[q] pc"
        if (nome.equals("Rodapé", ignoreCase = true) &&
            obs?.contains("peça pronta") == true
        ) {
            val match = Regex("""(\d+)\s+peças""").find(obs)
            val qtdPc = match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1)
            if (qtdPc != null) {
                return "$qtdPc pc"
            }
        }

        // ---------------- ESPECIAIS / EXCEÇÕES ----------------

        // Pedra em m² → mostrar só metragem
        if ((unid.equals("m²", true) || unid.equals("m2", true)) &&
            nome.contains("pedra", ignoreCase = true)
        ) {
            return NumberFormatter.format(alvo)
        }

        // Areia geral em m³ → sacos de 20 kg (densidade ~1400 kg/m³)
        if (nome.equals("Areia", ignoreCase = true) && unid.equals("m³", true)) {
            val areiaKg = alvo * 1400.0
            val sacos20 = ceil(areiaKg / 20.0).toInt().coerceAtLeast(1)
            return if (sacos20 == 1) "1 sc 20kg" else "$sacos20 sc 20kg"
        }

        // Argamassa colante → sacos 20kg
        if (nome.startsWith("Argamassa", ignoreCase = true) &&
            unid.equals("kg", true)
        ) {
            val n20 = ceil(alvo / 20.0).toInt()
            return if (n20 == 1) "1 sc 20kg" else "$n20 sc 20kg"
        }

        // Piso intertravado: areia assentamento m³ → sacos 20kg
        if (nome.equals("Areia de assentamento", ignoreCase = true) &&
            unid.equals("m³", true)
        ) {
            val areiaKg = alvo * 1400.0
            val sacos20 = ceil(areiaKg / 20.0).toInt().coerceAtLeast(1)
            return if (sacos20 == 1) "1 sc 20kg" else "$sacos20 sc 20kg"
        }

        // Piso intertravado: Manta Asfáltica → rolos 10 m²
        if (nome.contains("Manta Asfáltica", ignoreCase = true) &&
            (unid.equals("m²", true) || unid.equals("m2", true))
        ) {
            val rolos = ceil(alvo / 10.0).toInt().coerceAtLeast(1)
            return if (rolos == 1) "1 rolo 10m²" else "$rolos rolos 10m²"
        }

        // Piso intertravado: Manta Geotêxtil → rolos 100 m²
        if (nome.contains("Manta Geotêxtil", ignoreCase = true) &&
            (unid.equals("m²", true) || unid.equals("m2", true))
        ) {
            val rolos = ceil(alvo / 100.0).toInt().coerceAtLeast(1)
            return if (rolos == 1) "1 rolo de 100m²" else "$rolos rolos de 100m²"
        }

        // Piso intertravado: Aditivo impermeabilizante (Sika 1) → frasco/galão/balde
        if (nome.contains("Aditivo impermeabilizante", ignoreCase = true) &&
            unid.equals("L", true)
        ) {
            return buildAditivoSikaComprar(alvo)
        }

        // Cimento para estabilização / genérico → sacos 25kg/50kg
        if (nome.equals("Cimento", ignoreCase = true) &&
            unid.equals("kg", ignoreCase = true)
        ) {
            return buildCimentoComprar(alvo)
        }

        // ---------------- REJUNTE ----------------

        // Rejunte epóxi → 1kg, 2kg, 5kg
        if (nome.contains("Rejunte epóxi", ignoreCase = true) &&
            unid.equals("kg", true)
        ) {
            val pack = bestPackCombo(alvo, listOf(5.0, 2.0, 1.0))
            return pack.toCompraString(unidade = "kg", label = "pct")
        }

        // Rejunte cimentício → 5kg
        if (nome.contains("Rejunte comum", ignoreCase = true) &&
            unid.equals("kg", true)
        ) {
            val n5 = ceil(alvo / 5.0).toInt()
            return if (n5 <= 0) "0" else if (n5 == 1) "1 pct 5kg" else "$n5 pct 5kg"
        }

        // ---------------- IMPERMEABILIZANTES (EXCETO INTERTRAVADO) ----------------

        // 1) Membrana Acrílica → 18L, 3,6L, 1L
        if (nome.equals("Impermeabilizante Membrana Acrílica", ignoreCase = true) &&
            unid.equals("L", true)
        ) {
            val pack = bestPackCombo(alvo, listOf(18.0, 3.6, 1.0))
            return pack.toCompraString(unidade = "L", label = null)
        }

        // 2) Argamassa Polimérica Flexível → 18kg, 4kg
        if (nome.startsWith(
                "Impermeabilizante Argamassa Polimérica Flexível",
                ignoreCase = true
            ) && unid.equals("kg", true)
        ) {
            val pack = bestPackCombo(alvo, listOf(18.0, 4.0))
            return pack.toCompraString(unidade = "kg", label = null)
        }

        // 3) Argamassa Polimérica Bicomponente → 18kg, 4kg
        if (nome.startsWith(
                "Impermeabilizante Argamassa Polimérica Bicomponente",
                ignoreCase = true
            ) && unid.equals("kg", true)
        ) {
            val pack = bestPackCombo(alvo, listOf(18.0, 4.0))
            return pack.toCompraString(unidade = "kg", label = null)
        }

        // PASTILHA - Usa observação "Mantas por m²: X • Y peças." para montar "Nmn ou Ypc"
        if (nome.contains("Pastilha", ignoreCase = true) &&
            (unid.equals("m²", true) || unid.equals("m2", true)) &&
            obs != null
        ) {
            // Extrai "Mantas por m²: X"
            val mantasPorM2Match = Regex(
                pattern = """mantas por m²:\s*([\d.,]+)""",
                option = RegexOption.IGNORE_CASE
            ).find(obs)

            val mantasPorM2 = mantasPorM2Match
                ?.groupValues?.get(1)
                ?.replace(",", ".")
                ?.toDoubleOrNull()

            // Extrai "[totalPecas] peças"
            val totalPecas = Regex("""(\d+)\s+peças""")
                .find(obs)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()

            // item.qtd = área total em m² já com sobra → calcula total de mantas
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
            // Se por algum motivo não conseguir extrair, deixa cair no fallback abaixo
        }

        // ---------------- HEURÍSTICAS BASEADAS NA OBS ----------------

        if (obs?.contains("informativo") == true) return "—"

        // Mármore/Granito com "N peças" na observação → mostrar N pc
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

            // "10 sacos de 20 kg", "3 baldes de 18L" etc.
            Regex("""(\d+)\s*(?:x|sacos?\s+de|pacotes?\s+de|baldes?\s+de)\s*([\d.,]+)\s*(kg|l|un|unid|unidades)""")
                .find(obs)?.let {
                    val (qtd, tam, u) = it.destructured
                    return "${qtd}× ${tam}${u.uppercase()}"
                }
        }

        // ---------------- FALLBACK ----------------

        return NumberFormatter.format(alvo)
    }

    // ================= HELPERS INTERNOS =================

    private fun bestPackCombo(target: Double, sizes: List<Double>): Map<Double, Int> {
        val sorted = sizes.sortedDescending()
        val smallest = sorted.last()
        var best: Map<Double, Int> = emptyMap()
        var bestOver = Double.MAX_VALUE
        var bestCount = Int.MAX_VALUE

        val limits = sorted.associateWith { ceil(target / it).toInt() + 3 }

        // Detecta caso específico da Membrana Acrílica: 18L, 3,6L, 1L
        val isMembrana =
            sizes.size == 3 &&
                    sizes.contains(18.0) &&
                    sizes.contains(3.6) &&
                    sizes.contains(1.0)

        fun search(idx: Int, acc: Map<Double, Int>) {
            if (idx == sorted.size) {
                val total = acc.entries.sumOf { it.key * it.value }
                if (total < target || total <= 0.0) return

                val over = total - target
                val count = acc.values.sum()

                if (!isMembrana) {
                    // Comportamento original para todos os outros casos
                    if (over < bestOver || (over == bestOver && count < bestCount)) {
                        best = acc
                        bestOver = over
                        bestCount = count
                    }
                } else {
                    // Membrana Acrílica:
                    // - ainda tenta pouca sobra
                    // - mas aceita até +1L de sobra se reduzir o nº de embalagens
                    val smallCount = acc[smallest] ?: 0
                    val bestSmallCount = best[smallest] ?: Int.MAX_VALUE

                    if (
                        over < bestOver || // melhor sobra
                        (over <= bestOver + 1.0 && count < bestCount) || // permite um pouco mais de sobra com menos embalagens
                        (over == bestOver && count < bestCount && smallCount < bestSmallCount) // em empate, menos frascos de 1L
                    ) {
                        best = acc
                        bestOver = over
                        bestCount = count
                    }
                }
                return
            }

            val size = sorted[idx]
            val maxN = limits[size] ?: 5
            for (n in 0..maxN) {
                val next = if (n == 0) acc else acc + (size to n)
                val partial = next.entries.sumOf { it.key * it.value }

                // Poda:
                // para Membrana permitimos pequena folga extra (+1L) na busca
                val extraTolerancia = if (isMembrana) 1.0 else 0.0
                if (partial > target + bestOver + extraTolerancia &&
                    bestOver < Double.MAX_VALUE
                ) continue

                search(idx + 1, next)
            }
        }

        search(0, emptyMap())

        if (best.isEmpty()) {
            val n = ceil(target / smallest).toInt()
            return mapOf(smallest to n)
        }

        return best
    }

    private fun Map<Double, Int>.toCompraString(
        unidade: String,
        label: String? = null
    ): String {
        return entries
            .filter { it.value > 0 }
            .sortedByDescending { it.key }
            .joinToString(" + ") { (size, count) ->
                val sizeStr = if (size % 1.0 == 0.0)
                    size.toInt().toString()
                else
                    size.toString().replace('.', ',')
                if (label != null) {
                    if (count == 1) "1 $label ${sizeStr}$unidade"
                    else "$count $label ${sizeStr}$unidade"
                } else {
                    val countStr = if (count == 1) "1x" else "${count}x"
                    "$countStr ${sizeStr}$unidade"
                }
            }
    }

    /**
     * Distribui o volume de aditivo (L) em frascos 1L, galões 3,6L e baldes 18L
     * com o menor excedente possível e poucas embalagens.
     */
    private fun buildAditivoSikaComprar(litros: Double): String {
        if (litros <= 0.0) return "0"
        val sizes = listOf(18.0, 3.6, 1.0)
        var best: Map<Double, Int> = emptyMap()
        var bestOver = Double.MAX_VALUE
        var bestCount = Int.MAX_VALUE

        fun search(idx: Int, acc: Map<Double, Int>) {
            if (idx == sizes.size) {
                val total = acc.entries.sumOf { it.key * it.value }
                if (total <= 0.0) return
                val over = (total - litros).coerceAtLeast(0.0)
                val count = acc.values.sum()
                if (total >= litros &&
                    (count < bestCount || (count == bestCount && over < bestOver))
                ) {
                    best = acc
                    bestCount = count
                    bestOver = over
                }
                return
            }
            val size = sizes[idx]
            val maxN = ceil(litros / size).toInt() + 3
            for (n in 0..maxN) {
                val next = if (n == 0) acc else acc + (size to n)
                val partial = next.entries.sumOf { it.key * it.value }
                if (partial > litros + bestOver && bestOver < Double.MAX_VALUE) continue
                search(idx + 1, next)
            }
        }

        search(0, emptyMap())

        if (best.isEmpty()) {
            val n = ceil(litros).toInt()
            return if (n == 1) "1 frasco 1L" else "$n frascos 1L"
        }

        val parts = best.entries
            .sortedByDescending { it.key }
            .filter { it.value > 0 }
            .map { (size, count) ->
                when (size) {
                    18.0 -> if (count == 1) "1 balde 18L" else "$count baldes 18L"
                    3.6 -> if (count == 1) "1 galão 3,6L" else "$count galões 3,6L"
                    1.0 -> if (count == 1) "1 frasco 1L" else "$count frascos 1L"
                    else -> ""
                }
            }.filter { it.isNotBlank() }

        return if (parts.isEmpty()) "0" else parts.joinToString(" + ")
    }

    /**
     * Empacota o cimento (kg) em sacos de 25 kg e/ou 50 kg
     */
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