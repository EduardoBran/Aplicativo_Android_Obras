package com.luizeduardobrandao.obra.ui.material

import com.luizeduardobrandao.obra.data.model.Material

interface MaterialActions {

    /** Usuário clicou no ✏️ → abrir tela de edição. */
    fun onEdit(material: Material)

    /** Usuário clicou no 🔍 → abrir tela de detalhes. */
    fun onDetail(material: Material)

    /** Usuário clicou no 🗑️ → confirmar e excluir. */
    fun onDelete(material: Material)
}