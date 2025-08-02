package com.luizeduardobrandao.obra.ui.extensions

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import com.luizeduardobrandao.obra.utils.Constants
import java.text.NumberFormat
import java.util.Locale
import java.util.regex.Pattern

// ―――――――――――――――――――――――――――――――――――――――――――
// Pré-compila padrões para evitar recompilação em cada chamada
// ―――――――――――――――――――――――――――――――――――――――――――
/** Regex completo RFC 5322 para validar e-mail, definido em Constants */
private val EMAIL_PATTERN: Pattern =
    Pattern.compile(Constants.Validation.EMAIL_REGEX)

// Regex para remover tudo que não é dígito na formatação
private val NON_DIGITS_REGEX = Regex("\\D+")

// ―――――――――――――― 1. Validação de e-mail ――――――――――――――
/**
 * Retorna true se a string corresponder ao padrão completo RFC 5322.
 */
fun String.isValidEmail(): Boolean =
    EMAIL_PATTERN.matcher(this.trim()).matches()


// ―――――――――――――― 2. Validação de comprimento mínimo ――――――――――――――
/**
 * Retorna true se, após trim(), o tamanho da string for >= [min].
 * Ignora espaços em branco nas extremidades.
 */
fun String.hasMinLength(min: Int): Boolean =
    this.trim().length >= min


// ―――――――――――――― 3. Máscara monetária BR (R$ 1.234,56) ―――――――――――
/**
 * Adiciona um TextWatcher que formata dinamicamente o texto como moeda brasileira.
 *
 * @param locale define o formato de moeda (padrão: pt-BR definido em Constants).
 */
fun EditText.addMoneyMask(
    locale: Locale = Locale(
        Constants.Format.CURRENCY_LOCALE,
        Constants.Format.CURRENCY_COUNTRY
    )
) {
    val numberFormat = NumberFormat.getCurrencyInstance(locale)
    var currentText = ""

    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            val original = s?.toString().orEmpty()
            if (original == currentText) return  // sem alterações

            removeTextChangedListener(this)

            // extrai apenas dígitos e converte para valor em reais
            val digitsOnly = NON_DIGITS_REGEX.replace(original, "")
            val parsed = digitsOnly.toDoubleOrNull()?.div(100) ?: 0.0

            // formata e atualiza texto
            currentText = numberFormat.format(parsed)
            setText(currentText)

            // coloca cursor sempre ao final do texto formatado
            setSelection(currentText.length)

            addTextChangedListener(this)
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    })
}