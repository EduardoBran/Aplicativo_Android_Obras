package com.luizeduardobrandao.obra.ui.cronograma

import com.luizeduardobrandao.obra.data.model.Etapa

interface EtapaActions {

    /** Usuário clicou no ícone ✏️ → abrir tela de edição. */
    fun onEdit(etapa: Etapa)

    /** Usuário clicou no ícone 🔍 → abrir tela de detalhes. */
    fun onDetail(etapa: Etapa)

    /** Usuário clicou no ícone 🗑️ → confirmar e excluir a etapa. */
    fun onDelete(etapa: Etapa)
}