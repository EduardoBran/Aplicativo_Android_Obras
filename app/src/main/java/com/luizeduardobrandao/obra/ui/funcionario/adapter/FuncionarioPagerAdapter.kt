package com.luizeduardobrandao.obra.ui.funcionario.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.luizeduardobrandao.obra.ui.funcionario.FuncionarioListFragment

/**
 * PagerAdapter que gera DUAS páginas:
 *  • posição 0 → lista de funcionários “Ativo”
 *  • posição 1 → lista de funcionários “Inativo”
 *
 * Cada página é um [FuncionarioListFragment] configurado por
 * argumento Safe-Args (obraId + status).
 *
 * @param hostFragment  fragment que contém o ViewPager2 (FuncionarioFragment)
 * @param obraId        id da obra ao qual os funcionários pertencem
 */

class FuncionarioPagerAdapter(
    hostFragment: Fragment,
    private val obraId: String
) : FragmentStateAdapter(hostFragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        val status = if (position == 0) "ativo" else "inativo"
        return FuncionarioListFragment.newInstance(obraId, status)
    }
}