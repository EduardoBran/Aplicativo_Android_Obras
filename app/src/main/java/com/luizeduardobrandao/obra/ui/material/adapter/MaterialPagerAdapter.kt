package com.luizeduardobrandao.obra.ui.material.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.luizeduardobrandao.obra.ui.material.MaterialListFragment

class MaterialPagerAdapter(
    host: Fragment,
    private val obraId: String
) : FragmentStateAdapter(host) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        val status = when (position) {
            0    -> STATUS_ATIVO
            else -> STATUS_INATIVO
        }
        return MaterialListFragment.newInstance(obraId, status)
    }

    companion object {
        const val STATUS_ATIVO   = "Ativo"
        const val STATUS_INATIVO = "Inativo"
    }
}