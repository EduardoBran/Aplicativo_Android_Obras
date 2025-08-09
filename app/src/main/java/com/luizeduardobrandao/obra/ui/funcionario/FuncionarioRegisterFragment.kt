package com.luizeduardobrandao.obra.ui.funcionario

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentFuncionarioRegisterBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FuncionarioRegisterFragment : Fragment() {

    private var _binding: FragmentFuncionarioRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: FuncionarioRegisterFragmentArgs by navArgs()
    private val viewModel: FuncionarioViewModel by viewModels()

    private val isEdit get() = args.funcionarioId != null
    private var diasTrabalhados = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFuncionarioRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarFuncReg.setNavigationOnClickListener { findNavController().navigateUp() }

        if (isEdit) prefillFields()

        binding.btnPlus.setOnClickListener { updateDias(+1) }
        binding.btnMinus.setOnClickListener { updateDias(-1) }

        listOf(binding.etNomeFunc, binding.etSalario).forEach { edit ->
            edit.doAfterTextChanged { validateForm() }
        }

        // Escuta mudanças nos checkboxes de função
        getAllFuncaoCheckboxes().forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ -> validateForm() }
        }
        binding.rgPagamento.setOnCheckedChangeListener { _, _ -> validateForm() }

        observeSaveState()

        binding.btnSaveFuncionario.setOnClickListener { onSave() }

        validateForm()
    }

    private fun prefillFields() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeFuncionario(args.obraId, args.funcionarioId!!)
                    .collect { func ->
                        func ?: return@collect
                        binding.apply {
                            etNomeFunc.setText(func.nome)
                            etSalario.setText(func.salario.toString())
                            etPix.setText(func.pix)
                            tvDias.text = func.diasTrabalhados.toString()
                            diasTrabalhados = func.diasTrabalhados
                            btnSaveFuncionario.setText(R.string.generic_update)

                            // Marca múltiplas funções
                            val funcoesMarcadas = func.funcao.split("/").map { it.trim() }
                            getAllFuncaoCheckboxes().forEach { cb ->
                                cb.isChecked = funcoesMarcadas.any {
                                    it.equals(cb.text.toString(), ignoreCase = true)
                                }
                            }

                            // Forma de pagamento e status continuam com RadioButton
                            selectSingleChoice(binding.rgPagamento, func.formaPagamento)
                            selectSingleChoice(binding.rgStatus, func.status)
                        }
                        validateForm()
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

    private fun validateForm(): Boolean = with(binding) {
        // Nome (mínimo de caracteres)
        val nome = etNomeFunc.text?.toString()?.trim().orEmpty()
        val nomeOk = nome.length >= Constants.Validation.MIN_NAME
        tilNomeFunc.error = if (!nomeOk)
            getString(R.string.func_reg_error_nome, Constants.Validation.MIN_NAME)
        else null

        // Salário (> MIN_SALDO)
        val salario = etSalario.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val salarioOk = salario != null && salario > Constants.Validation.MIN_SALDO
        tilSalario.error = if (!salarioOk) getString(R.string.func_reg_error_salario) else null

        // Pelo menos UMA função marcada
        val funcoes = getCheckedFuncaoTexts()
        val funcaoOk = funcoes.isNotEmpty()
        tvFuncaoError.text = if (!funcaoOk) getString(R.string.func_reg_error_role) else null
        tvFuncaoError.visibility = if (!funcaoOk) View.VISIBLE else View.GONE

        // Uma forma de pagamento selecionada
        val pagtoOk = rgPagamento.checkedRadioButtonId != -1
        tvPagamentoError.text = if (!pagtoOk) getString(R.string.func_reg_error_pagamento) else null
        tvPagamentoError.visibility = if (!pagtoOk) View.VISIBLE else View.GONE

        val formOk = nomeOk && salarioOk && funcaoOk && pagtoOk
        btnSaveFuncionario.isEnabled = formOk
        formOk
    }

    private fun observeSaveState() {
        lifecycleScope.launch {
            viewModel.opState.collect { state ->
                when (state) {
                    is UiState.Loading -> binding.btnSaveFuncionario.isEnabled = false
                    is UiState.Success -> findNavController().navigateUp()
                    is UiState.ErrorRes -> {
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

        val funcionario = Funcionario(
            id = args.funcionarioId ?: "",
            nome = binding.etNomeFunc.text.toString().trim(),
            funcao = getCheckedFuncaoTexts().joinToString(" / "),
            salario = binding.etSalario.text.toString().replace(',', '.').toDouble(),
            formaPagamento = getCheckedRadioText(binding.rgPagamento),
            pix = binding.etPix.text.toString().trim(),
            diasTrabalhados = diasTrabalhados,
            status = getCheckedRadioText(binding.rgStatus).lowercase()
        )

        binding.btnSaveFuncionario.isEnabled = false
        binding.funcRegScroll.visibility = View.INVISIBLE

        if (isEdit) {
            viewModel.updateFuncionario(funcionario)
            Toast.makeText(
                requireContext(),
                getString(R.string.func_updated, funcionario.nome),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.addFuncionario(funcionario)
            Toast.makeText(
                requireContext(),
                getString(R.string.func_added, funcionario.nome),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun getCheckedRadioText(group: RadioGroup): String {
        val id = group.checkedRadioButtonId
        if (id == -1) return ""  // segurança, embora o validateForm já garanta seleção
        val rb = group.findViewById<MaterialRadioButton>(id)
        return rb.text.toString()
    }

    private fun getCheckedFuncaoTexts(): List<String> =
        getAllFuncaoCheckboxes().filter { it.isChecked }.map { it.text.toString() }

    private fun getAllFuncaoCheckboxes(): List<MaterialCheckBox> =
        (0 until binding.rgFuncao.childCount).mapNotNull { binding.rgFuncao.getChildAt(it) as? MaterialCheckBox } +
                (0 until binding.rgFuncao2.childCount).mapNotNull { binding.rgFuncao2.getChildAt(it) as? MaterialCheckBox }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}