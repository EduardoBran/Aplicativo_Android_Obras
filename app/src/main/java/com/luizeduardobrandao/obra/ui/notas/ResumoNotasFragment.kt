package com.luizeduardobrandao.obra.ui.notas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentResumoNotasBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.notas.adapter.NotaAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface

@AndroidEntryPoint
class ResumoNotasFragment : Fragment() {

    private var _binding: FragmentResumoNotasBinding? = null
    private val binding get() = _binding!!

    private val args: ResumoNotasFragmentArgs by navArgs()
    private val viewModel: NotasViewModel by viewModels()

    /* Adapter sem ações – só exibição */
    private val adapter by lazy { NotaAdapter(showActions = false) }

    // Estado das abas (preservado em rotação/processo)
    private var isTotalsExpanded = false
    private var isTiposExpanded  = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            isTotalsExpanded = it.getBoolean(KEY_TOT_EXPANDED, false)
            isTiposExpanded  = it.getBoolean(KEY_TIPOS_EXPANDED, false)
        }
    }

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

            // Liga as abas expansíveis
            setupExpandable(
                containerRoot = llTotaisContainer,
                header = headerAbaTotais,
                content = contentAbaTotais,
                arrow = ivArrowTotais,
                startExpanded = isTotalsExpanded
            ) { expanded -> isTotalsExpanded = expanded }

            setupExpandable(
                containerRoot = llTotaisContainer,
                header = headerAbaTipos,
                content = contentAbaTipos,
                arrow = ivArrowTipos,
                startExpanded = isTiposExpanded
            ) { expanded -> isTiposExpanded = expanded }
        }

        // Sem isso o estado fica eternamente em Loading
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

        tvResumoTotalAPagar.text = formatMoneyBR(totalAPagar)
        tvResumoTotalPago  .text = formatMoneyBR(totalPago)
        tvResumoTotalGeral .text = formatMoneyBR(totalGeral)

        // ---- Totais por tipo: (A Pagar) ----
        containerTiposValoresAPagar.removeAllViews()
        val porTipoAPagar = mutableMapOf<String, Double>()
        list.filter { it.status == "A Pagar" }.forEach { n ->
            n.tipos.forEach { t -> porTipoAPagar[t] = (porTipoAPagar[t] ?: 0.0) + n.valor }
        }
        // Se o TOTAL A RECEBER for zero, mostra a mensagem e esconde a lista
        if (totalAPagar == 0.0) {
            binding.tvTiposAPagarEmpty.isVisible = true
            binding.containerTiposValoresAPagar.isVisible = false
        } else {
            binding.tvTiposAPagarEmpty.isVisible = false
            binding.containerTiposValoresAPagar.isVisible = true

            porTipoAPagar.forEach { (tipo, v) ->
                val tv = layoutInflater.inflate(
                    R.layout.item_tipo_valor, containerTiposValoresAPagar, false
                ) as android.widget.TextView

                // Exemplo: "Elétrica: R$ 900,00"
                val label = "$tipo: ${formatMoneyBR(v)}"

                val styled = android.text.SpannableStringBuilder(label).apply {
                    setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        0,
                        tipo.length, // só o nome do tipo em negrito
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                tv.text = styled
                tv.textSize = 16f   // 16sp
                containerTiposValoresAPagar.addView(tv)
            }
        }

        // ---- Totais por tipo: (Pago) ----
        containerTiposValoresPago.removeAllViews()
        val porTipoPago = mutableMapOf<String, Double>()
        list.filter { it.status == "Pago" }.forEach { n ->
            n.tipos.forEach { t -> porTipoPago[t] = (porTipoPago[t] ?: 0.0) + n.valor }
        }
        if (totalPago == 0.0) {   // ✅ checa totalPago, não totalAPagar
            tvTiposPagoEmpty.isVisible = true
            containerTiposValoresPago.isVisible = false
        } else {
            tvTiposPagoEmpty.isVisible = false
            containerTiposValoresPago.isVisible = true

            porTipoPago.forEach { (tipo, v) ->
                val tv = layoutInflater.inflate(
                    R.layout.item_tipo_valor, containerTiposValoresPago, false
                ) as android.widget.TextView

                // Ex.: "Elétrica: R$ 100,00"
                val label = "$tipo: ${formatMoneyBR(v)}"

                val styled = SpannableStringBuilder(label).apply {
                    setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        tipo.length, // deixa só o nome do tipo em negrito
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                tv.text = styled
                tv.textSize = 16f   // 16sp
                containerTiposValoresPago.addView(tv)
            }
        }
    }

    private fun progress(show: Boolean) = with(binding) {
        progressNotasList.isVisible = show
        rvNotasResumo.isVisible     = !show
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_TOT_EXPANDED, isTotalsExpanded)
        outState.putBoolean(KEY_TIPOS_EXPANDED, isTiposExpanded)
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }

    /*───────────────────────── Helpers ─────────────────────────*/
    private fun setupExpandable(
        containerRoot: ViewGroup,
        header: View,
        content: View,
        arrow: ImageView,
        startExpanded: Boolean,
        onStateChange: (Boolean) -> Unit
    ) {
        fun applyState(expanded: Boolean, animate: Boolean) {
            if (animate) {
                TransitionManager.beginDelayedTransition(
                    containerRoot,
                    AutoTransition().apply { duration = 180 }
                )
            }
            content.isVisible = expanded
            arrow.animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()
            onStateChange(expanded)
        }

        // Estado inicial (sem animação para evitar "piscada" na abertura)
        content.post { applyState(startExpanded, animate = false) }

        header.setOnClickListener {
            val willExpand = !content.isVisible
            applyState(willExpand, animate = true)
        }
    }

    private fun formatMoneyBR(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    companion object {
        private const val KEY_TOT_EXPANDED   = "isTotalsExpanded"
        private const val KEY_TIPOS_EXPANDED = "isTiposExpanded"
    }
}