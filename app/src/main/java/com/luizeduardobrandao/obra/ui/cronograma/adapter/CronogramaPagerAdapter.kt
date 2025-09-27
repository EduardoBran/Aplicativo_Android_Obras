package com.luizeduardobrandao.obra.ui.cronograma.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.luizeduardobrandao.obra.ui.cronograma.CronogramaListFragment
import com.luizeduardobrandao.obra.ui.cronograma.CronStatus

/**
 * Pager com 3 abas (Pendente, Andamento, Concluído).
 *
 * @param host   Fragment hospedeiro (CronogramaFragment)
 * @param obraId Id da obra recebido por Safe-Args
 */

class CronogramaPagerAdapter(
    host: Fragment,
    private val obraId: String
) : FragmentStateAdapter(host) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        val status = when (position) {
            0 -> CronStatus.PENDENTE
            1 -> CronStatus.ANDAMENTO
            else -> CronStatus.CONCLUIDO
        }
        return CronogramaListFragment.newInstance(obraId, status)
    }

    companion object {
        const val STATUS_PENDENTE = CronStatus.PENDENTE
        const val STATUS_ANDAMENTO = CronStatus.ANDAMENTO
        const val STATUS_CONCLUIDO = CronStatus.CONCLUIDO
    }
}