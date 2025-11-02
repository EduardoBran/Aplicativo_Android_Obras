package com.luizeduardobrandao.obra.ui.calculo

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.databinding.FragmentCalcMaterialBinding

class CalcMaterialFragment : Fragment(R.layout.fragment_calc_material) {

    private var _binding: FragmentCalcMaterialBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCalcMaterialBinding.bind(view)

        with(binding.toolbar) {
            setNavigationOnClickListener { findNavController().navigateUp() }
            // título já vem do XML; se preferir setar aqui:
            // title = getString(R.string.calc_material_title)
        }

        binding.btnGoRevest.setOnClickListener {
            // Navega para CalcRevestimentoFragment
            // Garanta a action no nav_graph (ver item 3 abaixo)
            findNavController().navigate(R.id.action_calcMaterial_to_calcRevestimento)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}