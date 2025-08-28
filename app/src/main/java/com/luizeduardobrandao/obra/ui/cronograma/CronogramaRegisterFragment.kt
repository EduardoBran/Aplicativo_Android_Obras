package com.luizeduardobrandao.obra.ui.cronograma

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentCronogramaRegisterBinding
import com.luizeduardobrandao.obra.ui.cronograma.adapter.CronogramaPagerAdapter
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrWithInitial
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrToday

@AndroidEntryPoint
class CronogramaRegisterFragment : Fragment() {

    private var _binding: FragmentCronogramaRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: CronogramaRegisterFragmentArgs by navArgs()
    private val viewModel: CronogramaViewModel by viewModels()

    // Inclusão quando etapaId == null
    private val isEdit get() = args.etapaId != null
    private var etapaOriginal: Etapa? = null

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
        isLenient = false
    }

    // Controle de loading/navegação (mesmo padrão de Nota/Funcionário)
    private var isSaving = false
    private var shouldCloseAfterSave = false

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCronogramaRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            // Toolbar back & title
            toolbarEtapaReg.setNavigationOnClickListener { handleBackPress() }
            toolbarEtapaReg.title = if (isEdit)
                getString(R.string.etapa_reg_title_edit)
            else
                getString(R.string.etapa_reg_title)

            // Date pickers
            etDataInicioEtapa.setOnClickListener {
                if (isEdit) {
                    showMaterialDatePickerBrWithInitial(
                        etDataInicioEtapa.text?.toString()
                    ) { chosen ->
                        binding.etDataInicioEtapa.setText(chosen)
                        validateForm()
                    }
                } else {
                    showMaterialDatePickerBrToday { chosen ->
                        binding.etDataInicioEtapa.setText(chosen)
                        validateForm()
                    }
                }
            }
            etDataFimEtapa.setOnClickListener {
                if (isEdit) {
                    showMaterialDatePickerBrWithInitial(etDataFimEtapa.text?.toString()) { chosen ->
                        binding.etDataFimEtapa.setText(chosen)
                        validateForm()
                    }
                } else {
                    showMaterialDatePickerBrToday { chosen ->
                        binding.etDataFimEtapa.setText(chosen)
                        validateForm()
                    }
                }
            }

            // Botão Salvar/Atualizar
            btnSaveEtapa.text = getString(
                if (isEdit) R.string.generic_update else R.string.generic_save
            )
            btnSaveEtapa.isEnabled = false
            btnSaveEtapa.setOnClickListener { onSaveClicked() }

            // Validação dinâmica
            setupValidation()

            // Coleta estado de operação (loading/sucesso/erro)
            collectOperationState()
        }

        // Back físico/gesto
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPress()
        }

        // Edição: observar a lista para preencher a etapa
        if (isEdit) {
            viewModel.loadEtapas()
            observeEtapa()
        }
    }

    /*──────────── Observa a lista para obter a etapa em modo edição ───────────*/
    private fun observeEtapa() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    if (ui is UiState.Success) {
                        val et = ui.data.firstOrNull { it.id == args.etapaId } ?: return@collect
                        etapaOriginal = et
                        fillFields(et)
                    }
                }
            }
        }
    }

    /*──────────── Coleta opState para mostrar progress/navegar ───────────*/
    private fun collectOperationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.opState.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> {
                            if (isSaving) progress(true)
                        }

                        is UiState.Success -> {
                            isSaving = false
                            if (shouldCloseAfterSave) {
                                val msgRes = if (isEdit)
                                    R.string.etapa_update_success
                                else
                                    R.string.etapa_add_success
                                Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()

                                binding.root.post {
                                    findNavController().navigateUp()
                                    shouldCloseAfterSave = false
                                }
                            } else {
                                progress(false)
                                binding.btnSaveEtapa.isEnabled = true
                            }
                        }

                        is UiState.ErrorRes -> {
                            progress(false)
                            isSaving = false
                            shouldCloseAfterSave = false
                            binding.btnSaveEtapa.isEnabled = true
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

    /*──────────── Preenche os campos em modo edição ───────────*/
    private fun fillFields(e: Etapa) = with(binding) {
        etTituloEtapa.setText(e.titulo)
        etDescEtapa.setText(e.descricao)
        etFuncEtapa.setText(e.funcionarios)
        etDataInicioEtapa.setText(e.dataInicio)
        etDataFimEtapa.setText(e.dataFim)
        when (e.status) {
            CronogramaPagerAdapter.STATUS_PENDENTE -> rbStatPend.isChecked = true
            CronogramaPagerAdapter.STATUS_ANDAMENTO -> rbStatAnd.isChecked = true
            else -> rbStatConcl.isChecked = true
        }
        validateForm()
    }

    /*──────────── Salvar / Validar ───────────*/
    private fun onSaveClicked() {
        val titulo = binding.etTituloEtapa.text.toString().trim()
        val dataIni = binding.etDataInicioEtapa.text.toString().trim()
        val dataFim = binding.etDataFimEtapa.text.toString().trim()

        if (titulo.length < Constants.Validation.MIN_NAME) {
            showError(R.string.etapa_error_title)
            return
        }
        if (dataIni.isBlank() || dataFim.isBlank()) {
            showError(R.string.etapa_error_dates)
            return
        }
        if (!isDateOrderValid(dataIni, dataFim)) {
            binding.tilDataFimEtapa.error = getString(R.string.etapa_error_date_order)
            return
        }

        val status = when (binding.rgStatusEtapa.checkedRadioButtonId) {
            R.id.rbStatAnd -> CronogramaPagerAdapter.STATUS_ANDAMENTO
            R.id.rbStatConcl -> CronogramaPagerAdapter.STATUS_CONCLUIDO
            else -> CronogramaPagerAdapter.STATUS_PENDENTE
        }

        val etapa = Etapa(
            id = etapaOriginal?.id ?: "",
            titulo = titulo,
            descricao = binding.etDescEtapa.text.toString().trim(),
            funcionarios = binding.etFuncEtapa.text.toString().trim(),
            dataInicio = dataIni,
            dataFim = dataFim,
            status = status
        )

        // Fluxo de loading como Nota/Funcionário
        shouldCloseAfterSave = true
        isSaving = true
        progress(true)

        if (isEdit) viewModel.updateEtapa(etapa) else viewModel.addEtapa(etapa)
    }

    private fun showError(@androidx.annotation.StringRes res: Int) {
        showSnackbarFragment(
            Constants.SnackType.ERROR.name,
            getString(R.string.snack_error),
            getString(res),
            getString(R.string.snack_button_ok)
        )
    }

    /*──────────── Habilitação dinâmica do botão ───────────*/
    private fun setupValidation() = with(binding) {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateForm()
            }

            override fun afterTextChanged(s: Editable?) {}
        }

        etTituloEtapa.addTextChangedListener(watcher)
        etDataInicioEtapa.addTextChangedListener(watcher)
        etDataFimEtapa.addTextChangedListener(watcher)

        rgStatusEtapa.setOnCheckedChangeListener { _, _ -> validateForm() }

        validateForm()
    }

    private fun validateForm() = with(binding) {
        val titulo = etTituloEtapa.text?.toString()?.trim().orEmpty()
        val tituloOk = titulo.isNotEmpty()
        tilTituloEtapa.error =
            if (!tituloOk) getString(R.string.etapa_error_title_required) else null

        val iniStr = etDataInicioEtapa.text?.toString().orEmpty()
        val fimStr = etDataFimEtapa.text?.toString().orEmpty()
        val iniOk = iniStr.isNotBlank()
        val fimOk = fimStr.isNotBlank()

        tilDataInicioEtapa.error =
            if (!iniOk) getString(R.string.etapa_error_date_start_required) else null
        tilDataFimEtapa.error =
            if (!fimOk) getString(R.string.etapa_error_date_end_required) else null

        val ordemOk = if (iniOk && fimOk) isDateOrderValid(iniStr, fimStr) else false
        if (iniOk && fimOk && !ordemOk) {
            tilDataFimEtapa.error = getString(R.string.etapa_error_date_order)
        }

        val enabled = tituloOk && iniOk && fimOk && ordemOk
        if (!isSaving && !shouldCloseAfterSave) {
            btnSaveEtapa.isEnabled = enabled
        }
    }

    private fun parseDateOrNull(s: String?): Date? = try {
        if (s.isNullOrBlank()) null else sdf.parse(s)
    } catch (_: ParseException) {
        null
    }

    private fun isDateOrderValid(dataInicio: String?, dataFim: String?): Boolean {
        val ini = parseDateOrNull(dataInicio) ?: return false
        val fim = parseDateOrNull(dataFim) ?: return false
        return !fim.before(ini)
    }

    // ---------------- Verificação de Edição -----------------

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
        val titulo = etTituloEtapa.text?.toString()?.trim().orEmpty()
        val desc = etDescEtapa.text?.toString()?.trim().orEmpty()
        val func = etFuncEtapa.text?.toString()?.trim().orEmpty()
        val ini = etDataInicioEtapa.text?.toString()?.trim().orEmpty()
        val fim = etDataFimEtapa.text?.toString()?.trim().orEmpty()

        val statusAtual = when (rgStatusEtapa.checkedRadioButtonId) {
            R.id.rbStatAnd -> CronogramaPagerAdapter.STATUS_ANDAMENTO
            R.id.rbStatConcl -> CronogramaPagerAdapter.STATUS_CONCLUIDO
            else -> CronogramaPagerAdapter.STATUS_PENDENTE
        }

        if (!isEdit) {
            val statusDefault = CronogramaPagerAdapter.STATUS_PENDENTE
            return@with titulo.isNotEmpty() ||
                    desc.isNotEmpty() ||
                    func.isNotEmpty() ||
                    ini.isNotEmpty() ||
                    fim.isNotEmpty() ||
                    statusAtual != statusDefault
        }

        val orig = etapaOriginal ?: return@with false
        return@with titulo != orig.titulo ||
                desc != orig.descricao ||
                func != orig.funcionarios ||
                ini != orig.dataInicio ||
                fim != orig.dataFim ||
                statusAtual != orig.status
    }

    /*──────────── Progress control (spinner + UX) ───────────*/
    private fun progress(show: Boolean) = with(binding) {
        val saving = show && isSaving
        cronRegScroll.isEnabled = !saving
        btnSaveEtapa.isEnabled = if (saving) false else !shouldCloseAfterSave

        progressSaveEtapa.isVisible = saving

        if (saving) {
            requireActivity().currentFocus?.clearFocus()
            root.clearFocus()
            cronRegScroll.isFocusableInTouchMode = true
            cronRegScroll.requestFocus()
            root.hideKeyboard()
            progressSaveEtapa.post {
                cronRegScroll.smoothScrollTo(0, progressSaveEtapa.bottom)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}