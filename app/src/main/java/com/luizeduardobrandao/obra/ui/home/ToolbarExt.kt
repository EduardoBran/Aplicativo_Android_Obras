@file:Suppress("PackageDirectoryMismatch")

package com.luizeduardobrandao.obra.ui.home

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.doOnLayout
import androidx.core.view.get
import com.google.android.material.appbar.MaterialToolbar
import androidx.core.view.size

/* ---------- util para mostrar ícones no overflow ---------- */
@Suppress("RestrictedApi")
fun MaterialToolbar.forceShowOverflowMenuIcons() {
    (menu as? MenuBuilder)?.setOptionalIconsVisible(true)
}

/* ---------- 1) escala por bounds do drawable ---------- */
fun Toolbar.scaleNavigationIcon(factor: Float) {
    navigationIcon = navigationIcon?.scaled(factor)
}

fun Toolbar.scaleOverflowIcon(factor: Float) {
    overflowIcon = overflowIcon?.scaled(factor)
}

fun Toolbar.scaleMenuItemIcon(@IdRes itemId: Int, factor: Float) {
    val item = menu.findItem(itemId) ?: return
    item.icon = item.icon?.scaled(factor)
}

private fun Drawable.scaled(f: Float): Drawable {
    val baseW = intrinsicWidth.coerceAtLeast(1)
    val baseH = intrinsicHeight.coerceAtLeast(1)
    val w = (baseW * f).toInt().coerceAtLeast(1)
    val h = (baseH * f).toInt().coerceAtLeast(1)
    val copy = constantState?.newDrawable()?.mutate() ?: this.mutate()
    copy.setBounds(0, 0, w, h)
    return copy
}

/* ---------- 2) fallback: escala TODAS as action-views (exceto overflow) ---------- */
fun MaterialToolbar.scaleActionViewsOnLayout(
    @IdRes actionItemIds: IntArray,
    factor: Float
) {
    doOnLayout {
        var matched = 0

        // 2.1 Tenta por IDs
        actionItemIds.forEach { id ->
            findActionMenuChildByItemId(id)?.let { v ->
                v.scaleX = factor
                v.scaleY = factor
                v.pivotX = v.width / 2f
                v.pivotY = v.height / 2f
                matched++
            }
        }

        // 2.2 Overflow (três pontinhos)
        val overflowBtn = findOverflowButton()
        overflowBtn?.let { btn ->
            btn.scaleX = factor
            btn.scaleY = factor
            btn.pivotX = btn.width / 2f
            btn.pivotY = btn.height / 2f
        }

        // 2.3 Se nenhum item de ID foi encontrado, escale todos os botões (menos overflow)
        if (matched == 0) {
            val amv = findActionMenuView() ?: return@doOnLayout
            for (i in 0 until amv.childCount) {
                val child = amv.getChildAt(i)
                if (child === overflowBtn) continue
                child.scaleX = factor
                child.scaleY = factor
                child.pivotX = child.width / 2f
                child.pivotY = child.height / 2f
            }
        }
    }
}

/* ---------- helpers ---------- */
private fun MaterialToolbar.findActionMenuView(): ActionMenuView? {
    for (i in 0 until childCount) {
        val c = getChildAt(i)
        if (c is ActionMenuView) return c
    }
    return null
}

private fun MaterialToolbar.findActionMenuChildByItemId(@IdRes itemId: Int): View? {
    val amv = findActionMenuView() ?: return null
    for (i in 0 until amv.childCount) {
        val child = amv.getChildAt(i)
        if (child is ViewGroup) {
            if (child.id == itemId) return child
            val item = (0 until amv.menu.size).asSequence().map { amv.menu[it] }
                .firstOrNull { it.itemId == itemId } ?: continue
            if (child.contentDescription == item.title) return child
        }
    }
    return null
}

private fun MaterialToolbar.findOverflowButton(): View? {
    val amv = findActionMenuView() ?: return null
    for (i in 0 until amv.childCount) {
        val child = amv.getChildAt(i)
        val cd = child.contentDescription?.toString()?.lowercase() ?: ""
        if (cd.contains("mais opções") || cd.contains("more options") || cd.contains("mais opcoes")) {
            return child
        }
    }
    return null
}