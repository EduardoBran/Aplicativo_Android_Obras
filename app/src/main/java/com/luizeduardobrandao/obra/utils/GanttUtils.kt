package com.luizeduardobrandao.obra.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Utilitários de datas e cálculo de progresso para o Gantt.
 *
 * Convenções:
 *  - Datas exibidas na UI: BR "dd/MM/uuuu"
 *  - Datas persistidas nos "quadradinhos": UTC "uuuu-MM-dd"
 *  - Intervalos são SEMPRE inclusivos [início até fim]
 */
object GanttUtils {

    // Locale e zone padrão
    private val LOCALE_PT_BR: Locale = Locale("pt", "BR")

    // Formatadores (lenient=false)
    private val FMT_BR: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/uuuu", LOCALE_PT_BR)
            .withResolverStyle(ResolverStyle.STRICT)

    private val FMT_UTC: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT)

    // ─────────────────────────────────────────────────────────────────────────────
    // Conversões
    // ─────────────────────────────────────────────────────────────────────────────

    /** Converte string BR (dd/MM/uuuu) → LocalDate (sem timezone). */
    @JvmStatic
    fun brToLocalDate(br: String?): LocalDate? =
        try {
            if (br.isNullOrBlank()) null else LocalDate.parse(br.trim(), FMT_BR)
        } catch (_: Throwable) {
            null
        }

    @JvmStatic
    fun brToLocalDateOrNull(br: String?): LocalDate? = brToLocalDate(br)

    /** Converte LocalDate → string BR (dd/MM/uuuu). */
    @JvmStatic
    fun localDateToBr(date: LocalDate?): String =
        date?.format(FMT_BR).orEmpty()

    /** Converte string UTC (uuuu-MM-dd) → LocalDate. */
    @JvmStatic
    fun utcStringToLocalDate(utc: String?): LocalDate? =
        try {
            if (utc.isNullOrBlank()) null else LocalDate.parse(utc.trim(), FMT_UTC)
        } catch (_: Throwable) {
            null
        }

    /** Converte LocalDate → string UTC (uuuu-MM-dd). */
    @JvmStatic
    fun localDateToUtcString(date: LocalDate?): String =
        date?.format(FMT_UTC).orEmpty()

    // ─────────────────────────────────────────────────────────────────────────────
    // Range / dias planejados
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Lista de dias planejados (INCLUSIVO) entre início e fim em BR.
     * Retorna lista vazia quando inválido.
     */
    @JvmStatic
    fun diasPlanejados(inicioBr: String, fimBr: String): List<LocalDate> {
        val ini = brToLocalDate(inicioBr) ?: return emptyList()
        val fim = brToLocalDate(fimBr) ?: return emptyList()
        if (fim.isBefore(ini)) return emptyList()

        val out = ArrayList<LocalDate>()
        var d = ini
        while (!d.isAfter(fim)) {
            out.add(d)
            d = d.plusDays(1)
        }
        return out
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Cálculo de progresso (%)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Calcula o percentual concluído (0..100) a partir do set de dias concluídos (UTC)
     * e do intervalo planejado (BR).
     *
     * Regras:
     *  - Considera apenas dias dentro do intervalo planejado.
     *  - Divide por total de dias planejados (≥1); se zero, retorna 0.
     */
    @JvmStatic
    fun calcularProgresso(
        diasConcluidosUtc: Set<String>,
        inicioBr: String?,
        fimBr: String?
    ): Int {
        val ini = brToLocalDateOrNull(inicioBr) ?: return 0
        val fim = brToLocalDateOrNull(fimBr) ?: return 0
        if (fim.isBefore(ini)) return 0

        // Considere somente dias úteis planejados (exclui domingos)
        val planejados = daysBetween(ini, fim).filter { d -> !isSunday(d) }
        if (planejados.isEmpty()) return 0

        // Conta concluídos apenas entre os planejados
        val doneCount = planejados.count { d ->
            diasConcluidosUtc.contains(localDateToUtcString(d))
        }

        return ((doneCount.toDouble() / planejados.size) * 100.0).roundToInt()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers para contagem de concluídos no intervalo (útil na ViewModel)
    // ─────────────────────────────────────────────────────────────────────────────


    // ─────────────────────────────────────────────────────────────────────────────
    // Suporte a cabeçalho semanal e rótulos de célula
    // ─────────────────────────────────────────────────────────────────────────────

    /** Dia do mês + mês (duas linhas): "21\n09" */
    @JvmStatic
    fun formatDayForHeader(date: LocalDate): String =
        String.format(Locale.ROOT, "%02d\n%02d", date.dayOfMonth, date.monthValue)

    // Útil em várias checagens (verifica se é domingo)
    @JvmStatic
    fun isSunday(date: LocalDate): Boolean = date.dayOfWeek.value == 7

    /** Lista de dias (INCLUSIVO) entre duas datas LocalDate. */
    @JvmStatic
    fun daysBetween(start: LocalDate?, end: LocalDate?): List<LocalDate> {
        if (start == null || end == null) return emptyList()
        if (end.isBefore(start)) return emptyList()

        val out = ArrayList<LocalDate>()
        var d: LocalDate = start
        while (!d.isAfter(end)) {
            out.add(d)
            d = d.plusDays(1)
        }
        return out
    }
}