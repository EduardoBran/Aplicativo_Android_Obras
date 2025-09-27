package com.luizeduardobrandao.obra.utils

import java.time.LocalDate
import java.time.ZoneId
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
    private val ZONE_UTC: ZoneId = ZoneId.of("UTC")

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
        diasConcluidosUtc: Set<String>?,
        inicioBr: String,
        fimBr: String
    ): Int {
        val range = diasPlanejados(inicioBr, fimBr)
        val total = range.size
        if (total == 0) return 0

        // Normaliza UTC → LocalDate e faz interseção com o range planejado
        val concluido: Int = diasConcluidosUtc
            ?.mapNotNull { utcStringToLocalDate(it) }
            ?.toSet()
            ?.intersect(range.toSet())
            ?.size
            ?: 0

        val pct = (100.0 * concluido / total.toDouble())
        return pct.roundToInt().coerceIn(0, 100)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Status automático
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Decide o status textual a partir de dias feitos (done) e total.
     * Retorna: "Pendente" | "Andamento" | "Concluído"
     */
    @JvmStatic
    fun statusAuto(done: Int, total: Int): String {
        if (total <= 0) return "Pendente"
        return when {
            done <= 0 -> "Pendente"
            done in 1 until total -> "Andamento"
            else -> "Concluído"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers para contagem de concluídos no intervalo (útil na ViewModel)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Conta quantos dias do set UTC caem dentro do intervalo [inicioBr até fimBr] (INCLUSIVO).
     */
    @JvmStatic
    fun contarConcluidosNoIntervalo(
        diasConcluidosUtc: Set<String>?,
        inicioBr: String,
        fimBr: String
    ): Int {
        val range = diasPlanejados(inicioBr, fimBr).toSet()
        if (range.isEmpty()) return 0
        return diasConcluidosUtc
            ?.mapNotNull { utcStringToLocalDate(it) }
            ?.count { it in range }
            ?: 0
    }

    // ─────────────────────────────────────────────────────────────────────────────
// Suporte a cabeçalho semanal e rótulos de célula
// ─────────────────────────────────────────────────────────────────────────────

    /** Conta dias inclusive entre [inicioBr até fimBr]. */
    @JvmStatic
    fun daysBetweenInclusiveBr(inicioBr: String, fimBr: String): Int =
        diasPlanejados(inicioBr, fimBr).size

    /** Dia do mês + mês (duas linhas): "21\n09" */
    @JvmStatic
    fun formatDayForHeader(date: LocalDate): String =
        String.format(Locale.ROOT, "%02d\n%02d", date.dayOfMonth, date.monthValue)

    // Útil em várias checagens (verifica se é domingo)
    @JvmStatic
    fun isSunday(date: LocalDate): Boolean = date.dayOfWeek.value == 7

    /** Letra curta da semana em pt-BR para header: D S T Q Q S S (Dom..Sáb). */
    @JvmStatic
    fun weekdayLetterPt(date: LocalDate): String = when (date.dayOfWeek.value) {
        // ISO: 1=Mon..7=Sun  → queremos Dom..Sáb
        7 -> "D" // Sunday
        1 -> "S" // Monday
        2 -> "T" // Tuesday
        3 -> "Q" // Wednesday
        4 -> "Q" // Thursday
        5 -> "S" // Friday
        else -> "S" // 6 = Saturday
    }

    /** Alinha um dia ao início da semana de acordo com pt-BR (WeekFields). */
    @JvmStatic
    fun alinharAoInicioDaSemana(date: LocalDate): LocalDate {
        val wf = java.time.temporal.WeekFields.of(LOCALE_PT_BR)
        val dow = wf.firstDayOfWeek // em pt-BR, geralmente SUNDAY
        var d = date
        while (d.dayOfWeek != dow) d = d.minusDays(1)
        return d
    }

    /** Lista todos os inícios de semana que cobrem [inicioBr até fimBr] (inclusive). */
    @JvmStatic
    fun listarIniciosDeSemana(inicioBr: String, fimBr: String): List<LocalDate> {
        val ini = brToLocalDate(inicioBr) ?: return emptyList()
        val fim = brToLocalDate(fimBr) ?: return emptyList()
        if (fim.isBefore(ini)) return emptyList()

        val first = alinharAoInicioDaSemana(ini)
        val out = ArrayList<LocalDate>()
        var d = first
        while (!d.isAfter(fim)) {
            out.add(d)
            d = d.plusWeeks(1)
        }
        return out
    }

// ─────────────────────────────────────────────────────────────────────────────
    /** Converte Set<String UTC yyyy-MM-dd> → Set<LocalDate>. */
    @JvmStatic
    fun utcSetToLocalDates(utc: Set<String>?): Set<LocalDate> =
        utc?.mapNotNull { utcStringToLocalDate(it) }?.toSet() ?: emptySet()

    /** Converte Set<LocalDate> → Set<String UTC yyyy-MM-dd>. */
    @JvmStatic
    fun localDatesToUtcSet(dates: Set<LocalDate>): Set<String> =
        dates.map { localDateToUtcString(it) }.toSet()

    /** Recorta os dias concluídos para caberem no intervalo BR informado (inclusive). */
    @JvmStatic
    fun clampDiasConcluidosAoIntervalo(
        diasConcluidosUtc: Set<String>?,
        inicioBr: String,
        fimBr: String
    ): Set<String> {
        val range = diasPlanejados(inicioBr, fimBr).toSet()
        if (range.isEmpty()) return emptySet()
        val clamped = utcSetToLocalDates(diasConcluidosUtc).intersect(range)
        return localDatesToUtcSet(clamped)
    }

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