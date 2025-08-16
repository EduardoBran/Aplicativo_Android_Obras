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
import com.google.android.material.datepicker.MaterialDatePicker
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Obra
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentDadosObraBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.DatePickerDialog
import android.widget.DatePicker
import java.text.NumberFormat
import java.util.Calendar

@AndroidEntryPoint
class DadosObraFragment : Fragment() {

    private var _binding: FragmentDadosObraBinding? = null
    private val binding get() = _binding!!

    private val args: DadosObraFragmentArgs by navArgs()
    private val viewModel: DadosObraViewModel by viewModels()

    private val formatterBr =
        SimpleDateFormat(Constants.Format.DATE_PATTERN_BR, Locale.getDefault())

    private var isDeleting = false
    private var isAddingAporte = false

    // flags para evitar "flash" de erros
    private var dataLoaded = false
    private var watchersSet = false

    // guarda a data do aporte no formato ISO para salvar (yyyy-MM-dd)
    private var aporteDateIso: String? = null

    private var currentObra: Obra? = null

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /* ───────────────── Toolbar ───────────────── */
    private fun setupToolbar() = binding.toolbarDadosObra.setNavigationOnClickListener {
        findNavController().navigateUp()
    }

    /* ───────────────── DatePickers (Obra) ───────────────── */
    private fun setupObraDatePickers() = with(binding) {
        etDataInicioObra.setOnClickListener { showDatePicker { etDataInicioObra.setText(it) } }
        etDataFimObra.setOnClickListener { showDatePicker { etDataFimObra.setText(it) } }
    }

    private fun showDatePicker(onResult: (String) -> Unit) {
        MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.date_picker_title))
            .build()
            .apply {
                addOnPositiveButtonClickListener { millis ->
                    val chosen = formatterBr.format(Date(millis))
                    onResult(chosen)
                    validateForm() // só fará algo quando dataLoaded = true
                }
            }
            .show(childFragmentManager, "DATE_PICKER")
    }

    /* ───────────────── Listeners (Obra) ───────────────── */
    private fun setupListenersObra() = with(binding) {
        btnSalvarObra.setOnClickListener {
            if (!validateForm()) return@setOnClickListener
            it.hideKeyboard()
            viewModel.salvarObra(
                nome = etNomeCliente.text.toString(),
                endereco = etEnderecoObra.text.toString(),
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
                btnText = getString(R.string.snack_button_yes),          // SIM
                onAction = {
                    isDeleting = true
                    viewModel.excluirObra()
                },
                btnNegativeText = getString(R.string.snack_button_no),   // NÃO
                onNegative = { /* fecha */ }
            )
        }
    }

    /* ───────────────── Listeners (Aporte) ───────────────── */
    private fun setupListenersAporteCard() = with(binding) {
        // Abre/fecha o card de aporte
        btnAdicionarAporte.setOnClickListener {
            if (cardNovoAporte.visibility == View.GONE) {
                showAporteCard()
            } else {
                hideAporteCard(clear = true)
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

        // Salvar aporte
        btnSalvarAporte.setOnClickListener {
            if (!validateAporteForm()) return@setOnClickListener

            val valor = etAporteValor.text.toString().replace(',', '.').toDoubleOrNull()
            val dataIso = aporteDateIso
            val desc = etAporteDescricao.text?.toString()?.trim().orEmpty()

            if (valor == null || dataIso.isNullOrBlank()) {
                // segurança extra
                validateAporteForm()
                return@setOnClickListener
            }

            it.hideKeyboard()
            isAddingAporte = true
            // Chame aqui o método do seu VM (ajuste o nome se necessário)
            viewModel.addAporte(
                valor = valor,
                descricao = desc,
                dataIso = dataIso
            )
        }
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
        val c = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _: DatePicker, y: Int, m: Int, d: Int ->
                // Mostra no campo em BR (dd/MM/yyyy)
                val brText = "%02d/%02d/%04d".format(d, m + 1, y)
                binding.etAporteData.setText(brText)

                // Guarda ISO (yyyy-MM-dd) para salvar
                aporteDateIso = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)

                // Helper de data no passado (informativo), igual ao NotaRegister
                val sel = Calendar.getInstance().apply {
                    set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                }
                val hoje = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                binding.tilAporteData.helperText =
                    if (sel.before(hoje)) getString(R.string.nota_past_date_warning) else null

                validateAporteForm()
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
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
                                binding.progressDadosObra.visibility = View.VISIBLE
                                binding.scrollDadosObra.visibility = View.GONE
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
                            is UiState.Loading -> binding.progressDadosObra.visibility =
                                View.VISIBLE

                            is UiState.Success -> {
                                binding.progressDadosObra.visibility = View.GONE
                                if (isDeleting) {
                                    isDeleting = false
                                    findNavController().navigate(
                                        DadosObraFragmentDirections.actionDadosObraToWork()
                                    )
                                } else {
                                    // sucesso ao salvar/atualizar a OBRA
                                    Toast.makeText(
                                        requireContext(),
                                        getString(R.string.obra_data_toast_updated),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    findNavController().navigateUp()
                                }
                                viewModel.resetOpState()
                            }

                            is UiState.ErrorRes -> {
                                binding.progressDadosObra.visibility = View.GONE
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
                                binding.progressDadosObra.visibility = View.VISIBLE
                                binding.btnSalvarAporte.isEnabled = false
                            }

                            is UiState.Success -> {
                                binding.progressDadosObra.visibility = View.GONE
                                // ✅ Somente TOAST no sucesso (NÃO mostrar snackbar)
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.aporte_toast_added),
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Esconde e limpa o card
                                hideAporteCard(clear = true)
                                viewModel.resetAporteOp()
                            }

                            is UiState.ErrorRes -> {
                                binding.progressDadosObra.visibility = View.GONE
                                // Erro de aporte -> Snackbar
                                showSnackbarFragment(
                                    Constants.SnackType.ERROR.name,
                                    getString(R.string.snack_error),
                                    getString(ui.resId),
                                    getString(R.string.snack_button_ok)
                                )
                                binding.btnSalvarAporte.isEnabled = true
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
                                val aportes = ui.data
                                if (aportes.isEmpty()) {
                                    // sem aportes -> esconde a linha
                                    binding.llSaldoComAportes.visibility = View.GONE
                                } else {
                                    // há aportes -> mostra linha e calcula (Saldo Inicial + total de aportes)
                                    val totalAportes = aportes.sumOf { it.valor }
                                    val saldoInicial = currentObra?.saldoInicial ?: 0.0
                                    binding.llSaldoComAportes.visibility = View.VISIBLE
                                    // ⬇️ título com plural: "Saldo total com aporte(s)"
                                    binding.tvSaldoAporteLabel.text = resources.getQuantityString(
                                        R.plurals.title_aportes_header_plural,
                                        aportes.size
                                    )
                                    binding.tvSaldoComAportesValor.text =
                                        formatMoneyBR(saldoInicial + totalAportes)
                                }
                            }

                            is UiState.ErrorRes -> {
                                // Em caso de erro no carregamento de aportes, esconda a linha
                                binding.llSaldoComAportes.visibility = View.GONE
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
            if (etDataInicioObra.text.isNullOrBlank()) {
                tilDataInicioObra.error = getString(R.string.dados_obra_date_start_error)
                isValid = false
            }
            if (etDataFimObra.text.isNullOrBlank()) {
                tilDataFimObra.error = getString(R.string.dados_obra_date_end_error)
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

        val valor = etAporteValor.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
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
}