package com.luizeduardobrandao.obra.utils

import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView

class AccessibleHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    override fun performClick(): Boolean {
        super.performClick()
        // Sem ação extra — o importante é existir para acessibilidade
        return true
    }

    // 👉 Expor awakenScrollBars de forma pública
    fun showScrollBarsTemporarily() {
        super.awakenScrollBars() // chama o protegido da superclasse
    }
}