package com.luizeduardobrandao.obra.ui.cronograma

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Toast
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
import java.util.*
import androidx.activity.addCallback

@AndroidEntryPoint
class CronogramaRegisterFragment : Fragment() {

    private var _binding: FragmentCronogramaRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: CronogramaRegisterFragmentArgs by navArgs()
    private val viewModel: CronogramaViewModel by viewModels()

    /* Se etapaId == null ⇒ inclusão */
    private val isEdit get() = args.etapaId != null
    private var etapaOriginal: Etapa? = null

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
        isLenient = false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
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
            etDataInicioEtapa.setOnClickListener { pickDate(etDataInicioEtapa) }
            etDataFimEtapa.setOnClickListener { pickDate(etDataFimEtapa) }

            // Botão Salvar/Atualizar
            btnSaveEtapa.text = getString(
                if (isEdit) R.string.generic_update else R.string.generic_save
            )
            btnSaveEtapa.isEnabled = false // começa desabilitado
            btnSaveEtapa.setOnClickListener { onSaveClicked() }

            // Habilitar/desabilitar botão conforme preenchimento
            setupValidation()
        }
        // Intercepta o botão físico/gesto de voltar
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPress()
        }

        // Se for edição, garanta que os dados estejam carregados e preencha
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
        // Atualiza estado do botão após preencher
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
            // garante que o erro esteja visível
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

        binding.btnSaveEtapa.isEnabled = false
        binding.btnSaveEtapa.text = getString(R.string.generic_saving)
        requireView().hideKeyboard()

        if (isEdit) viewModel.updateEtapa(etapa) else viewModel.addEtapa(etapa)

        Toast.makeText(
            requireContext(),
            getString(if (isEdit) R.string.etapa_update_success else R.string.etapa_add_success),
            Toast.LENGTH_SHORT
        ).show()
        findNavController().navigateUp()
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

        // Valida imediatamente (útil em modo edição)
        validateForm()
    }

    private fun validateForm() = with(binding) {
        // 1) Título (obrigatório)
        val titulo = etTituloEtapa.text?.toString()?.trim().orEmpty()
        val tituloOk = titulo.isNotEmpty()
        tilTituloEtapa.error =
            if (!tituloOk) getString(R.string.etapa_error_title_required) else null

        // 2) Datas (obrigatórias)
        val iniStr = etDataInicioEtapa.text?.toString().orEmpty()
        val fimStr = etDataFimEtapa.text?.toString().orEmpty()

        val iniOk = iniStr.isNotBlank()
        val fimOk = fimStr.isNotBlank()

        tilDataInicioEtapa.error =
            if (!iniOk) getString(R.string.etapa_error_date_start_required) else null
        tilDataFimEtapa.error =
            if (!fimOk) getString(R.string.etapa_error_date_end_required) else null

        // 3) Ordem das datas (somente quando ambas preenchidas): fim >= início
        val ordemOk = if (iniOk && fimOk) isDateOrderValid(iniStr, fimStr) else false
        if (iniOk && fimOk && !ordemOk) {
            // mostra erro de ordem no campo de término (vermelho)
            tilDataFimEtapa.error = getString(R.string.etapa_error_date_order)
        }

        // 4) Habilita o botão
        btnSaveEtapa.isEnabled = tituloOk && iniOk && fimOk && ordemOk
    }

    /*──────────── Date picker util ───────────*/
    private fun pickDate(target: View) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                (target as? android.widget.EditText)
                    ?.setText(
                        String.format(
                            Locale.getDefault(),
                            "%02d/%02d/%04d",
                            day,
                            month + 1,
                            year
                        )
                    )
                validateForm() // revalida após escolher a data
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun parseDateOrNull(s: String?): Date? = try {
        if (s.isNullOrBlank()) null else sdf.parse(s)
    } catch (_: ParseException) {
        null
    }

    /** true se as duas datas existem e dataFim >= dataInicio; false caso contrário */
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
                btnText = getString(R.string.snack_button_yes), // SIM
                onAction = { findNavController().navigateUp() },
                btnNegativeText = getString(R.string.snack_button_no), // NÃO
                onNegative = { /* permanece nesta tela */ }
            )
        } else {
            findNavController().navigateUp()
        }
    }

    /** Verifica se existem alterações não salvas no formulário. */
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
            // Inclusão: compara com “vazio” e status default (PENDENTE)
            val statusDefault = CronogramaPagerAdapter.STATUS_PENDENTE
            return@with titulo.isNotEmpty() ||
                    desc.isNotEmpty() ||
                    func.isNotEmpty() ||
                    ini.isNotEmpty() ||
                    fim.isNotEmpty() ||
                    statusAtual != statusDefault
        }

        // Edição: compara com a etapa original
        val orig = etapaOriginal ?: return@with false
        return@with titulo != orig.titulo ||
                desc != orig.descricao ||
                func != orig.funcionarios ||
                ini != orig.dataInicio ||
                fim != orig.dataFim ||
                statusAtual != orig.status
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}