package com.luizeduardobrandao.obra.ui.cronograma

object CronStatus {
    const val PENDENTE = "Pendente"
    const val ANDAMENTO = "Andamento"
    const val CONCLUIDO = "Concluído"

    /** Deriva o status a partir de dias concluídos x total do intervalo. */
    @JvmStatic
    fun statusAuto(done: Int, total: Int): String = when {
        total <= 0 || done <= 0 -> PENDENTE
        done in 1 until total -> ANDAMENTO
        else -> CONCLUIDO
    }
}