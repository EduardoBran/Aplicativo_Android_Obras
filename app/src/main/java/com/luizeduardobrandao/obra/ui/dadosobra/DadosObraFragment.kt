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

    private var _binding: FragmentDadosObraBinding? = null
    private val binding get() = _binding!!

    private val args: DadosObraFragmentArgs by navArgs()
    private val viewModel: DadosObraViewModel by viewModels()

    private val formatter =
        SimpleDateFormat(Constants.Format.DATE_PATTERN_BR, Locale.getDefault())

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
        setupDatePickers()
        setupListeners()
        observeViewModel()

        // ⬇ valida tudo assim que a tela aparecer
        validateForm()

        // ⬇ revalida toda vez que o usuário muda qualquer um desses campos
        listOf(
            binding.etNomeCliente,
            binding.etEnderecoObra,
            binding.etDataInicioObra,
            binding.etDataFimObra
        ).forEach { edit ->
            edit.doAfterTextChanged { validateForm() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupToolbar() = binding.toolbarDadosObra.setNavigationOnClickListener {
        findNavController().navigateUp()
    }

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
                    val chosen = formatter.format(Date(millis))
                    onResult(chosen)        // preenche o EditText
                    validateForm()          // <-- força nova validação
                }
            }
            .show(childFragmentManager, "DATE_PICKER")
    }

    private fun setupListeners() = with(binding) {
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

        btnAtualizarSaldo.setOnClickListener {
            val valor = etSaldoAjustado.text.toString().toDoubleOrNull()
            if (valor == null) {
                showSnackbarFragment(
                    Constants.SnackType.ERROR.name,
                    getString(R.string.snack_error),
                    getString(R.string.dados_obra_update_balance_error),
                    getString(R.string.snack_button_ok)
                )
            } else {
                viewModel.atualizarSaldoAjustado(valor)
            }
        }


        btnExcluirObra.setOnClickListener {
            showSnackbarFragment(
                Constants.SnackType.WARNING.name,
                getString(R.string.snack_warning),
                getString(R.string.obra_data_snack_delete_msg),
                getString(R.string.obra_data_button_delete)
            ) { viewModel.excluirObra() }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // observa dados da obra
                launch {
                    viewModel.obraState.collect { ui ->
                        when (ui) {
                            is UiState.Loading -> binding.progressDadosObra.visibility =
                                View.VISIBLE

                            is UiState.Success -> {
                                binding.progressDadosObra.visibility = View.GONE
                                populateFields(ui.data)
                            }

                            is UiState.ErrorRes -> {
                                binding.progressDadosObra.visibility = View.GONE
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
                // observa operações (salvar, excluir, saldo)
                launch {
                    viewModel.opState.collect { ui ->
                        when (ui) {
                            is UiState.Loading -> binding.progressDadosObra.visibility =
                                View.VISIBLE

                            is UiState.Success -> {
                                binding.progressDadosObra.visibility = View.GONE
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.obra_data_toast_updated),
                                    Toast.LENGTH_SHORT
                                ).show()
                                findNavController().navigateUp()
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

    private fun populateFields(obra: Obra) = with(binding) {
        toolbarDadosObra.title = getString(R.string.obra_data_title, obra.nomeCliente)
        etNomeCliente.setText(obra.nomeCliente)
        etEnderecoObra.setText(obra.endereco)
        etDescricaoObra.setText(obra.descricao)
        tvSaldoInicialValor.text = getString(R.string.money_mask, obra.saldoInicial)
        etSaldoAjustado.setText(obra.saldoAjustado.toString())
        etDataInicioObra.setText(obra.dataInicio)
        etDataFimObra.setText(obra.dataFim)
    }

    /**
     * Faz todas as validações e, no final,
     * habilita ou desabilita o botão “Salvar”.
     */
    private fun validateForm(): Boolean {
        var isValid = true

        binding.apply {
            tilNomeCliente.error = null
            tilEnderecoObra.error = null
            tilDataInicioObra.error = null
            tilDataFimObra.error = null

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

            // ⬇ aqui você liga/desliga de verdade o botão
            btnSalvarObra.isEnabled = isValid
        }

        return isValid
    }
}