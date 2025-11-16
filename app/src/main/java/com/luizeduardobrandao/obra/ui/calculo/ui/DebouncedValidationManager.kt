package com.luizeduardobrandao.obra.ui.calculo.ui

import com.google.android.material.textfield.TextInputEditText

/**
 * Gerencia validações "live" com atraso (debounce) para evitar
 * erro piscando enquanto o usuário está digitando.
 *
 * Ele NÃO conhece regras de negócio, apenas agenda um bloco de código
 * para rodar depois de "delayMs"]" ms sem novas digitações naquele campo.
 */
class DebouncedValidationManager(
    private val delayMs: Long = 1000L
) {

    // Mapa campo → Runnable pendente
    private val pending = mutableMapOf<TextInputEditText, Runnable>()

    /**
     * Agenda [block] para ser executado depois de [delayMs] milissegundos,
     * desde que não haja nova digitação nesse mesmo campo.
     */
    fun schedule(et: TextInputEditText, block: () -> Unit) {
        // Cancela runnable anterior (se existir)
        pending[et]?.let { previous ->
            et.removeCallbacks(previous)
        }

        val runnable = Runnable {
            // Remove antes de executar para não ficar lixo no mapa
            pending.remove(et)
            block()
        }

        pending[et] = runnable
        et.postDelayed(runnable, delayMs)
    }

    /**
     * Cancela a validação adiada para este campo (se houver).
     */
    fun cancel(et: TextInputEditText) {
        pending.remove(et)?.let { runnable ->
            et.removeCallbacks(runnable)
        }
    }

    /**
     * Cancela TODAS as validações pendentes.
     * Deve ser chamado em onDestroyView() do Fragment.
     */
    fun cancelAll() {
        pending.forEach { (et, runnable) ->
            et.removeCallbacks(runnable)
        }
        pending.clear()
    }
}