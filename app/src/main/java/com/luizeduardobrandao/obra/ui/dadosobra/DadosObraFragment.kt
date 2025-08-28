package com.luizeduardobrandao.obra.ui.dadosobra

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Obra
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentDadosObraBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import java.text.NumberFormat
import java.util.Calendar
import androidx.activity.addCallback
import androidx.core.view.isGone
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrWithInitial
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrToday

@AndroidEntryPoint
class DadosObraFragment : Fragment() {

    private var _binding: FragmentDadosObraBinding? = null
    private val binding get() = _binding!!

    private val args: DadosObraFragmentArgs by navArgs()
    private val viewModel: DadosObraViewModel by viewModels()

    private var isDeleting = false

    // flags para evitar "flash" de erros
    private var dataLoaded = false
    private var watchersSet = false

    // guarda a data do aporte no formato ISO para salvar (yyyy-MM-dd)
    private var aporteDateIso: String? = null

    private var currentObra: Obra? = null

    private var isSavingObra = false   // true enquanto salva OU exclui a obra

    // Buffer local de aportes ainda NAO persistidos
    private data class AporteDraft(
        val valor: Double,
        val descricao: String,
        val dataIso: String
    )

    private val pendingAportes = mutableListOf<AporteDraft>()
    private var flushingAportes = false
    private var aporteFlushIndex = 0

    // Snapshot do saldo e label durante flush para evitar "flash" na UI
    private var saldoTotalSnapshot: String? = null
    private var aportesLabelSnapshot: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDadosObraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupObraDatePickers()
        setupListenersObra()
        setupListenersAporteCard()
        observeViewModel()

        // NÃO valide nem instale watchers agora; espere os dados chegarem
        binding.btnSalvarObra.isEnabled = false
        // Botão salvar aporte começa desabilitado
        binding.btnSalvarAporte.isEnabled = false

