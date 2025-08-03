package com.luizeduardobrandao.obra.ui.notas

import com.luizeduardobrandao.obra.data.model.Nota

interface NotaActions {
    fun onEdit(nota: Nota)
    fun onDetail(nota: Nota)
    fun onDelete(nota: Nota)
}