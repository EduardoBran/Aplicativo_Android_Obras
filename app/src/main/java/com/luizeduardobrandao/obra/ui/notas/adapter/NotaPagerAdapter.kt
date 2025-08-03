package com.luizeduardobrandao.obra.ui.notas.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.luizeduardobrandao.obra.ui.notas.NotaListFragment

/**
 * Pager de duas páginas (“A Pagar” e “Pago”) dentro de NotasFragment.
 */
class NotaPagerAdapter(
    host: Fragment,
    private val obraId: String
) : FragmentStateAdapter(host) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        val status = if (position == 0) STATUS_A_PAGAR else STATUS_PAGO
        return NotaListFragment.newInstance(obraId, status)
    }

    companion object {
        const val STATUS_A_PAGAR = "A Pagar"
        const val STATUS_PAGO    = "Pago"
    }
}