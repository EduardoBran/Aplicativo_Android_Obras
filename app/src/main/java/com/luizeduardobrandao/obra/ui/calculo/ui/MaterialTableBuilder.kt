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

/**
 * Construtor de tabelas de materiais para exibição no resultado
 */
class MaterialTableBuilder(
    private val context: Context,
    private val layoutInflater: LayoutInflater
) {

    /**
     * Cria linha de cabeçalho da tabela
     */
    @SuppressLint("InflateParams")
    fun makeHeaderRow(): View {
        val row = layoutInflater.inflate(R.layout.item_material_row, null, false)

        row.setBackgroundColor(ContextCompat.getColor(context, R.color.tableHeaderBg))
        val headerTextColor = ContextCompat.getColor(context, R.color.tableHeaderText)

        row.findViewById<TextView>(R.id.tvItem).apply {
            text = context.getString(R.string.col_item)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(headerTextColor)
        }

        row.findViewById<TextView>(R.id.tvUnid).apply {
            text = context.getString(R.string.col_unid)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(headerTextColor)
        }

        row.findViewById<TextView>(R.id.tvQtdUsada).apply {
            text = context.getString(R.string.col_usado)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(headerTextColor)
        }

        row.findViewById<TextView>(R.id.tvQtdComprar).apply {
            text = context.getString(R.string.col_comprar)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(headerTextColor)
        }

        row.findViewById<TextView>(R.id.tvObservacao).visibility = View.GONE

        return row
    }

    /**
     * Cria linha de dados da tabela
     */
    @SuppressLint("InflateParams")
    fun makeDataRow(item: CalcRevestimentoViewModel.MaterialItem): View {
        val row = layoutInflater.inflate(R.layout.item_material_row, null, false)

        row.findViewById<TextView>(R.id.tvItem).text = item.item
        row.findViewById<TextView>(R.id.tvUnid).text = item.unid
        row.findViewById<TextView>(R.id.tvQtdUsada).text = NumberFormatter.format(item.qtd)
        row.findViewById<TextView>(R.id.tvQtdComprar).text = buildComprarCell(item)

        val tvObs = row.findViewById<TextView>(R.id.tvObservacao)
        if (!item.observacao.isNullOrBlank()) {
            tvObs.text = item.observacao
            tvObs.visibility = View.VISIBLE
        } else {
            tvObs.visibility = View.GONE
        }

        return row
    }

    /**
     * Constrói célula "Comprar" com extração inteligente de embalagens
     */
    fun buildComprarCell(item: CalcRevestimentoViewModel.MaterialItem): String {
        val obs = item.observacao?.lowercase(Locale.getDefault()) ?: return "—"

        // Itens inclusos ou informativos
        if (obs.contains("incluso")) return "Incluso"
        if (obs.contains("informativo")) return "—"

        // 1. Peças com caixas: "72 peças (5 caixas)"
        Regex("""(\d+)\s+peças?\s*\((\d+)\s+caixas?\)""").find(obs)?.let {
            return "${it.groupValues[2]} cx"
        }

        // 2. Peças sem caixas: "72 peças"
        Regex("""(\d+)\s+peças?""").find(obs)?.let {
            return "${it.groupValues[1]} pc"
        }

        // 3. Múltiplas embalagens: "2x 50 kg + 1x 25 kg"
        if (obs.contains("+")) {
            val partes = mutableListOf<String>()
            Regex("""(\d+)\s*x\s*([\d.,]+)\s*(kg|l|un|unid|unidades)""").findAll(obs).forEach {
                val (qtd, tam, unid) = it.destructured
                partes.add("${qtd}×${tam}${unid.uppercase()}")
            }
            if (partes.isNotEmpty()) return partes.joinToString(" + ")
        }

        // 4. Formato padrão: "10x 20 kg" | "10 sacos de 20 kg"
        Regex("""(\d+)\s*(?:x|sacos?\s+de|pacotes?\s+de|baldes?\s+de)\s*([\d.,]+)\s*(kg|l|un|unid|unidades)""")
            .find(obs)?.let {
                val (qtd, tam, unid) = it.destructured
                return "${qtd}× ${tam}${unid.uppercase()}"
            }

        // 5. Fallback
        return NumberFormatter.format(item.qtd)
    }
}