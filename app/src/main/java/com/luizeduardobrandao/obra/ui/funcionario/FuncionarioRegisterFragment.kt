package com.luizeduardobrandao.obra.ui.funcionario

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.Pagamento
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentFuncionarioRegisterBinding
import com.luizeduardobrandao.obra.ui.extensions.bindScrollToBottomFabBehavior
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.isAtBottom
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.extensions.updateFabVisibilityAnimated
import com.luizeduardobrandao.obra.ui.funcionario.adapter.PagamentoAdapter
import com.luizeduardobrandao.obra.utils.alignPagamentoTwoByTwo
import com.luizeduardobrandao.obra.utils.applyResponsiveButtonSizingGrowShrink
import com.luizeduardobrandao.obra.utils.applyFullWidthButtonSizingGrowShrink
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.ensureAlignedTwoRowsOrFallbackToThreeRows
import com.luizeduardobrandao.obra.utils.syncTextSizesGroup
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrToday
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrWithInitial
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class FuncionarioRegisterFragment : Fragment() {

    private var _binding: FragmentFuncionarioRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: FuncionarioRegisterFragmentArgs by navArgs()
    private val viewModel: FuncionarioViewModel by viewModels()

    private val isEdit get() = args.funcionarioId != null
    private var diasTrabalhados = 0

    private var funcionarioOriginal: Funcionario? = null

    // Loading do botão principal
    private var isSaving = false
    private var shouldCloseAfterSave = false

    // Pagamentos
    private lateinit var pagamentoAdapter: PagamentoAdapter

    // Cache da lista para update otimista
    private var cachedPagamentos: List<Pagamento> = emptyList()

    // Estado da edição
    private var pagamentoEmEdicao: Pagamento? = null
    private var editDataIso: String? = null
    private var dataLoaded = false
    private var watchersSet = false
    private var lockInitialTitleMargins = false

    // Valores MarginTop
    private val topErrorDp = 24          // quando há mensagem de erro
    private val topNoErrorDp = 16        // quando não há erro
    private val topEditInitialDp = 16    // nascer em edição

    private val Int.dp get() = (this * Resources.getSystem().displayMetrics.density).toInt()

    // Flags de “grudar no 12dp” até a 1ª interação do usuário
    private var funcaoStickyInitial = false     // depende de etNomeFunc
    private var pagtoStickyInitial = false      // depende de etSalario

    // Houve alterações na lista de pagamentos (add/editar/excluir) desde que a tela abriu?
    private var pagamentosAlterados: Boolean = false

    // Adições/Edições/Exclusões pendentes (somente local; não enviamos ao banco até confirmar)
    private val pagamentosExcluidosPendentes = mutableSetOf<Pagamento>()
    private val pagamentosAdicionadosPendentes = mutableListOf<Pagamento>()
    private val pagamentosEditadosPendentes = mutableMapOf<String, Pagamento>() // key = id original

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFuncionarioRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarFuncReg.setNavigationOnClickListener { handleBackPress() }

        // Estado inicial
        binding.btnSaveFuncionario.isEnabled = false

        // Se for edição, vamos preencher e SÓ DEPOIS liberar validação
        if (isEdit) {
            lockInitialTitleMargins = true
            funcaoStickyInitial = true
            pagtoStickyInitial = true

            val startTop = topEditInitialDp.dp   // 12dp
            binding.tvTitleFuncao.runBeforeFirstDraw {
                binding.tvTitleFuncao.setTopMarginNow(
                    startTop
                )
            }
            binding.tvTitlePagamento.runBeforeFirstDraw {
                binding.tvTitlePagamento.setTopMarginNow(
                    startTop
                )
            }

            prefillFields()
        } else {
            dataLoaded = true
            setupTextWatchersOnce()
            validateForm()
        }

        // FAB de rolagem – visível só quando: edição && !salvando && !no final
        bindScrollToBottomFabBehavior(
            fab = binding.fabScrollDown,
            scrollView = binding.funcRegScroll,
            isEditProvider = { isEdit },
            isSavingProvider = { isSaving }
        )
        binding.funcRegScroll.post {
            binding.fabScrollDown.updateFabVisibilityAnimated(
                isEdit && !isSaving && !binding.funcRegScroll.isAtBottom()
            )
        }

        // + / -
        binding.btnPlus.setOnClickListener { updateDias(+1) }
        binding.btnMinus.setOnClickListener { updateDias(-1) }

        // Botão principal
        binding.btnSaveFuncionario.setOnClickListener { onSave() }

        // ───────── Responsividade dos botões ─────────
        binding.btnAddPagamento.doOnPreDraw {
            binding.btnAddPagamento.applyResponsiveButtonSizingGrowShrink()
        }
        binding.btnSaveFuncionario.doOnPreDraw {
            binding.btnSaveFuncionario.applyFullWidthButtonSizingGrowShrink()
        }

        // ───────── Alinhamento + equalização de texto (Função & Pagamento) ─────────

        // reduzir espaço entre o check/radio e o texto (Função mais apertado)
        reduceChoiceInnerPadding(
            views = listOf(
                binding.rbPintor, binding.rbPedreiro, binding.rbEletricista,
                binding.rbEncanador, binding.rbAjudante, binding.rbOutro
            ),
            dp = 2
        )
        reduceChoiceInnerPadding(
            views = listOf(
                binding.rbDiaria,
                binding.rbSemanal,
                binding.rbMensal,
                binding.rbTarefeiro
            ),
            dp = 4
        )

        binding.rgFuncao.updateHorizontalPaddingDp(0)
        binding.rgFuncao2.updateHorizontalPaddingDp(0)
        binding.rowPagto1.updateHorizontalPaddingDp(0)
        binding.rowPagto2.updateHorizontalPaddingDp(0)

        // Gap condicionado à orientação
        val isLandscape = resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val funcColGapDp = if (isLandscape) 10 else 2   // Função: 2dp (portrait) → 12dp (landscape)
        val pagtoColGapDp =
            if (isLandscape) 16 else 4   // Pagamento: 4dp (portrait) → 16dp (landscape)

        // ——— Bump de tamanho (sp) por bucket de resolução/densidade ———
        //  • 720×1280 @ 320dpi → sem mudança
        //  • 1080×2424 @ 420dpi → +2sp
        //  • acima de 1080×2424 e ≥420dpi → +4sp
        val dm = resources.displayMetrics
        val minSide = minOf(dm.widthPixels, dm.heightPixels)
        val maxSide = maxOf(dm.widthPixels, dm.heightPixels)
        val dpi = dm.densityDpi

        val bumpSp = when {
            (minSide == 1080 && maxSide == 2424 && dpi >= 420) -> 1f
            (maxSide > 2424 && dpi >= 420) -> 4f
            else -> 0f
        }
        // base que os utilitários vão usar (eles reduzem se precisar)
        val normalSpBase = 20f + bumpSp

        var minSpFunc: Float? = null
        var minSpPagto: Float? = null

        fun equalizeIfReady() {
            val f = minSpFunc
            val p = minSpPagto
            if (f != null && p != null) {
                val globalMin = minOf(f, p)
                listOf(
                    binding.rbPintor, binding.rbPedreiro, binding.rbEletricista,
                    binding.rbEncanador, binding.rbAjudante, binding.rbOutro
                ).forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, globalMin) }
                listOf(binding.rbDiaria, binding.rbSemanal, binding.rbMensal, binding.rbTarefeiro)
                    .forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, globalMin) }
            }
        }

        // 1) Forma de Pagamento (2×2) — UMA ÚNICA CHAMADA, sem flash
        binding.rowPagto1.runBeforeFirstDraw {
            minSpPagto = alignPagamentoTwoByTwo(
                context = requireContext(),
                rowPagto1 = binding.rowPagto1,
                rowPagto2 = binding.rowPagto2,
                radios = listOf(
                    binding.rbDiaria,
                    binding.rbSemanal,
                    binding.rbMensal,
                    binding.rbTarefeiro
                ),
                normalTextSp = normalSpBase,     // ← aplica bump
                minTextSp = 12f,
                hGapDp = pagtoColGapDp
            )
            equalizeIfReady()
        }

        // 2) Função (2×3 → fallback 3×2) — sem flash
        binding.rgFuncao2.runBeforeFirstDraw {
            minSpFunc = ensureAlignedTwoRowsOrFallbackToThreeRows(
                context = requireContext(),
                rgRow1 = binding.rgFuncao,
                rgRow2 = binding.rgFuncao2,
                checkBoxes = listOf(
                    binding.rbPintor, binding.rbPedreiro, binding.rbEletricista,
                    binding.rbEncanador, binding.rbAjudante, binding.rbOutro
                ),
                normalTextSp = normalSpBase,     // ← aplica bump
                minTextSp = 12f,
                colGapDp = funcColGapDp
            )
            equalizeIfReady()
        }

        // Voltar físico/gesto
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (binding.overlayEditPagamento.isVisible) {
                fecharModalEdicao()
                return@addCallback
            }
            handleBackPress()
        }

        // Copiar PIX
        binding.tilPix.setEndIconOnClickListener {
            val text = binding.etPix.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                val cm =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PIX", text))
                Toast.makeText(
                    requireContext(),
                    getString(R.string.func_toast_pix_copy),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.func_toast_pix_copy_empty),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        /* ───────────── Pagamentos (somente no modo edição) ───────────── */
        binding.layoutPagamento.isVisible = isEdit
        binding.cardAbaHistoricoPagto.isVisible = false
        binding.tvEmptyPagamentos.isVisible = false

        if (isEdit) {
            pagamentoAdapter = PagamentoAdapter(
                showDelete = true,
                showEdit = true,
                onDeleteClick = { pagamento ->
                    showSnackbarFragment(
                        type = Constants.SnackType.WARNING.name,
                        title = getString(R.string.snack_delete_pagamento_title),
                        msg = getString(R.string.snack_delete_pagamento_msg),
                        btnText = getString(R.string.snack_button_yes),
                        onAction = {
                            cachedPagamentos = cachedPagamentos.filter { it.id != pagamento.id }
                            pagamentoAdapter.submitList(cachedPagamentos) {
                                val n = cachedPagamentos.size
                                if (n - 1 >= 0) pagamentoAdapter.notifyItemChanged(n - 1)
                            }
                            pagamentosExcluidosPendentes.add(pagamento)
                            pagamentosAlterados = true
                            if (cachedPagamentos.isEmpty()) {
                                binding.cardAbaHistoricoPagto.isVisible = false
                                binding.tvEmptyPagamentos.isVisible = true
                            }
                        },
                        btnNegativeText = getString(R.string.snack_button_no),
                        onNegative = { }
                    )
                },
                onEditClick = { pagamento -> abrirModalEdicao(pagamento) }
            )
            binding.rvPagamentos.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = pagamentoAdapter
            }

            observePagamentos(args.funcionarioId!!)
            setupExpandableHistorico()

            binding.btnAddPagamento.setOnClickListener { onAddPagamentoClick() }
            binding.btnCancelEditPagamento.setOnClickListener { fecharModalEdicao() }
            binding.btnSaveEditPagamento.setOnClickListener { onSaveEditPagamento() }
            binding.etEditData.setOnClickListener { abrirDatePickerEdicao() }

            // Responsividade dos botões do modal
            binding.cardEditPagamento.doOnPreDraw {
                binding.btnSaveEditPagamento.applyResponsiveButtonSizingGrowShrink()
                binding.btnCancelEditPagamento.applyResponsiveButtonSizingGrowShrink()
                binding.cardEditPagamento.syncTextSizesGroup(
                    binding.btnSaveEditPagamento, binding.btnCancelEditPagamento
                )
            }
        }

        // Label inicial dos dias
        updateDiasLabel()

        // Estado de salvar
        observeSaveState()
    }

    /* ───────────────────────── Pré-preenchimento (edição) ───────────────────────── */
    private fun prefillFields() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeFuncionario(args.obraId, args.funcionarioId!!)
                    .collect { func ->
                        func ?: return@collect
                        funcionarioOriginal = func

                        // 🔹 Define o título da Toolbar no modo edição: "Editar [Nome do Funcionário]"
                        binding.toolbarFuncReg.title =
                            getString(R.string.func_reg_title_edit, func.nome)

                        binding.apply {
                            etNomeFunc.setText(func.nome)
                            val nf =
                                NumberFormat.getNumberInstance(
                                    Locale("pt", "BR")
                                ).apply {
                                    minimumFractionDigits = 2
                                    maximumFractionDigits = 2
                                    isGroupingUsed = false
                                }
                            etSalario.setText(nf.format(func.salario))
                            etPix.setText(func.pix)
                            tvDias.text = func.diasTrabalhados.toString()
                            diasTrabalhados = func.diasTrabalhados

                            btnSaveFuncionario.setText(R.string.generic_update)

                            binding.btnSaveFuncionario.doOnPreDraw {
                                binding.btnSaveFuncionario.applyFullWidthButtonSizingGrowShrink()
                            }

                            val funcoesMarcadas = func.funcao.split("/").map { it.trim() }
                            getAllFuncaoCheckboxes().forEach { cb ->
                                cb.isChecked = funcoesMarcadas.any {
                                    it.equals(cb.text.toString(), ignoreCase = true)
                                }
                            }

                            selectPagamentoByText(func.formaPagamento)
                            selectSingleChoice(binding.rgStatus, func.status)
                            updateDiasLabel()
                        }
                        dataLoaded = true
                        setupTextWatchersOnce()
                        validateForm()   // primeira validação já com tudo preenchido, sem “flash”
                        lockInitialTitleMargins = false
                        reevalScrollFab()
                    }
            }
        }
    }

    private fun selectSingleChoice(group: RadioGroup, text: String) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is MaterialRadioButton &&
                child.text.toString().equals(text, ignoreCase = true)
            ) {
                child.isChecked = true
                return
            }
        }
    }

    /* ───────────────────────── Validação geral ───────────────────────── */
    private fun validateForm(): Boolean = with(binding) {
        if (!dataLoaded) {
            // limpa erros e não habilita o botão enquanto não estiver pronto
            tilNomeFunc.error = null
            tilSalario.error = null
            tvFuncaoError.visibility = View.GONE
            tvPagamentoError.visibility = View.GONE
            btnSaveFuncionario.isEnabled = false
            return false
        }
        // --- validações como já faz ---
        val nomeOk = etNomeFunc.text?.toString()?.trim().orEmpty()
            .let { it.length >= Constants.Validation.MIN_NAME }
        tilNomeFunc.error = if (!nomeOk)
            getString(R.string.func_reg_error_nome, Constants.Validation.MIN_NAME)
        else null

        val salarioOk = etSalario.text?.toString()
            ?.replace(',', '.')?.toDoubleOrNull()
            ?.let { it > Constants.Validation.MIN_SALDO } == true
        tilSalario.error = if (!salarioOk) getString(R.string.func_reg_error_salario) else null

        val funcaoOk = getCheckedFuncaoTexts().isNotEmpty()
        val pagtoOk = getAllPagamentoRadios().any { it.isChecked }

        tvFuncaoError.text = if (!funcaoOk) getString(R.string.func_reg_error_role) else null
        tvFuncaoError.visibility = if (!funcaoOk) View.VISIBLE else View.GONE

        tvPagamentoError.text = if (!pagtoOk) getString(R.string.func_reg_error_pagamento) else null
        tvPagamentoError.visibility = if (!pagtoOk) View.VISIBLE else View.GONE

        // --- AQUI está a mudança importante ---
        // Evita espaço fantasma do TextInputLayout:
        tilNomeFunc.isErrorEnabled = !nomeOk
        tilSalario.isErrorEnabled = !salarioOk

        val hasNomeError = !nomeOk
        val hasSalarioError = !salarioOk

        val topFunc = when {
            hasNomeError -> topErrorDp.dp
            isEdit && funcaoStickyInitial -> topEditInitialDp.dp  // ainda sem interação
            else -> topNoErrorDp.dp
        }
        val topPagto = when {
            hasSalarioError -> topErrorDp.dp
            isEdit && pagtoStickyInitial -> topEditInitialDp.dp   // ainda sem interação
            else -> topNoErrorDp.dp
        }

        // só anima após o primeiro layout do modo edição
        if (!isEdit || !lockInitialTitleMargins) {
            tvTitleFuncao.updateTopMarginAnimated(topFunc)
            tvTitlePagamento.updateTopMarginAnimated(topPagto)
        }


        val formOk = nomeOk && salarioOk && funcaoOk && pagtoOk
        btnSaveFuncionario.isEnabled = formOk
        formOk
    }


    /* ───────────────────────── Observa estado de salvar ───────────────────────── */
    private fun observeSaveState() {
        lifecycleScope.launch {
            viewModel.opState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        if (isSaving) progress(true)
                    }

                    is UiState.Success -> {
                        isSaving = false
                        if (shouldCloseAfterSave) {
                            val msgRes = if (isEdit) R.string.func_updated else R.string.func_added
                            Toast.makeText(
                                requireContext(),
                                getString(msgRes, binding.etNomeFunc.text.toString().trim()),
                                Toast.LENGTH_SHORT
                            ).show()
                            binding.root.hideKeyboard()
                            binding.root.post {
                                findNavController().navigateUp()
                                shouldCloseAfterSave = false
                            }
                        } else {
                            progress(false)
                            binding.btnSaveFuncionario.isEnabled = true
                        }
                    }

                    is UiState.ErrorRes -> {
                        progress(false)
                        isSaving = false
                        shouldCloseAfterSave = false
                        binding.btnSaveFuncionario.isEnabled = true
                        showSnackbarFragment(
                            Constants.SnackType.ERROR.name,
                            getString(R.string.snack_error),
                            getString(state.resId),
                            getString(R.string.snack_button_ok)
                        )
                    }

                    else -> Unit
                }
            }
        }
    }

    /* ───────────────────────── Pagamentos: observar & renderizar ───────────────────────── */
    private fun observePagamentos(funcId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observePagamentos(funcId).collect { listaFromDb ->
                    // 1) remove itens marcados p/ exclusão
                    var composed = listaFromDb.filter { dbItem ->
                        pagamentosExcluidosPendentes.none { it.id == dbItem.id }
                    }

                    // 2) aplica edições pendentes
                    if (pagamentosEditadosPendentes.isNotEmpty()) {
                        composed = composed.map { dbItem ->
                            pagamentosEditadosPendentes[dbItem.id] ?: dbItem
                        }
                    }

                    // 3) acrescenta os adicionados pendentes (id temporário)
                    if (pagamentosAdicionadosPendentes.isNotEmpty()) {
                        composed = composed + pagamentosAdicionadosPendentes
                    }

                    cachedPagamentos = composed
                    renderPagamentos(composed)
                }
            }
        }
    }

    private fun renderPagamentos(lista: List<Pagamento>) = with(binding) {
        pagamentoAdapter.submitList(lista) {
            val n = lista.size
            if (n - 2 >= 0) pagamentoAdapter.notifyItemChanged(n - 2)
            if (n - 1 >= 0) pagamentoAdapter.notifyItemChanged(n - 1)
        }

        tvEmptyPagamentos.isVisible = lista.isEmpty()

        if (lista.isNotEmpty()) {
            cardAbaHistoricoPagto.isVisible = true
            if (!contentAbaHistoricoPagto.isVisible) {
                contentAbaHistoricoPagto.isVisible = true
                ivArrowHistoricoPagto.rotation = 180f
            }
        }

        tvTituloHistoricoPagto.text = if (lista.size == 1)
            getString(R.string.historico_pagamento)
        else
            getString(R.string.historico_pagamentos)

        // Reavaliar FAB após atualizar lista/altura
        reevalScrollFab()
    }

    private fun setupExpandableHistorico() = with(binding) {
        val header = headerAbaHistoricoPagto
        val content = contentAbaHistoricoPagto
        val arrow = ivArrowHistoricoPagto

        fun applyState(expanded: Boolean, animate: Boolean) {
            if (animate) {
                androidx.transition.TransitionManager.beginDelayedTransition(
                    content,
                    androidx.transition.AutoTransition().apply { duration = 180 }
                )
            }
            content.isVisible = expanded
            arrow.animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()
        }
        header.setOnClickListener {
            applyState(!content.isVisible, animate = true)
            reevalScrollFab()
        }
    }

    /* ───────────────────────── Adicionar pagamento ───────────────────────── */
    private fun onAddPagamentoClick() = with(binding) {
        val valorTxt = etPagamento.text?.toString()?.trim().orEmpty()
        val valor = parseBrlToDouble(valorTxt)
        if (valor == null || valor <= 0.0) {
            showSnackbarFragment(
                type = Constants.SnackType.ERROR.name,
                title = getString(R.string.snack_error),
                msg = getString(R.string.erro_pagamento_valor),
                btnText = getString(R.string.snack_button_ok)
            )
            return@with
        }
        // 🔒 trava o botão para impedir taps múltiplos
        btnAddPagamento.isEnabled = false

        // Solicita data e, ao escolher, persiste
        showPagamentoDatePicker(valor)
    }

    private fun showPagamentoDatePicker(valorPre: Double? = null) {
        showMaterialDatePickerBrToday { chosen ->
            // chosen = "dd/MM/yyyy" → ISO "yyyy-MM-dd"
            val parts = chosen.split("/")
            val iso = String.format(
                Locale.ROOT, "%04d-%02d-%02d",
                parts[2].toInt(), parts[1].toInt(), parts[0].toInt()
            )

            valorPre?.let { v ->
                // pagamento "temporário" (apenas local/Recycler até salvar de fato)
                val temp = Pagamento(
                    id = "tmp-${System.currentTimeMillis()}",
                    valor = v,
                    data = iso
                )

                // garante consistência com exclusões pendentes
                pagamentosExcluidosPendentes.removeAll { it.id == temp.id }

                // marca como adição pendente
                pagamentosAdicionadosPendentes.add(temp)
                pagamentosAlterados = true

                // reflete no Recycler
                cachedPagamentos = cachedPagamentos + temp
                pagamentoAdapter.submitList(cachedPagamentos) {
                    val n = cachedPagamentos.size
                    if (n - 2 >= 0) pagamentoAdapter.notifyItemChanged(n - 2)
                    if (n - 1 >= 0) pagamentoAdapter.notifyItemChanged(n - 1)
                }

                // limpa campo e mostra a aba
                binding.etPagamento.setText("")
                binding.cardAbaHistoricoPagto.isVisible = true
                binding.btnAddPagamento.isEnabled = true
            }
        }
    }

    /* ───────────────────────── Edição via modal ───────────────────────── */
    private fun abrirModalEdicao(pagamento: Pagamento) = with(binding) {
        pagamentoEmEdicao = pagamento
        // Valor preenchido
        etEditValor.setText(formatMoneyNumber(pagamento.valor))
        // Data preenchida (texto dd/MM/yyyy) + iso para controle
        etEditData.setText(formatDateBR(pagamento.data))
        editDataIso = pagamento.data

        // Exibe modal (não fecha ao clicar fora)
        scrimEditPagamento.visibility = View.VISIBLE
        overlayEditPagamento.visibility = View.VISIBLE

        // Foco no valor
        etEditValor.requestFocus()
    }

    private fun fecharModalEdicao() = with(binding) {
        pagamentoEmEdicao = null
        editDataIso = null
        etEditValor.setText("")
        etEditData.setText("")

        scrimEditPagamento.visibility = View.GONE
        overlayEditPagamento.visibility = View.GONE
    }

    private fun abrirDatePickerEdicao() {
        val initialBr = binding.etEditData.text?.toString()  // já está em dd/MM/yyyy
        showMaterialDatePickerBrWithInitial(initialBr) { chosen ->
            // chosen = "dd/MM/yyyy"
            binding.etEditData.setText(chosen)
            editDataIso = run {
                val p = chosen.split("/")
                String.format(
                    Locale.ROOT,
                    "%04d-%02d-%02d",
                    p[2].toInt(),
                    p[1].toInt(),
                    p[0].toInt()
                )
            }
        }
    }

    private fun onSaveEditPagamento() {
        val original = pagamentoEmEdicao ?: return
        val valorTxt = binding.etEditValor.text?.toString()?.trim().orEmpty()
        val valorNovo = parseBrlToDouble(valorTxt)
        val dataIsoNova = editDataIso

        if (valorNovo == null || valorNovo <= 0.0 || dataIsoNova.isNullOrBlank()) {
            showSnackbarFragment(
                type = Constants.SnackType.ERROR.name,
                title = getString(R.string.snack_error),
                msg = getString(R.string.erro_pagamento_valor),
                btnText = getString(R.string.snack_button_ok)
            )
            return
        }

        val atualizado = original.copy(valor = valorNovo, data = dataIsoNova)

        // Marca como "edição pendente" (por id real; se for temporário, usa o id temporário)
        pagamentosEditadosPendentes[original.id] = atualizado
        pagamentosAlterados = true

        // Atualiza só local/Recycler
        cachedPagamentos = cachedPagamentos.map { if (it.id == original.id) atualizado else it }
        pagamentoAdapter.submitList(cachedPagamentos) {
            val n = cachedPagamentos.size
            if (n - 2 >= 0) pagamentoAdapter.notifyItemChanged(n - 2)
            if (n - 1 >= 0) pagamentoAdapter.notifyItemChanged(n - 1)
        }

        fecharModalEdicao()
    }

    /* ───────────────────────── Helpers ───────────────────────── */
    private fun parseBrlToDouble(text: String): Double? =
        text.replace(".", "").replace(',', '.').toDoubleOrNull()

    private fun formatDateBR(iso: String): String {
        // yyyy-MM-dd -> dd/MM/yyyy simples
        return if (iso.length == 10 && iso[4] == '-' && iso[7] == '-')
            "${iso.substring(8, 10)}/${iso.substring(5, 7)}/${iso.substring(0, 4)}"
        else iso
    }

    private fun formatMoneyNumber(value: Double): String {
        // sem símbolo, 2 casas, agrupamento desabilitado para edição
        val nf = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = false
        }
        return nf.format(value)
    }

    private fun updateDias(delta: Int) {
        diasTrabalhados = (diasTrabalhados + delta).coerceAtLeast(0)
        binding.tvDias.text = diasTrabalhados.toString()
    }

    private fun onSave() {
        if (!validateForm()) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.func_reg_error_required),
                getString(R.string.snack_button_ok)
            )
            return
        }

        binding.root.hideKeyboard()

        shouldCloseAfterSave = true
        isSaving = true
        progress(true)

        val funcionario = Funcionario(
            id = args.funcionarioId ?: "",
            nome = binding.etNomeFunc.text.toString().trim(),
            funcao = getCheckedFuncaoTexts().joinToString(" / "),
            salario = binding.etSalario.text.toString().replace(',', '.').toDouble(),
            formaPagamento = getCheckedRadioTextPagamento(),
            pix = binding.etPix.text.toString().trim(),
            diasTrabalhados = diasTrabalhados,
            status = getCheckedRadioText(binding.rgStatus).lowercase()
        )

        if (isEdit) {
            viewModel.updateFuncionario(funcionario)

            // 1) Exclusões pendentes
            pagamentosExcluidosPendentes.forEach { p ->
                viewModel.deletePagamento(args.funcionarioId!!, p.id)
            }
            pagamentosExcluidosPendentes.clear()

            // 2) Edições pendentes
            pagamentosEditadosPendentes.values.forEach { p ->
                // Se o item editado for um "temporário" (acabou de ser adicionado e foi editado),
                // não existe no banco ainda. Nesse caso ele também estará nos "adicionados pendentes"
                // e será tratado como adição (abaixo). Aqui só atualizamos os que já existem no DB.
                val isTemp = p.id.startsWith("tmp-")
                if (!isTemp) {
                    viewModel.updatePagamento(args.funcionarioId!!, p)
                }
            }
            pagamentosEditadosPendentes.clear()

            // 3) Adições pendentes (criam ID real)
            pagamentosAdicionadosPendentes.forEach { pTemp ->
                viewModel.addPagamento(args.funcionarioId!!, pTemp.copy(id = "")) // repo gera id
            }
            pagamentosAdicionadosPendentes.clear()

            // Limpa o “sujo” dos pagamentos
            pagamentosAlterados = false

        } else {
            viewModel.addFuncionario(funcionario)
        }
    }

    private fun getCheckedRadioText(group: RadioGroup): String {
        val id = group.checkedRadioButtonId
        if (id == -1) return ""
        val rb = group.findViewById<MaterialRadioButton>(id)
        return rb.text.toString()
    }

    private fun getCheckedFuncaoTexts(): List<String> =
        getAllFuncaoCheckboxes().filter { it.isChecked }.map { it.text.toString() }

    private fun getAllFuncaoCheckboxes(): List<MaterialCheckBox> =
        buildList {
            // Linha 1
            for (i in 0 until binding.rgFuncao.childCount) {
                (binding.rgFuncao.getChildAt(i) as? MaterialCheckBox)?.let { add(it) }
            }
            // Linha 2
            for (i in 0 until binding.rgFuncao2.childCount) {
                (binding.rgFuncao2.getChildAt(i) as? MaterialCheckBox)?.let { add(it) }
            }
            // Linha 3 (criada pelo helper, tag = "rgFuncao3")
            val rg3 = binding.root.findViewWithTag<RadioGroup>("rgFuncao3")
            if (rg3 != null) {
                for (i in 0 until rg3.childCount) {
                    (rg3.getChildAt(i) as? MaterialCheckBox)?.let { add(it) }
                }
            }
        }

    private fun updateDiasLabel() = with(binding) {
        val res = when {
            rbDiaria.isChecked -> R.string.func_reg_days_hint
            rbSemanal.isChecked -> R.string.func_reg_weeks_hint
            rbMensal.isChecked -> R.string.func_reg_months_hint
            rbTarefeiro.isChecked -> R.string.func_reg_task_fixed_hint
            else -> R.string.func_reg_days_hint
        }
        tvLabelDias.setText(res)
    }

    private fun getCheckedRadioTextPagamento(): String {
        return getAllPagamentoRadios().firstOrNull { it.isChecked }?.text?.toString().orEmpty()
    }

    private fun selectPagamentoByText(text: String) {
        val target = getAllPagamentoRadios().firstOrNull {
            it.text.toString().equals(text, ignoreCase = true)
        }
        target?.isChecked = true
    }

    private fun getAllPagamentoRadios() = listOf(
        binding.rbDiaria,
        binding.rbSemanal,
        binding.rbMensal,
        binding.rbTarefeiro
    )

    private fun progress(show: Boolean) = with(binding) {
        val saving = show && isSaving
        funcRegScroll.isEnabled = !saving
        btnSaveFuncionario.isEnabled = !saving

        // Controla FAB (some enquanto salva e quando está no fim)
        fabScrollDown.updateFabVisibilityAnimated(
            visible = isEdit && !saving && !funcRegScroll.isAtBottom()
        )

        progressSaveFuncionario.isVisible = saving

        if (saving) {
            requireActivity().currentFocus?.clearFocus()
            root.clearFocus()

            funcRegScroll.isFocusableInTouchMode = true
            funcRegScroll.requestFocus()

            root.hideKeyboard()

            progressSaveFuncionario.post {
                funcRegScroll.smoothScrollTo(0, progressSaveFuncionario.bottom)
            }
        }
    }

    /* ───────────────────────── Verificação de edição ───────────────────────── */
    private fun handleBackPress() {
        if (hasUnsavedChanges()) {
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.snack_attention),
                msg = getString(R.string.unsaved_confirm_msg),
                btnText = getString(R.string.snack_button_yes),
                onAction = { findNavController().navigateUp() },
                btnNegativeText = getString(R.string.snack_button_no),
                onNegative = { /* permanece nesta tela */ }
            )
        } else {
            findNavController().navigateUp()
        }
    }

    private fun hasUnsavedChanges(): Boolean = with(binding) {
        val nomeStr = etNomeFunc.text?.toString()?.trim().orEmpty()
        val salarioStr = etSalario.text?.toString()?.trim().orEmpty()
        val salarioNum = salarioStr.replace(',', '.').toDoubleOrNull()
        val pixStr = etPix.text?.toString()?.trim().orEmpty()

        val funcoesSel = getCheckedFuncaoTexts()
            .map { it.trim().lowercase(Locale.getDefault()) }
            .toSet()

        val formaPagto = getCheckedRadioTextPagamento()
        val statusStr = getCheckedRadioText(rgStatus).lowercase(Locale.getDefault())

        val diasAtual = diasTrabalhados

        if (!isEdit) {
            val temRadioPagto = getAllPagamentoRadios().any { it.isChecked }
            return@with nomeStr.isNotEmpty() ||
                    salarioStr.isNotEmpty() ||
                    pixStr.isNotEmpty() ||
                    funcoesSel.isNotEmpty() ||
                    temRadioPagto ||
                    statusStr.isNotEmpty() ||
                    diasAtual != 0 ||
                    pagamentosAlterados               // <--- add aqui também no modo "novo"
        }

        val orig = funcionarioOriginal ?: return@with false

        val salarioMudou = when {
            salarioNum == null -> true
            else -> salarioNum != orig.salario
        }

        val funcoesOrig = orig.funcao.split("/")
            .map { it.trim().lowercase(Locale.getDefault()) }
            .toSet()

        val formaMudou = !formaPagto.equals(orig.formaPagamento, ignoreCase = true)
        val statusMudou = !statusStr.equals(orig.status, ignoreCase = true)

        return@with nomeStr != orig.nome ||
                salarioMudou ||
                pixStr != (orig.pix ?: "") ||
                diasAtual != orig.diasTrabalhados ||
                formaMudou ||
                statusMudou ||
                funcoesSel != funcoesOrig ||
                pagamentosAlterados                   // <--- add aqui no modo edição
    }

    // “Recheck” útil para mudanças que alteram a altura/posição do conteúdo
    private fun reevalScrollFab() {
        binding.funcRegScroll.post {
            binding.fabScrollDown.updateFabVisibilityAnimated(
                isEdit && !isSaving && !binding.funcRegScroll.isAtBottom()
            )
        }
    }

    // Helper rápido no Fragment para alterar marginTop com animação
    private fun View.updateTopMarginAnimated(targetPx: Int, durationMs: Long = 160L) {
        val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val start = lp.topMargin
        if (start == targetPx) return

        ValueAnimator.ofInt(start, targetPx).apply {
            duration = durationMs
            addUpdateListener { anim ->
                lp.topMargin = anim.animatedValue as Int
                layoutParams = lp
                requestLayout()
            }
        }.start()
    }

    // Evita flash de pré carregamento em edição
    private fun setupTextWatchersOnce() = with(binding) {
        if (watchersSet) return
        watchersSet = true

        etNomeFunc.doAfterTextChanged {
            if (isEdit && funcaoStickyInitial) funcaoStickyInitial = false
            if (dataLoaded) validateForm()
        }
        etSalario.doAfterTextChanged {
            if (isEdit && pagtoStickyInitial) pagtoStickyInitial = false
            if (dataLoaded) validateForm()
        }

        getAllFuncaoCheckboxes().forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ -> if (dataLoaded) validateForm() }
        }
        getAllPagamentoRadios().forEach { rb ->
            rb.setOnCheckedChangeListener { button, isChecked ->
                if (isChecked) {
                    getAllPagamentoRadios().forEach { other ->
                        if (other != button && other.isChecked) other.isChecked = false
                    }
                    updateDiasLabel()
                    if (dataLoaded) validateForm()
                }
            }
        }
    }

    private inline fun View.runBeforeFirstDraw(crossinline block: () -> Unit) {
        val vto = viewTreeObserver
        vto.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(this)
                block()
                // garante que o novo layout seja aplicado antes do primeiro frame
                requestLayout()
                return false // cancela este draw; o próximo já vem ajustado (sem flash)
            }
        })
    }

    private fun View.setTopMarginNow(targetPx: Int) {
        val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (lp.topMargin != targetPx) {
            lp.topMargin = targetPx
            layoutParams = lp
            requestLayout()
        }
    }

    private fun View.updateHorizontalPaddingDp(dp: Int) {
        val px = (dp * Resources.getSystem().displayMetrics.density).toInt()
        setPaddingRelative(px, paddingTop, px, paddingBottom)
    }

    private fun reduceChoiceInnerPadding(views: List<TextView>, dp: Int = 4) {
        val px = (dp * Resources.getSystem().displayMetrics.density).toInt()
        views.forEach { tv ->
            tv.compoundDrawablePadding = px   // ícone ↔ texto
            // opcional: reduzir padding lateral do próprio item
            tv.setPaddingRelative(0, tv.paddingTop, 0, tv.paddingBottom)
        }
    }


    override fun onDestroyView() {
        // Remover listeners do scroll e do FAB
        binding.funcRegScroll.setOnScrollChangeListener(
            null as NestedScrollView.OnScrollChangeListener?
        )
        binding.fabScrollDown.setOnClickListener(null)

        _binding = null
        super.onDestroyView()
    }
}