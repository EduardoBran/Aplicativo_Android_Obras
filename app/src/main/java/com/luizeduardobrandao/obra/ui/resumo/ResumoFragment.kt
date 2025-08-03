package com.luizeduardobrandao.obra.ui.resumo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentResumoBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

/**
 * Fragmento que exibe **o panorama financeiro** da obra:
 *  • Totais de mão de obra, materiais e tipos de materiais
 *  • Saldos (inicial, ajustado, restante)
 *
 *  Padrão:
 *  • ViewBinding
 *  • navArgs para obraId
 *  • repeatOnLifecycle STARTED
 */

@AndroidEntryPoint
class ResumoFragment : Fragment() {

    /*──────────────  Boilerplate  ──────────────*/

    private var _binding: FragmentResumoBinding? = null
    private val binding get() = _binding!!

    private val args: ResumoFragmentArgs by navArgs()
    private val viewModel: ResumoViewModel by viewModels()

    /*──────────────  Ciclo de Vida  ──────────────*/

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResumoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarResumoObra.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /*──────────────  Observers  ──────────────*/
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> {
                            binding.progressResumo.visibility = View.VISIBLE
                            binding.containerResumo.visibility = View.GONE
                        }

                        is UiState.Success -> {
                            binding.progressResumo.visibility = View.GONE
                            binding.containerResumo.visibility = View.VISIBLE
                            bindResumo(ui.data)
                        }

                        is UiState.ErrorRes -> {
                            binding.progressResumo.visibility = View.GONE
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.snack_error),
                                getString(ui.resId),
                                getString(R.string.snack_button_ok)
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    /*──────────────  UI Binding  ──────────────*/
    private fun bindResumo(res: ResumoViewModel.ResumoData) = with(binding) {

        /*――― Funcionários ―――*/
        tvFuncCount.text = resources.getQuantityString(
            R.plurals.resumo_func_qtd, res.countFuncionarios, res.countFuncionarios
        )
        tvFuncDias.text = resources.getQuantityString(
            R.plurals.resumo_func_days, res.totalDias, res.totalDias
        )
        tvFuncTotal.text = getString(R.string.money_mask, res.totalMaoDeObra)

        /*――― Materiais ―――*/
        tvMatCount.text = resources.getQuantityString(
            R.plurals.resumo_mat_qtd, res.countNotas, res.countNotas
        )
        tvMatTotal.text = getString(R.string.money_mask, res.totalMateriais)

        // limpa e injeta linhas do mapa tipos→valor
        containerTiposMat.removeAllViews()
        if (res.totalPorTipo.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = getString(R.string.resumo_mat_no_types)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            }
            containerTiposMat.addView(tv)
        } else {
            res.totalPorTipo.forEach { (tipo, valor) ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(4.dp)
                }
                val tvNome = TextView(requireContext()).apply {
                    text = tipo.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tvVal = TextView(requireContext()).apply {
                    text = getString(R.string.money_mask, valor)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                row.addView(tvNome)
                row.addView(tvVal)
                containerTiposMat.addView(row)
            }
        }

        /*――― Saldos ―――*/
        tvSaldoInicialResumo.text = getString(R.string.money_mask, res.saldoInicial)
        tvSaldoAjustadoResumo.text = getString(R.string.money_mask, res.saldoAjustado)
        tvSaldoRestanteResumo.text = getString(R.string.money_mask, res.saldoRestante)
    }

    /*──────────────  Extensão dp  ──────────────*/
    private val Int.dp get() =
        (this * resources.displayMetrics.density).roundToInt()
}