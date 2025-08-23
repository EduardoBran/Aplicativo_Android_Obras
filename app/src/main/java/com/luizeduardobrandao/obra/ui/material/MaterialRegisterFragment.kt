package com.luizeduardobrandao.obra.ui.material

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
import com.google.android.material.radiobutton.MaterialRadioButton
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Material
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentMaterialRegisterBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.activity.addCallback

@AndroidEntryPoint
class MaterialRegisterFragment : Fragment() {

    private var _binding: FragmentMaterialRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: MaterialRegisterFragmentArgs by navArgs()
    private val viewModel: MaterialViewModel by viewModels()

    private val isEdit get() = args.materialId != null
    private var quantidade = 1

    private var materialOriginal: Material? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMaterialRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            // Toolbar
            toolbarMaterialReg.setNavigationOnClickListener { handleBackPress() }
            toolbarMaterialReg.title = getString(
                if (isEdit) R.string.material_edit_title else R.string.material_register_title
            )

            // Botão salvar / atualizar
            btnSaveMaterial.text = getString(
                if (isEdit) R.string.material_reg_button_edit else R.string.material_reg_button_save
            )
            btnSaveMaterial.isEnabled = false

            // Habilita salvar quando a lista carregar (e o form estiver ok)
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.state.collect { s ->
                        if (s is UiState.Success) {
                            btnSaveMaterial.isEnabled = validateForm()
                        }
                    }
                }
            }

            if (!isEdit) {
                quantidade = 1
                tvQuantidade.text = quantidade.toString()
            }

            // Quantidade – / +
            btnPlusQtd.setOnClickListener { updateQuantidade(+1) }
            btnMinusQtd.setOnClickListener { updateQuantidade(-1) }

            // Validação dinâmica do nome
            etNomeMaterial.doAfterTextChanged { validateForm() }

            // Observa opState e, se edição, faz prefill
            observeOpState()
            if (isEdit) prefillFields()

            // Clique salvar
            btnSaveMaterial.setOnClickListener { onSave() }

            // valida inicial
            validateForm()

            // Interceptar o botão físico/gesto de voltar
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                handleBackPress()
            }
        }
    }

    /*──────────────────── Prefill (edição) ────────────────────*/
    private fun prefillFields() {
        val materialId = args.materialId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeMaterial(materialId).collect { mat ->
                    mat ?: return@collect
                    materialOriginal = mat
                    binding.apply {
                        etNomeMaterial.setText(mat.nome)
                        etDescMaterial.setText(mat.descricao.orEmpty())
                        quantidade = mat.quantidade.coerceAtLeast(0)
                        tvQuantidade.text = quantidade.toString()

                        if (mat.status.equals(
                                getString(R.string.material_status_active),
                                ignoreCase = true
                            )
                            || mat.status.equals("Ativo", ignoreCase = true)
                        ) {
                            rbStatusAtivoMat.isChecked = true
                        } else {
                            rbStatusInativoMat.isChecked = true
                        }

                        btnSaveMaterial.text = getString(R.string.material_reg_button_edit)
                    }
                    validateForm()
                }
            }
        }
    }

    /*──────────────────── Validação ────────────────────*/
    private fun validateForm(): Boolean = with(binding) {
        val nome = etNomeMaterial.text?.toString()?.trim().orEmpty()
        val nomeOk = nome.length >= Constants.Validation.MIN_NAME

        // erro inline (campo obrigatório)
        tilNomeMaterial.error = if (!nomeOk)
        // Reaproveita o hint como mensagem amigável de preenchimento
            getString(R.string.material_hint_name)
        else null

        // Habilita botão
        btnSaveMaterial.isEnabled = nomeOk
        nomeOk
    }

    /*──────────────────── Salvar ────────────────────*/
    private fun onSave() {
        if (!validateForm()) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.material_save_error),
                getString(R.string.snack_button_ok)
            )
            return
        }

        val nome = binding.etNomeMaterial.text.toString().trim()
        if (viewModel.isDuplicateName(nome, args.materialId)) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.material_duplicate_name),
                getString(R.string.snack_button_ok)
            )
            return
        }

        val status = getCheckedStatus()
        val material = Material(
            id = args.materialId ?: "",
            nome = binding.etNomeMaterial.text.toString().trim(),
            descricao = binding.etDescMaterial.text?.toString()?.trim().orEmpty().ifBlank { null },
            quantidade = quantidade,
            status = status
        )

        // UI feedback de ação
        binding.btnSaveMaterial.isEnabled = false
        binding.materialRegScroll.visibility = View.INVISIBLE
        requireView().hideKeyboard()

        if (isEdit) {
            viewModel.updateMaterial(material)
            Toast.makeText(
                requireContext(),
                getString(R.string.material_update_success),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.addMaterial(material)
            Toast.makeText(
                requireContext(),
                getString(R.string.material_save_success),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /*──────────────────── Helpers ────────────────────*/
    private fun updateQuantidade(delta: Int) {
        quantidade = (quantidade + delta).coerceAtLeast(1)
        binding.tvQuantidade.text = quantidade.toString()
    }

    private fun getCheckedStatus(): String {
        val id = binding.rgStatusMaterial.checkedRadioButtonId
        val rb = binding.root.findViewById<MaterialRadioButton>(id)
        return rb?.text?.toString() ?: getString(R.string.material_status_active)
    }

    private fun observeOpState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.opState.collect { state ->
                when (state) {
                    is UiState.Loading -> binding.btnSaveMaterial.isEnabled = false
                    is UiState.Success -> findNavController().navigateUp()
                    is UiState.ErrorRes -> {
                        binding.btnSaveMaterial.isEnabled = true
                        binding.materialRegScroll.visibility = View.VISIBLE
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
        val nomeAtual = etNomeMaterial.text?.toString()?.trim().orEmpty()
        val descAtual = etDescMaterial.text?.toString()?.trim().orEmpty()
        val statusAtual = getCheckedStatus()
        val qtdAtual = quantidade

        if (!isEdit) {
            // Cadastro novo: compara com estado "vazio"/padrão (qtd = 1, status = Ativo)
            val statusPadrao = getString(R.string.material_status_active)
            return@with nomeAtual.isNotEmpty() ||
                    descAtual.isNotEmpty() ||
                    qtdAtual != 1 ||
                    !statusAtual.equals(statusPadrao, ignoreCase = true)
        }

        // Edição: compara com o material original
        val orig = materialOriginal ?: return@with false
        val descOrig = orig.descricao?.trim().orEmpty()

        return@with nomeAtual != orig.nome ||
                descAtual != descOrig ||
                !statusAtual.equals(orig.status, ignoreCase = true) ||
                qtdAtual != orig.quantidade
    }

    /*──────────────────── Lifecycle ────────────────────*/
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}