        // Intercepta o botão físico/gesto de voltar
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPress()
        }
    }

    /* ───────────────── Toolbar ───────────────── */
    private fun setupToolbar() = binding.toolbarDadosObra.setNavigationOnClickListener {
        handleBackPress()
    }

    /* ───────────────── DatePickers (Obra) ───────────────── */
    private fun setupObraDatePickers() = with(binding) {
        etDataInicioObra.setOnClickListener {
            showMaterialDatePickerBrWithInitial(etDataInicioObra.text?.toString()) { chosen ->
                etDataInicioObra.setText(chosen)
                validateForm()
            }
        }
        etDataFimObra.setOnClickListener {
            showMaterialDatePickerBrWithInitial(etDataFimObra.text?.toString()) { chosen ->
                etDataFimObra.setText(chosen)
                validateForm()
            }
        }
    }

    // Converte "dd/MM/yyyy" para LocalDate com segurança
    private fun parseBrDateOrNull(s: String?): java.time.LocalDate? {
        if (s.isNullOrBlank()) return null
        return try {
            val (dStr, mStr, yStr) = s.split("/")
            val d = dStr.toInt()
            val m = mStr.toInt()
            val y = yStr.toInt()
            java.time.LocalDate.of(y, m, d)
        } catch (_: Exception) {
            null
        }
    }

    /* ───────────────── Listeners (Obra) ───────────────── */
    private fun setupListenersObra() = with(binding) {
        btnSalvarObra.setOnClickListener {
            if (!validateForm()) return@setOnClickListener
            it.hideKeyboard()

            // ⬇️ INÍCIO DO LOADING TIPO “NOTA/FUNC/CRON/MAT”
            isDeleting = false
            isSavingObra = true
            progressBottom(true)
            btnExcluirObra.isEnabled = false

            viewModel.salvarObra(
                nome = etNomeCliente.text.toString(),
                endereco = etEnderecoObra.text.toString(),
                contato = etContatoObra.text.toString(),
                descricao = etDescricaoObra.text.toString(),
                dataInicio = etDataInicioObra.text.toString(),
                dataFim = etDataFimObra.text.toString()
            )
        }

        btnExcluirObra.setOnClickListener {
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.snack_warning),
                msg = getString(R.string.obra_data_snack_delete_msg),
                btnText = getString(R.string.snack_button_yes),
                onAction = {
                    // ⬇️ INÍCIO DO LOADING AO EXCLUIR
                    isDeleting = true
                    isSavingObra = true
                    progressBottom(true)
                    // ⬆️
                    viewModel.excluirObra()
                },
                btnNegativeText = getString(R.string.snack_button_no),
                onNegative = { /* nada */ }
            )
        }
    }


    /* ───────────────── Listeners (Aporte) ───────────────── */
    private fun setupListenersAporteCard() = with(binding) {
        // Abre/fecha o card de aporte
        btnAdicionarAporte.setOnClickListener {
            if (cardNovoAporte.isGone) {
                showAporteCard()
            } else {
                hideAporteCard(clear = true)
                tilAporteData.helperText = null
            }
        }

        // Data do aporte via date picker
        etAporteData.setOnClickListener {
            showAporteDatePicker()
        }

        // Valor: valida a cada mudança
        etAporteValor.doAfterTextChanged {
            validateAporteForm()
        }

        // Descrição é opcional, mas vamos revalidar por consistência
        etAporteDescricao.doAfterTextChanged {
            // sem regra obrigatória
        }

        // Cancelar: esconde e limpa
        btnCancelarAporte.setOnClickListener {
            hideAporteCard(clear = true)
        }

        // Salvar aporte temporariamente
        btnSalvarAporte.setOnClickListener {
            if (!validateAporteForm()) return@setOnClickListener

            val valor =
                etAporteValor.text.toString().replace(',', '.').toDoubleOrNull()
            val dataIso = aporteDateIso
            val desc = etAporteDescricao.text?.toString()?.trim().orEmpty()

            if (valor == null || dataIso.isNullOrBlank()) {
                validateAporteForm()
                return@setOnClickListener
            }

            it.hideKeyboard()

            // ✅ Nao persiste ainda: apenas adiciona ao buffer local
            pendingAportes += AporteDraft(
                valor = valor,
                descricao = desc,
                dataIso = dataIso
            )

            // Atualiza UI (saldo com aportes) imediatamente
            updateSaldoComAportesUI()

            // Limpa e esconde o card
            hideAporteCard(clear = true)

            Toast.makeText(
                requireContext(),
                getString(R.string.aporte_toast_new_added),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Mostre o saldo com aportes somando também os pendentes
    private fun updateSaldoComAportesUI() {
        // Durante o flush, nao recalcule: use o snapshot para evitar contagem dupla (flash)
        if (flushingAportes && saldoTotalSnapshot != null) {
            binding.llSaldoComAportes.visibility = View.VISIBLE
            aportesLabelSnapshot?.let { binding.tvSaldoAporteLabel.text = it }
            saldoTotalSnapshot?.let { binding.tvSaldoComAportesValor.text = it }
            return
        }
        // Usa o estado atual vindo do ViewModel (aportes do banco) + os pendentes locais
        val saldoInicial = currentObra?.saldoInicial ?: 0.0

        // Tente obter a lista atual carregada
        val aportesBanco = (viewModel.aportesState.value as? UiState.Success)?.data.orEmpty()
        val totalAportesBanco = aportesBanco.sumOf { it.valor }
        val totalAportesPendentes = pendingAportes.sumOf { it.valor }
        val totalCount = aportesBanco.size + pendingAportes.size

        if (totalCount == 0) {
            binding.llSaldoComAportes.visibility = View.GONE
            return
        }

        binding.llSaldoComAportes.visibility = View.VISIBLE
        binding.tvSaldoAporteLabel.text = resources.getQuantityString(
            R.plurals.title_aportes_header_plural, totalCount
        )
        binding.tvSaldoComAportesValor.text =
            formatMoneyBR(saldoInicial + totalAportesBanco + totalAportesPendentes)
    }

    private fun showAporteCard() = with(binding) {
        cardNovoAporte.visibility = View.VISIBLE
        // limpa erros/estado
        tilAporteValor.error = null
        tilAporteData.error = null
        etAporteValor.text = null
        etAporteDescricao.text = null
        etAporteData.setText("")
        aporteDateIso = null
        btnSalvarAporte.isEnabled = false
    }

    private fun hideAporteCard(clear: Boolean) = with(binding) {
        if (clear) {
            tilAporteValor.error = null
            tilAporteData.error = null
            etAporteValor.text = null
            etAporteDescricao.text = null
            etAporteData.setText("")
            aporteDateIso = null
            btnSalvarAporte.isEnabled = false
        }
        cardNovoAporte.visibility = View.GONE
    }

    private fun showAporteDatePicker() {
        showMaterialDatePickerBrToday { chosen ->
            binding.etAporteData.setText(chosen)

            // chosen = "dd/MM/yyyy"
            val parts = chosen.split("/")
            // ✅ Use Locale.ROOT para evitar dependência da localidade do dispositivo
            aporteDateIso = String.format(
                Locale.ROOT, "%04d-%02d-%02d",
                parts[2].toInt(), parts[1].toInt(), parts[0].toInt()
            )

            // Helper de data no passado (informativo), igual ao NotaRegister
            val sel = Calendar.getInstance().apply {
                set(
                    parts[2].toInt(),
                    parts[1].toInt() - 1,
                    parts[0].toInt(),
                    0,
                    0,
                    0
                )
                set(Calendar.MILLISECOND, 0)
            }
            val hoje = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            binding.tilAporteData.helperText =
                if (sel.before(hoje)) getString(R.string.nota_past_date_warning) else null

            validateAporteForm()
        }
    }

    /* ───────────────── Observers ───────────────── */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // 1) Dados da obra
                launch {
                    viewModel.obraState.collect { ui ->
                        when (ui) {
                            is UiState.Loading -> {
                                // Só mostra o loader central se NAO estivermos em operação de salvar/flush
                                if (!isSavingObra && !flushingAportes) {
                                    binding.progressDadosObra.visibility = View.VISIBLE
                                    binding.scrollDadosObra.visibility = View.GONE
                                }
                                dataLoaded = false
                            }

                            is UiState.Success -> {
                                currentObra = ui.data
                                populateFields(ui.data)
                                binding.progressDadosObra.visibility = View.GONE
                                binding.scrollDadosObra.visibility = View.VISIBLE
                                dataLoaded = true
                                setupTextWatchersOnce()
                                validateForm()
                                // ⬇️ Novo:
                                updateSaldoComAportesUI()
                            }

                            is UiState.ErrorRes -> {
                                binding.progressDadosObra.visibility = View.GONE
                                binding.scrollDadosObra.visibility = View.VISIBLE
                                showSnackbarIfNotDeleting(ui.resId)
                            }

                            else -> Unit
                        }
                    }
                }

                // 2) Resultado das operações da OBRA (salvar/atualizar/excluir obra)
                launch {
                    viewModel.opState.collect { ui ->
                        when (ui) {
                            is UiState.Loading -> {
                                // somente mostra o progress “embaixo do botão” quando a ação partiu do botão
                                if (isSavingObra) {
                                    progressBottom(true)
                                } else {
                                    // fallback (quase não usado aqui): mantém o spinner grande
                                    binding.progressDadosObra.visibility = View.VISIBLE
                                }
                            }

                            is UiState.Success -> {
                                // Obra salva/atualizada com sucesso
                                progressBottom(false)
                                isSavingObra = false

                                if (isDeleting) {
                                    isDeleting = false
                                    findNavController().navigate(
                                        DadosObraFragmentDirections.actionDadosObraToWork()
                                    )
                                    viewModel.resetOpState()
                                    return@collect
                                }

                                // ✅ Se houver aportes pendentes, vamos persistir agora (flush)
                                if (pendingAportes.isNotEmpty()) {
                                    // Congela o valor atual exibido na UI para evitar "flash" durante o flush
                                    saldoTotalSnapshot =
                                        binding.tvSaldoComAportesValor.text?.toString()
                                    aportesLabelSnapshot =
                                        binding.tvSaldoAporteLabel.text?.toString()
                                    flushingAportes = true
                                    aporteFlushIndex = 0
                                    addNextPendingAporte()
                                    // Nao navega ainda; so depois que terminar de salvar os pendentes
                                } else {
                                    // Sem pendentes: comportamento atual
                                    Toast.makeText(
                                        requireContext(),
                                        getString(R.string.obra_data_toast_updated),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    findNavController().navigateUp()
                                    viewModel.resetOpState()
                                }
                            }

                            is UiState.ErrorRes -> {
                                progressBottom(false)
                                isSavingObra = false

                                val msgRes = if (isDeleting)
                                    R.string.dados_obra_delete_error
                                else
                                    ui.resId

                                showSnackbarFragment(
                                    Constants.SnackType.ERROR.name,
                                    getString(R.string.snack_error),
                                    getString(msgRes),
                                    getString(R.string.snack_button_ok)
                                )
                                isDeleting = false
                                viewModel.resetOpState()
                            }

                            else -> Unit
                        }
                    }
                }

                // 3) Resultado das operações de APORTES (add/update/delete)
                launch {
                    viewModel.aporteOpState.collect { ui ->
                        when (ui) {
                            is UiState.Loading -> {
                                // Durante flush, nao exibir o loader central para evitar "flash"
                                if (!flushingAportes) {
                                    binding.progressDadosObra.visibility = View.VISIBLE
                                }
                                // Desabilita apenas os controles do card de aporte
                                binding.btnSalvarAporte.isEnabled = false
                            }

                            is UiState.Success -> {
                                binding.progressDadosObra.visibility = View.GONE

                                if (flushingAportes) {
                                    // Remova o draft recem-persistido
                                    if (aporteFlushIndex < pendingAportes.size) {
                                        pendingAportes.removeAt(aporteFlushIndex)
                                    }
                                    // Nao incremente o indice; o proximo item "desliza" para a mesma posicao
                                    addNextPendingAporte()
                                    // (Opcional) Atualize a UI apos remover o draft
                                    updateSaldoComAportesUI()
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        getString(R.string.aporte_toast_added),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    hideAporteCard(clear = true)
                                    viewModel.resetAporteOp()
                                }
                            }

                            is UiState.ErrorRes -> {
                                binding.progressDadosObra.visibility = View.GONE

                                // Em erro durante flush, avise e ABORTE o flush (nao navega)
                                showSnackbarFragment(
                                    Constants.SnackType.ERROR.name,
                                    getString(R.string.snack_error),
                                    getString(ui.resId),
                                    getString(R.string.snack_button_ok)
                                )
                                flushingAportes = false
                                // limpar snapshots (ja que voltamos a reprocessar normalmente)
                                saldoTotalSnapshot = null
                                aportesLabelSnapshot = null
                                // Nao limpamos pendingAportes: usuario pode tentar salvar de novo
                                viewModel.resetAporteOp()
                            }

                            else -> Unit
                        }
                    }
                }

                // 4) Aportes da obra: mostrar "Saldo com aporte" quando houver ao menos 1
                launch {
                    viewModel.aportesState.collect { ui ->
                        when (ui) {
                            is UiState.Success -> {
                                // Agora quem calcula e exibe e o helper, pois ele soma banco + pendentes
                                updateSaldoComAportesUI()
                            }

                            is UiState.ErrorRes -> {
                                // Mesmo em erro, ainda pode haver pendentes locais
                                updateSaldoComAportesUI()
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun showSnackbarIfNotDeleting(resId: Int) {
        if (!isDeleting) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(resId),
                getString(R.string.snack_button_ok)
            )
        }
    }

    /* ───────────────── Watchers / Populate ───────────────── */
    private fun setupTextWatchersOnce() {
        if (watchersSet) return
        watchersSet = true
        listOf(
            binding.etNomeCliente,
            binding.etEnderecoObra,
            binding.etContatoObra,
            binding.etDataInicioObra,
            binding.etDataFimObra
        ).forEach { edit ->
            edit.doAfterTextChanged { if (dataLoaded) validateForm() }
        }
    }

    private fun populateFields(obra: Obra) = with(binding) {
        toolbarDadosObra.title = getString(R.string.obra_data_title, obra.nomeCliente)
        etNomeCliente.setText(obra.nomeCliente)
        etEnderecoObra.setText(obra.endereco)
        etContatoObra.setText(obra.contato)
        etDescricaoObra.setText(obra.descricao)
        tvSaldoInicialValor.text = formatMoneyBR(obra.saldoInicial)
        etDataInicioObra.setText(obra.dataInicio)
        etDataFimObra.setText(obra.dataFim)
        // card de aporte não depende do estado da obra; permanece limpo/oculto
    }

    /* ───────────────── Validações ───────────────── */
    /** Valida somente depois de dataLoaded = true para evitar flash de erros */
    private fun validateForm(): Boolean {
        if (!dataLoaded) {
            binding.btnSalvarObra.isEnabled = false
            clearErrors()
            return false
        }

        var isValid = true
        binding.apply {
            clearErrors()

            if (etNomeCliente.text.isNullOrBlank() ||
                etNomeCliente.text!!.trim().length < Constants.Validation.MIN_NAME
            ) {
                tilNomeCliente.error = getString(R.string.dados_obra_name_error)
                isValid = false
            }
            if (etEnderecoObra.text.isNullOrBlank()) {
                tilEnderecoObra.error = getString(R.string.dados_obra_address_error)
                isValid = false
            }
            if (etContatoObra.text.isNullOrBlank()) {
                tilContatoObra.error = getString(R.string.work_error_contato)
                isValid = false
            }
            if (etDataInicioObra.text.isNullOrBlank()) {
                tilDataInicioObra.error = getString(R.string.dados_obra_date_start_error)
                isValid = false
            }
            if (etDataFimObra.text.isNullOrBlank()) {
                tilDataFimObra.error = getString(R.string.dados_obra_date_end_error)
                isValid = false
            }

            // Impedir data de término > que data de início
            val start = parseBrDateOrNull(etDataInicioObra.text?.toString())
            val end = parseBrDateOrNull(etDataFimObra.text?.toString())
            if (start != null && end != null && end.isBefore(start)) {
                tilDataFimObra.error = getString(R.string.dados_obra_date_end_before_start)
                isValid = false
            }

            btnSalvarObra.isEnabled = isValid
        }
        return isValid
    }

    private fun validateAporteForm(): Boolean = with(binding) {
        var ok = true

        tilAporteValor.error = null
        tilAporteData.error = null

        val valor =
            etAporteValor.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        if (valor == null || valor <= 0.0) {
            tilAporteValor.error = getString(R.string.aporte_value_error)
            ok = false
        }

        if (aporteDateIso.isNullOrBlank()) {
            tilAporteData.error = getString(R.string.aporte_date_error)
            ok = false
        }

        btnSalvarAporte.isEnabled = ok
        ok
    }

    private fun clearErrors() = with(binding) {
        tilNomeCliente.error = null
        tilEnderecoObra.error = null
        tilDataInicioObra.error = null
        tilDataFimObra.error = null
    }

    private fun formatMoneyBR(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    // ---------------- Verificação de Edição -----------------

    private fun handleBackPress() {
        if (hasUnsavedChanges()) {
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.snack_attention),
                msg = getString(R.string.unsaved_confirm_msg),
                btnText = getString(R.string.snack_button_yes), // SIM
                onAction = { findNavController().navigateUp() },
                btnNegativeText = getString(R.string.snack_button_no), // NÃO
                onNegative = { /* permanece na tela */ }
            )
        } else {
            findNavController().navigateUp()
        }
    }

    /** True se houver alterações não salvas na OBRA ou um aporte parcialmente preenchido. */
    private fun hasUnsavedChanges(): Boolean = with(binding) {
        // Campos atuais (OBRA)
        val nomeAtual = etNomeCliente.text?.toString()?.trim().orEmpty()
        val endAtual = etEnderecoObra.text?.toString()?.trim().orEmpty()
        val contatoAtual = etContatoObra.text?.toString()?.trim().orEmpty()
        val descAtual = etDescricaoObra.text?.toString()?.trim().orEmpty()
        val dataIniAtual = etDataInicioObra.text?.toString()?.trim().orEmpty()
        val dataFimAtual = etDataFimObra.text?.toString()?.trim().orEmpty()

        // Aporte parcialmente preenchido conta como alteração pendente
        val aporteValorTxt = etAporteValor.text?.toString()?.trim().orEmpty()
        val aporteDescTxt = etAporteDescricao.text?.toString()?.trim().orEmpty()
        val aportePendente = !cardNovoAporte.isGone &&  // card visível
                (aporteValorTxt.isNotEmpty() || !aporteDateIso.isNullOrBlank() || aporteDescTxt.isNotEmpty())
        val haAportesPendentesNaoPersistidos = pendingAportes.isNotEmpty()

        // Se ainda não temos a obra original (ou for um novo cadastro de obra), compara com "vazio"
        val obraOrig = currentObra
            ?: return@with nomeAtual.isNotEmpty() ||
                    endAtual.isNotEmpty() ||
                    contatoAtual.isNotEmpty() ||
                    descAtual.isNotEmpty() ||
                    dataIniAtual.isNotEmpty() ||
                    dataFimAtual.isNotEmpty() ||
                    aportePendente ||
                    haAportesPendentesNaoPersistidos

        // Comparação com o original
        return@with (nomeAtual != obraOrig.nomeCliente) ||
                (endAtual != obraOrig.endereco) ||
                (contatoAtual != obraOrig.contato) ||
                (descAtual != (obraOrig.descricao?.trim().orEmpty())) ||
                (dataIniAtual != obraOrig.dataInicio) ||
                (dataFimAtual != obraOrig.dataFim) ||
                aportePendente ||
                haAportesPendentesNaoPersistidos
    }

    private fun progressBottom(show: Boolean) = with(binding) {
        // trava scroll e botões somente durante a operação de salvar/excluir
        val saving = show && isSavingObra

        scrollDadosObra.isEnabled = !saving
        btnSalvarObra.isEnabled = !saving
        btnExcluirObra.isEnabled = !saving

        progressSaveObra.visibility = if (saving) View.VISIBLE else View.GONE

        if (saving) {
            // 1) limpar focos para evitar auto-scroll do sistema
            requireActivity().currentFocus?.clearFocus()
            root.clearFocus()

            // 2) segurar o foco no container não-editável
            scrollDadosObra.isFocusableInTouchMode = true
            scrollDadosObra.requestFocus()

            // 3) fechar teclado
            root.hideKeyboard()

            // 4) rolar até o indicador para garantir visibilidade
            progressSaveObra.post {
                scrollDadosObra.smoothScrollTo(0, progressSaveObra.bottom)
            }
        }
    }

    private fun addNextPendingAporte() {
        if (aporteFlushIndex >= pendingAportes.size) {
            flushingAportes = false
            pendingAportes.clear()
            Toast.makeText(
                requireContext(),
                getString(R.string.obra_data_toast_updated),
                Toast.LENGTH_SHORT
            ).show()
            // limpar snapshots
            saldoTotalSnapshot = null
            aportesLabelSnapshot = null

            findNavController().navigateUp()
            viewModel.resetOpState()
            viewModel.resetAporteOp()
            return
        }
        val draft = pendingAportes[aporteFlushIndex] // sempre na posicao atual
        viewModel.addAporte(
            valor = draft.valor,
            descricao = draft.descricao,
            dataIso = draft.dataIso
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}