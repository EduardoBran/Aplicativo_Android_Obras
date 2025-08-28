package com.luizeduardobrandao.obra.ui.funcionario

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
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentResumoFuncionarioBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.funcionario.adapter.FuncionarioAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class ResumoFuncionarioFragment : Fragment() {

    private var _binding: FragmentResumoFuncionarioBinding? = null
    private val binding get() = _binding!!

    private val args: ResumoFuncionarioFragmentArgs by navArgs()
    private val viewModel: FuncionarioViewModel by viewModels()

    private val adapter by lazy { FuncionarioAdapter(showActions = false) }

    // Estado de expansão da aba
    private var isResumoFuncsExpanded = false

    // Mapa agregado vindo do ViewModel: funcId -> total pago
    private var totaisPorFunc: Map<String, Double> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isResumoFuncsExpanded = savedInstanceState?.getBoolean(KEY_RESUMO_FUNCS_EXPANDED) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResumoFuncionarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    /* ───────────────────────── lifecycle ───────────────────────── */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarResumoFuncionario.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.rvFuncionarios.adapter = adapter

        // Conecta a aba (card no rodapé)
        setupExpandable(
            containerRoot = binding.cardAbaResumoFuncs,
            header = binding.headerAbaResumoFuncs,
            content = binding.contentAbaResumoFuncs,
            arrow = binding.ivArrowResumoFuncs,
            startExpanded = isResumoFuncsExpanded
        ) { expanded -> isResumoFuncsExpanded = expanded }

        observeViewModel()

        /*  disparo único do listener  */
        viewModel.loadFuncionarios()

        // Observa o agregado de pagamentos para preencher a aba de resumo
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeTotaisPorFuncionario().collect { mapa ->
                    totaisPorFunc = mapa
                    // Atualiza a caixinha do resumo quando o mapa mudar
                    updateResumoBox()
                }
            }
        }
    }

    /* ───────────────────────── observers ───────────────────────── */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> showLoading()
                        is UiState.Success -> renderList(ui.data)
                        is UiState.ErrorRes -> showError(ui.resId)
                        else -> Unit
                    }
                }
            }
        }
    }

    /** Atualiza a aba (Nome + Total pago) usando o mapa de totais por funcionário. */
    private fun updateResumoBox() = with(binding) {
        val list = adapter.currentList
        if (list.isEmpty()) {
            containerResumoFuncionarios.removeAllViews()
            tvResumoTotalGeralFuncs.text = formatMoneyBR(0.0)
            return@with
        }

        containerResumoFuncionarios.removeAllViews()

        var totalGeral = 0.0
        list.forEach { f ->
            val totalPago = totaisPorFunc[f.id] ?: 0.0
            totalGeral += totalPago

            // Reuso do layout simples para uma linha de texto
            val tv = layoutInflater.inflate(
                R.layout.item_tipo_valor, containerResumoFuncionarios, false
            ) as android.widget.TextView

            // "Nome: R$ X,XX" (negrito no nome)
            val valorFmt = formatMoneyBR(totalPago)
            val textoLinha = "${f.nome}: $valorFmt"

            val spannable = android.text.SpannableStringBuilder(textoLinha).apply {
                setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0,
                    f.nome.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            tv.text = spannable
            tv.textSize = 18f

            containerResumoFuncionarios.addView(tv)
        }

        tvResumoTotalGeralFuncs.text = formatMoneyBR(totalGeral)
    }

    /* ───────────────────────── UI helpers ──────────────────────── */
    private fun showLoading() = with(binding) {
        progressFuncList.visibility = View.VISIBLE
        rvFuncionarios.visibility = View.GONE
        cardAbaResumoFuncs.visibility = View.GONE
        tvEmptySum.visibility = View.GONE
    }

    private fun renderList(list: List<Funcionario>) = with(binding) {
        progressFuncList.visibility = View.GONE

        if (list.isEmpty()) {
            tvEmptySum.visibility = View.VISIBLE
            rvFuncionarios.visibility = View.GONE
            cardAbaResumoFuncs.visibility = View.GONE
            return@with
        }

        tvEmptySum.visibility = View.GONE
        rvFuncionarios.visibility = View.VISIBLE
        cardAbaResumoFuncs.visibility = View.VISIBLE

        // Lista principal (fora da aba)
        adapter.submitList(list)

        // Atualiza a aba com o mapa atual de totais por funcionário
        updateResumoBox()
    }

    private fun showError(resId: Int) {
        binding.progressFuncList.visibility = View.GONE
        showSnackbarFragment(
            Constants.SnackType.ERROR.name,
            getString(R.string.snack_error),
            getString(resId),
            getString(R.string.snack_button_ok)
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_RESUMO_FUNCS_EXPANDED, isResumoFuncsExpanded)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /*───────────────────────── Helper: Aba expansível ─────────────────────────*/
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

        // Estado inicial sem animação
        content.post { applyState(startExpanded, animate = false) }

        header.setOnClickListener {
            applyState(!content.isVisible, animate = true)
        }
    }

    private fun formatMoneyBR(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    companion object {
        private const val KEY_RESUMO_FUNCS_EXPANDED = "key_resumo_funcs_expanded"
    }
}