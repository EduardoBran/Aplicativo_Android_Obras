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
    private val viewModel: FuncionarioViewModel by viewModels({ requireParentFragment() })

    private val isEdit get() = args.funcionarioId != null

    private var diasTrabalhados = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentFuncionarioRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar
        binding.toolbarFuncReg.setNavigationOnClickListener { findNavController().navigateUp() }


        // Pré-carrega dados se edição
        if (isEdit) prefillFields()


        // Stepper + / − dias
        binding.btnPlus.setOnClickListener { updateDias(+1) }
        binding.btnMinus.setOnClickListener { updateDias(-1) }


        // Habilita botão quando nome ≥ 3 && salário > 0
        listOf(binding.etNomeFunc, binding.etSalario).forEach { edit ->
            edit.doAfterTextChanged { validateForm() }
        }

        observeSaveState()

        // Submissão
        binding.btnSaveFuncionario.setOnClickListener { onSave() }
    }

    /*------------------------------------------------*/
    /* Prefill em modo edição                         */
    /*------------------------------------------------*/
    private fun prefillFields() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                viewModel.observeFuncionario(args.obraId, args.funcionarioId!!).collect { func ->

                    func ?: return@collect
                    binding.etNomeFunc.setText(func.nome)
                    selectRadio(binding.rgFuncao, func.funcao)
                    binding.etSalario.setText(func.salario.toString())
                    selectRadio(binding.rgPagamento, func.formaPagamento)
                    binding.etPix.setText(func.pix)
                    diasTrabalhados = func.diasTrabalhados
                    binding.tvDias.text = diasTrabalhados.toString()
                    selectRadio(binding.rgStatus, func.status)
                    binding.btnSaveFuncionario.setText(R.string.generic_update)
                    validateForm()
                }
            }
        }
    }

    // Marca o RadioButton cujo texto coincide com [text].
    private fun selectRadio(group: RadioGroup, text: String) {
        for (i in 0 until group.childCount) {
            // rb é MaterialRadioButton? aqui
            val rb = group.getChildAt(i) as? MaterialRadioButton
            // primeiro checamos se não é nulo e o texto bate
            if (rb != null && rb.text.toString().equals(text, ignoreCase = true)) {
                rb.isChecked = true
                return
            }
        }
    }


    /*------------------------------------------------*/
    // Exibe Snackbars de erro e só habilita o salvar se tudo válido.
    /*------------------------------------------------*/
    private fun validateForm(){
        // 1) Nome
        val nome = binding.etNomeFunc.text.toString().trim()
        if (nome.length < Constants.Validation.MIN_NAME) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.func_error_nome),
                getString(R.string.snack_button_ok)
            )
            binding.btnSaveFuncionario.isEnabled = false
            return
        }
        // 2) Salário
        val salario = binding.etSalario.text.toString().toDoubleOrNull()
        if (salario == null || salario <= Constants.Validation.MIN_SALDO) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.func_error_salario),
                getString(R.string.snack_button_ok)
            )
            binding.btnSaveFuncionario.isEnabled = false
            return
        }
        // 3) Função
        if (binding.rgFuncao.checkedRadioButtonId == -1) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.func_error_funcao),
                getString(R.string.snack_button_ok)
            )
            binding.btnSaveFuncionario.isEnabled = false
            return
        }
        // 4) Pagamento
        if (binding.rgPagamento.checkedRadioButtonId == -1) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.func_error_pagamento),
                getString(R.string.snack_button_ok)
            )
            binding.btnSaveFuncionario.isEnabled = false
            return
        }
        // tudo ok
        binding.btnSaveFuncionario.isEnabled = true
    }

    // Observa o estado de criação/atualização no ViewModel.
    private fun observeSaveState() {
        lifecycleScope.launch {
            viewModel.opState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.btnSaveFuncionario.isEnabled = false
                    }
                    is UiState.Success -> {
                        findNavController().navigateUp()
                    }
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


    /*------------------------------------------------*/
    /* Stepper                                        */
    /*------------------------------------------------*/
    private fun updateDias(delta: Int) {
        diasTrabalhados = (diasTrabalhados + delta).coerceAtLeast(0)
        binding.tvDias.text = diasTrabalhados.toString()
    }


    /*------------------------------------------------*/
    /* Salvar / Atualizar                             */
    /*------------------------------------------------*/
    private fun onSave() {
        // esconde o teclado
        binding.root.hideKeyboard()

        val nome = binding.etNomeFunc.text.toString().trim()
        val funcao = getCheckedText(binding.rgFuncao)
        val sal = binding.etSalario.text.toString().toDoubleOrNull() ?: 0.0
        val forma = getCheckedText(binding.rgPagamento)
        val pix = binding.etPix.text.toString().trim()
        val stat  = getCheckedText(binding.rgStatus)

        val funcionario = Funcionario(
            id = args.funcionarioId ?: "", // será setado no repo se novo
            nome = nome,
            funcao = funcao,
            salario = sal,
            formaPagamento  = forma,
            pix = pix,
            diasTrabalhados = diasTrabalhados,
            status = stat
        )

        // Progress UI
        binding.btnSaveFuncionario.isEnabled = false
        binding.funcRegScroll.visibility = View.INVISIBLE

        // chama direto — o ViewModel cuidará de lançar no I/O dispatcher
        if (isEdit) {
            viewModel.updateFuncionario(funcionario)
            Toast.makeText(
                requireContext(),
                getString(R.string.func_toast_updated),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.addFuncionario(funcionario)
            Toast.makeText(
                requireContext(),
                getString(R.string.func_toast_added),
                Toast.LENGTH_SHORT
            ).show()
        }

        // volta
        findNavController().navigateUp()
    }

    private fun getCheckedText(group: RadioGroup): String =
        group.findViewById<MaterialRadioButton>(
            group.checkedRadioButtonId
        ).text.toString()


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}