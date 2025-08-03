package com.luizeduardobrandao.obra.ui.dadosobra

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
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
import java.util.*

/**
 * Fragmento responsável por **editar** ou **excluir** os dados da obra.
 *
 * Padrão seguido:
 *  • ViewBinding
 *  • navArgs para recuperar [obraId]
 *  • ViewModel + StateFlow para UI-state
 *  • repeatOnLifecycle(Lifecycle.State.STARTED)
 */

@AndroidEntryPoint
class DadosObraFragment : Fragment() {

    /*───────────────────────────────  Setup Básico  ────────────────────────────────*/

    private var _binding: FragmentDadosObraBinding? = null
    private val binding get() = _binding!!

    private val args: DadosObraFragmentArgs by navArgs()
    private val viewModel: DadosObraViewModel by viewModels()

    /*───────────────────────────────  Ciclo de Vida  ───────────────────────────────*/

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDadosObraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupDatePickers()
        setupListeners()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /*───────────────────────────────  Toolbar  ───────────────────────────────*/

    private fun setupToolbar() = with(binding.toolbarDadosObra) {
        // título definitivo virá via coleta da Obra
        setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private val formatter =
        SimpleDateFormat(Constants.Format.DATE_PATTERN_BR, Locale.getDefault())

    private fun setupDatePickers() = with(binding) {
        etDataInicioObra.setOnClickListener { showDatePicker { etDataInicioObra.setText(it) } }
        etDataFimObra.setOnClickListener { showDatePicker { etDataFimObra.setText(it) } }
    }

    private fun showDatePicker(onResult: (String) -> Unit) {
        MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.date_picker_title))
            .build()
            .apply {
                addOnPositiveButtonClickListener { millis ->
                    onResult(formatter.format(Date(millis)))
                }
            }
            .show(childFragmentManager, "DATE_PICKER")
    }

    /*───────────────────────────────  Listeners  ───────────────────────────────*/
    private fun setupListeners() = with(binding) {

        // Botão Save
        btnSalvarObra.setOnClickListener {
            if (validateForm()) {
                it.hideKeyboard()
                viewModel.salvarObra(
                    nome = etNomeCliente.text.toString(),
                    endereco = etEnderecoObra.text.toString(),
                    descricao = etDescricaoObra.text.toString(),
                    dataInicio = etDataInicioObra.text.toString(),
                    dataFim = etDataFimObra.text.toString()
                )
            }
        }

        // Botão Atualizar SaldoAjustado
        btnAtualizarSaldo.setOnClickListener {
            val valor = etSaldoAjustado.text.toString().toDoubleOrNull()
            if (valor == null) {
                showSnackbarFragment(
                    Constants.SnackType.ERROR.name,
                    getString(R.string.snack_error),
                    getString(R.string.dados_obra_update_balance_error),
                    getString(R.string.snack_button_ok)
                )
            } else viewModel.atualizarSaldoAjustado(valor)
        }

        // Botão Excluir
        btnExcluirObra.setOnClickListener {
            showSnackbarFragment(
                Constants.SnackType.WARNING.name,
                getString(R.string.snack_warning),
                getString(R.string.obra_data_snack_delete_msg),
                getString(R.string.generic_delete)
            ) { viewModel.excluirObra() }
        }

        /*‒‒‒ máscara simples para moeda em tempo real no saldoAjustado ‒‒‒*/
        etSaldoAjustado.addTextChangedListener(afterTextChanged = { text ->
            if (text.toString().startsWith(".")) {
                // usa string com placeholder em res/values/strings.xml
                val formatted = requireContext().getString(
                    R.string.saldo_ajustado_prefix,
                    text.toString()
                )
                etSaldoAjustado.setText(formatted)
            }
        })
    }

    /*───────────────────────────────  Observers  ───────────────────────────────*/

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                /*――― 1) Obra em tempo real ―――*/
                launch {
                    viewModel.obraState.collect { ui ->
                        when (ui) {
                            is UiState.Loading -> binding.progressDadosObra.visibility = View.VISIBLE
                            is UiState.Success -> populateFields(ui.data)
                            is UiState.ErrorRes -> showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.snack_error),
                                getString(ui.resId),
                                getString(R.string.snack_button_ok)
                            )
                            else -> Unit
                        }
                    }
                }

                /*――― 2) Operações (save / update saldo / delete) ―――*/
                launch {
                    viewModel.opState.collect { ui ->
                        when (ui) {
                            is UiState.Loading -> binding.progressDadosObra.visibility = View.VISIBLE

                            is UiState.Success -> {
                                binding.progressDadosObra.visibility = View.GONE
                                when {
                                    // heurística simples: se estamos deletando voltamos
                                    binding.btnExcluirObra.isPressed -> {
                                        Toast.makeText(
                                            requireContext(),
                                            getString(R.string.obra_data_toast_deleted),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        findNavController().navigateUp()
                                    }

                                    binding.btnSalvarObra.isPressed -> Toast.makeText(
                                        requireContext(),
                                        getString(R.string.obra_data_toast_updated),
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    binding.btnAtualizarSaldo.isPressed -> Toast.makeText(
                                        requireContext(),
                                        getString(R.string.dados_obra_balance_updated),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                viewModel.resetOpState()
                            }

                            is UiState.ErrorRes -> {
                                binding.progressDadosObra.visibility = View.GONE
                                showSnackbarFragment(
                                    Constants.SnackType.ERROR.name,
                                    getString(R.string.snack_error),
                                    getString(ui.resId),
                                    getString(R.string.snack_button_ok)
                                )
                                viewModel.resetOpState()
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    /*───────────────────────────────  Helpers  ───────────────────────────────*/

    /** Preenche todos os campos da tela com os dados da obra */
    private fun populateFields(obra: Obra) = with(binding) {
        progressDadosObra.visibility = View.GONE

        toolbarDadosObra.title =
            getString(R.string.obra_data_title, obra.nomeCliente)

        etNomeCliente.setText(obra.nomeCliente)
        etEnderecoObra.setText(obra.endereco)
        etDescricaoObra.setText(obra.descricao)
        tvSaldoInicialValor.text =
            getString(R.string.money_mask, obra.saldoInicial)
        etSaldoAjustado.setText(obra.saldoAjustado.toString())
        etDataInicioObra.setText(obra.dataInicio)
        etDataFimObra.setText(obra.dataFim)
    }

    /** Validação simples — retorna *true* se o formulário está ok */
    private fun validateForm(): Boolean {
        var isValid = true

        binding.apply {
            // 1) limpa erros antigos
            tilNomeCliente.error = null
            tilEnderecoObra.error = null
            tilDataInicioObra.error = null
            tilDataFimObra.error = null

            // 2) valida Nome do Cliente
            val nomeText = etNomeCliente.text?.toString()?.trim().orEmpty()
            if (nomeText.length < Constants.Validation.MIN_NAME) {
                tilNomeCliente.error = getString(R.string.dados_obra_name_error)
                isValid = false
            }

            // 3) valida Endereço
            if (etEnderecoObra.text.isNullOrBlank()) {
                tilEnderecoObra.error = getString(R.string.dados_obra_address_error)
                isValid = false
            }

            // 4) valida Data Início
            if (etDataInicioObra.text.isNullOrBlank()) {
                tilDataInicioObra.error = getString(R.string.dados_obra_date_start_error)
                isValid = false
            }

            // 5) valida Data Término
            if (etDataFimObra.text.isNullOrBlank()) {
                tilDataFimObra.error = getString(R.string.dados_obra_date_end_error)
                isValid = false
            }
        }

        return isValid
    }
}