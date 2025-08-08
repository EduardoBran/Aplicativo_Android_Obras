package com.luizeduardobrandao.obra.ui.notas

import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentResumoNotasBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.notas.adapter.NotaAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResumoNotasFragment : Fragment() {

    private var _binding: FragmentResumoNotasBinding? = null
    private val binding get() = _binding!!

    private val args: ResumoNotasFragmentArgs by navArgs()
    private val viewModel: NotasViewModel by viewModels()

    /* Adapter sem ações – só exibição */
    private val adapter by lazy { NotaAdapter(showActions = false) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentResumoNotasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            progressNotasList.isVisible = true
            rvNotasResumo.isVisible = false
            tvEmptyNotas.isVisible = false

            toolbarResumoNotas.setNavigationOnClickListener { findNavController().navigateUp() }
            rvNotasResumo.adapter = adapter
        }

        // 🔽 sem isso o estado fica eternamente em Loading
        viewModel.loadNotas()

        observeState()
    }

    /*───────────────────────── Collector ─────────────────────────*/
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> progress(true)
                        is UiState.Success -> showNotas(ui.data)
                        is UiState.ErrorRes -> {
                            progress(false)
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

    private fun showNotas(list: List<Nota>) = with(binding) {
        progress(false)

        if (list.isEmpty()) {
            rvNotasResumo.isVisible = false
            tvEmptyNotas.isVisible = true
            llTotaisContainer.isVisible = false
            return@with
        }

        tvEmptyNotas.isVisible = false
        llTotaisContainer.isVisible = true
        rvNotasResumo.isVisible = true

        // Lista completa nas cards
        adapter.submitList(list)

        // ---- Totais (valores) ----
        val totalAPagar = list.filter { it.status == "A Pagar" }.sumOf { it.valor }
        val totalPago   = list.filter { it.status == "Pago" }.sumOf { it.valor }
        val totalGeral  = totalAPagar + totalPago

        tvResumoTotalAPagar.text = getString(R.string.money_mask, totalAPagar)
        tvResumoTotalPago  .text = getString(R.string.money_mask, totalPago)
        tvResumoTotalGeral .text = getString(R.string.money_mask, totalGeral)

        // ---- Totais por tipo: (A Pagar) ----
        containerTiposValoresAPagar.removeAllViews()
        val porTipoAPagar = mutableMapOf<String, Double>()
        list.filter { it.status == "A Pagar" }.forEach { n ->
            n.tipos.forEach { t -> porTipoAPagar[t] = (porTipoAPagar[t] ?: 0.0) + n.valor }
        }
        porTipoAPagar.forEach { (tipo, v) ->
            val tv = layoutInflater.inflate(
                R.layout.item_tipo_valor, containerTiposValoresAPagar, false
            ) as android.widget.TextView
            tv.text = getString(R.string.tipo_valor_mask, tipo, v)
            containerTiposValoresAPagar.addView(tv)
        }

        // ---- Totais por tipo: (Pago) ----
        containerTiposValoresPago.removeAllViews()
        val porTipoPago = mutableMapOf<String, Double>()
        list.filter { it.status == "Pago" }.forEach { n ->
            n.tipos.forEach { t -> porTipoPago[t] = (porTipoPago[t] ?: 0.0) + n.valor }
        }
        porTipoPago.forEach { (tipo, v) ->
            val tv = layoutInflater.inflate(
                R.layout.item_tipo_valor, containerTiposValoresPago, false
            ) as android.widget.TextView
            tv.text = getString(R.string.tipo_valor_mask, tipo, v)
            containerTiposValoresPago.addView(tv)
        }
    }

    private fun progress(show: Boolean) = with(binding) {
        progressNotasList.isVisible = show
        rvNotasResumo.isVisible     = !show
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}