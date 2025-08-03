package com.luizeduardobrandao.obra.utils

import androidx.annotation.ColorRes
import com.luizeduardobrandao.obra.R

/**
 * Conjunto de constantes reutilizadas em t0do o projeto.
 * Coloque aqui somente valores realmente “globais” — evita *magic numbers*
 * ou *hard-coded strings* espalhados pelo código.
 */
object Constants {

    // ▶ Firebase
    object Firebase {
        // Nó raiz para obras => definido também no FirebaseModule.
        const val PATH_OBRAS = "obras"

        // Sub-nós dentro de cada obra
        const val CHILD_FUNCIONARIOS = "funcionarios"
        const val CHILD_NOTAS = "notas"
        const val CHILD_CRONOGRAMA = "cronograma"
    }

    // ▶ Validações / UI
    object Validation {

        // Regex simplificada RFC 5322 para e-mail.
        const val EMAIL_REGEX =
            "(?:[a-zA-Z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\\." +
                    "[a-zA-Z0-9!#\$%&'*+/=?^_`{|}~-]+)*|\"" +
                    "(?:[\u0020-\u001F\u007F]|\\\\[\\u0020-\\u007F])*\")@" +
                    "(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+" +
                    "[a-zA-Z]{2,}"

        const val MIN_PASSWORD = 6
        const val MIN_NAME = 3
        const val MAX_CLIENT_NAME = 15
        const val MIN_SALDO = 0.0
    }

    // ▶ Formatos
    object Format {
        // Padrão “dd/MM/yyyy” – usado nos DatePickerDialogs.
        const val DATE_PATTERN_BR = "dd/MM/yyyy"

        // Separador decimal padrão (para masks de moeda). */
        const val CURRENCY_LOCALE = "pt"
        const val CURRENCY_COUNTRY = "BR"
    }

    // ▶ Snackbar types (cores + ícones)
    enum class SnackType(
        @ColorRes val bgColor: Int,
        @ColorRes val textColor: Int
    ) {
        SUCCESS(
            R.color.md_theme_light_primaryContainer,
            R.color.md_theme_light_onPrimaryContainer
        ),

        ERROR(
            R.color.md_theme_light_error,
            R.color.white
        ),

        WARNING(
            R.color.md_theme_light_secondaryContainer,
            R.color.md_theme_light_onSecondaryContainer
        )
    }
}