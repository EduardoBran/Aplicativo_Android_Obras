package com.luizeduardobrandao.obra.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.luizeduardobrandao.obra.R

data class HomeAction(
    val id: Id,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    @StringRes val subtitle: Int? = null
) {
    enum class Id {
        CRONOGRAMA, FOTOS, CALC_MATERIAL, IA, NOTAS, MATERIAIS, FUNCIONARIOS, RESUMO
    }

    companion object {
        fun defaultList(): List<HomeAction> = listOf(
            HomeAction(
                Id.FUNCIONARIOS,
                R.string.func_title,
                R.drawable.ic_employee,
                R.string.home_sub_func
            ),
            HomeAction(
                Id.NOTAS,
                R.string.home_btn_notas,
                R.drawable.ic_edit,
                R.string.home_sub_notas
            ),
            HomeAction(
                Id.CRONOGRAMA,
                R.string.cron_title,
                R.drawable.ic_schedule,
                R.string.home_sub_cron
            ),
            HomeAction(
                Id.MATERIAIS,
                R.string.material_list_title,
                R.drawable.ic_build,
                R.string.home_sub_materiais
            ),
            HomeAction(
                Id.FOTOS,
                R.string.imagens_title,
                R.drawable.ic_camera,
                R.string.home_sub_fotos
            ),
            HomeAction(
                Id.CALC_MATERIAL,
                R.string.calc_material_action_title,
                R.drawable.ic_calculator,
                R.string.home_sub_calc_material
            ),
            HomeAction(Id.IA, R.string.ia_title, R.drawable.ic_robo, R.string.home_sub_ia),
            HomeAction(
                Id.RESUMO,
                R.string.resumo_title,
                R.drawable.ic_summary2,
                R.string.home_sub_resumo
            )
        )
    }
}