package com.luizeduardobrandao.obra.ui.funcionario

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.Pagamento
import androidx.recyclerview.widget.LinearLayoutManager
import com.luizeduardobrandao.obra.ui.funcionario.adapter.PagamentoAdapter
import com.luizeduardobrandao.obra.databinding.FragmentDetalheFuncionarioBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.view.isVisible
import android.widget.Toast
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class DetalheFuncionarioFragment : Fragment() {

    private var _binding: FragmentDetalheFuncionarioBinding? = null
    private val binding get() = _binding!!

    private val args: DetalheFuncionarioFragmentArgs by navArgs()
    private val viewModel: FuncionarioViewModel by viewModels()

    private lateinit var pagamentoAdapter: PagamentoAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentDetalheFuncionarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarDetalheFuncionario.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // ── RecyclerView do histórico (sem exclusão)
        pagamentoAdapter = PagamentoAdapter(
            showDelete = false,
            showEdit = false,           // <- esconde o ícone de editar no detalhe
            onDeleteClick = {},
            onEditClick = {}
        )
        binding.rvPagamentosDetalhe.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pagamentoAdapter
        }

        // ── Header expansível da aba "Histórico de Pagamento"
        setupExpandableHistorico()

        // Dados do funcionário
        observeFuncionario()

        // Histórico de pagamentos do funcionário
        observePagamentos()
    }

    private fun observeFuncionario() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeFuncionario(args.obraId, args.funcionarioId).collect { func ->
                    func?.let { bindData(it) }
                }
            }
        }
    }

    private fun bindData(f: Funcionario) = with(binding) {
        // 1) Título da Toolbar: "Funcionário [nome]"
        toolbarDetalheFuncionario.title = getString(R.string.func_detail_title, f.nome)

        // 2) Campos simples
        tvDetailNome.text = f.nome
        tvDetailFuncao.text = f.funcao

        // 3) Salário formatado
        tvDetailSalario.text = formatMoneyBR(f.salario)

        // 4) Forma de pagamento
        tvDetailPagamento.text = f.formaPagamento

        // 5) Pix
        val hasPix = !f.pix.isNullOrBlank()
        tvDetailPix.text = if (hasPix) f.pix else "—"
        btnCopyPix.isVisible = hasPix
        btnCopyPix.setOnClickListener {
            val text = tvDetailPix.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                val cm = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PIX", text))
                Toast.makeText(
                    requireContext(),
                    getString(R.string.func_toast_pix_copy),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // 6) Dias e Status
        tvDetailDias.text = f.diasTrabalhados.toString()
        tvDetailStatus.text = f.status.replaceFirstChar { it.titlecase() }
    }

    private fun observePagamentos() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observePagamentos(args.funcionarioId).collect { lista ->
                    renderPagamentos(lista)
                }
            }
        }
    }

    private fun renderPagamentos(lista: List<Pagamento>) = with(binding) {
        // lista no adapter
        pagamentoAdapter.submitList(lista)

        // Soma total pago e total geral
        val totalPago = lista.sumOf { it.valor }
        tvTotalPagoFuncionario.text = formatMoneyBR(totalPago)
        tvDetailTotalGasto.text = formatMoneyBR(totalPago)

        if (lista.isEmpty()) {
            // Esconde o card expansível e mostra título + texto simples
            cardAbaHistoricoPagtoDetalhe.isVisible = false
            tvHistoricoTitulo.isVisible = true
            tvHistoricoEmptyInline.isVisible = true

            // Garante conteúdo da aba fechado
            contentAbaHistoricoPagtoDetalhe.isVisible = false
            ivArrowHistoricoPagtoDetalhe.rotation = 0f
        } else {
            // Mostra o card e esconde o título/empty simples
            tvHistoricoTitulo.isVisible = false
            tvHistoricoEmptyInline.isVisible = false

            cardAbaHistoricoPagtoDetalhe.isVisible = true

            // Dentro do card: controla vazio/lista internos
            tvEmptyPagamentosDetalhe.isVisible = false      // não usamos o vazio interno
            rvPagamentosDetalhe.isVisible = true

            // Título (singular/plural) no cabeçalho do card
            tvTituloHistoricoPagtoDetalhe.text = if (lista.size == 1)
                getString(R.string.historico_pagamento)
            else
                getString(R.string.historico_pagamentos)
        }
    }

    private fun setupExpandableHistorico() = with(binding) {
        val header = headerAbaHistoricoPagtoDetalhe
        val content = contentAbaHistoricoPagtoDetalhe
        val arrow = ivArrowHistoricoPagtoDetalhe

        fun applyState(expanded: Boolean, animate: Boolean) {
            if (animate) {
                androidx.transition.TransitionManager.beginDelayedTransition(
                    content, androidx.transition.AutoTransition().apply { duration = 180 }
                )
            }
            content.isVisible = expanded
            arrow.animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()
        }

        // Estado inicial fechado (já vem GONE do XML)
        header.setOnClickListener { applyState(!content.isVisible, animate = true) }
    }

    private fun formatMoneyBR(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}