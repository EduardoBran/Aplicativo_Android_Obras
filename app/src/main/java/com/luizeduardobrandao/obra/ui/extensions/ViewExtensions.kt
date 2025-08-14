@file:Suppress("unused")

package com.luizeduardobrandao.obra.ui.extensions

import androidx.fragment.app.Fragment
import com.luizeduardobrandao.obra.ui.snackbar.SnackbarFragment
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

/**
 * Abre o [SnackbarFragment] em *modal bottom-sheet* para exibir
 * erros, avisos ou mensagens de sucesso de forma padronizada.
 *
 * @param type      controla cor de fundo / ícone interno no fragment (ex.: "error", "success")
 * @param title     título centralizado
 * @param msg       texto abaixo do título
 * @param btnText   rótulo do botão de ação (opcional – deixa oculto se nulo ou vazio)
 */
fun Fragment.showSnackbarFragment(
    type: String,
    title: String,
    msg: String,
    btnText: String? = null,
    onAction: (() -> Unit)? = null,
    // NOVOS opcionais:
    btnNegativeText: String? = null,
    onNegative: (() -> Unit)? = null
) {
    val tag = SnackbarFragment.TAG
    val fm = parentFragmentManager
    if (fm.findFragmentByTag(tag) != null) return

    val sheet = SnackbarFragment.newInstance(type, title, msg, btnText, btnNegativeText)
    sheet.actionCallback = onAction
    sheet.secondaryActionCallback = onNegative // NOVO

    sheet.show(fm, tag)
}

/**
 * Esconde o teclado (caso esteja aberto) a partir de qualquer [View].
 */
fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
}
