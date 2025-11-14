package com.luizeduardobrandao.obra.ui.material

import android.os.Bundle
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.radiobutton.MaterialRadioButton
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Material
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentMaterialRegisterBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.applyFullWidthButtonSizingGrowShrink
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MaterialRegisterFragment : Fragment() {

    private var _binding: FragmentMaterialRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: MaterialRegisterFragmentArgs by navArgs()
    private val viewModel: MaterialViewModel by viewModels()

    private val isEdit get() = args.materialId != null
    private var quantidade = 1

    private var materialOriginal: Material? = null

    // Controle de loading/navegação
    private var isSaving = false
    private var shouldCloseAfterSave = false

    // Anti-flash e watchers
    private var dataLoaded = false
    private var watchersSet = false
    private var nomeWatcher: TextWatcher? = null

    // Guarda o estado anterior para animar apenas quando mudar
    private var lastNomeErrorVisible: Boolean? = null

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
                if (isEdit) R.string.generic_update else R.string.generic_add
            )
            btnSaveMaterial.isEnabled = false

            // Responsividade do botão (mesmo padrão do cronograma)
            btnSaveMaterial.doOnPreDraw {
                btnSaveMaterial.applyFullWidthButtonSizingGrowShrink()
            }

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
                // Modo criação: evita flash
                dataLoaded = true
                setupValidationWatchersOnce()

                quantidade = 1
                tvQuantidade.text = quantidade.toString()

                // Validação inicial
                validateForm()

                // Ajuste inicial do espaçamento (sem animação) — erro externo
                root.post {
                    adjustSpacingAfterView(
                        tvNomeMaterialError,
                        tilDescMaterial,
                        visibleTopDp = 8,
                        goneTopDp = 12,
                        animate = false
                    )
                }
            } else {
                // Modo edição: só valida e instala watchers após preencher os dados
                prefillFields()
            }

            // Quantidade – / +
            btnPlusQtd.setOnClickListener { updateQuantidade(+1) }
            btnMinusQtd.setOnClickListener { updateQuantidade(-1) }

            // Observa opState e navegação/feedback
            collectOperationState()

            // Clique salvar
            btnSaveMaterial.setOnClickListener { onSave() }

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

                        btnSaveMaterial.text = getString(R.string.generic_update)
                    }
                    // Anti-flash: só agora liberamos validação + watchers
                    dataLoaded = true
                    setupValidationWatchersOnce()

                    // Validação inicial
                    validateForm()

                    // Ajuste inicial do espaçamento (sem animação) — erro externo
                    binding.root.post {
                        adjustSpacingAfterView(
                            binding.tvNomeMaterialError,
                            binding.tilDescMaterial,
                            animate = false
                        )
                    }
                }
            }
        }
    }

    private fun setupValidationWatchersOnce(): TextWatcher {
        if (watchersSet && nomeWatcher != null) return nomeWatcher!!

        watchersSet = true
        // usa o KTX que retorna TextWatcher
        nomeWatcher = binding.etNomeMaterial.addTextChangedListener(
            afterTextChanged = {
                if (dataLoaded) validateForm()
            }
        )
        return nomeWatcher!!
    }

    /*──────────────────── Validação ────────────────────*/
    private fun validateForm(): Boolean = with(binding) {
        // Anti-flash: não valide antes de os dados estarem carregados
        if (!dataLoaded) {
            btnSaveMaterial.isEnabled = false
            clearErrors()
            return false
        }

        val nome = etNomeMaterial.text?.toString()?.trim().orEmpty()
        val nomeOk = nome.length >= 2

        // Usar TextView externo (sem caption do TIL)
        tvNomeMaterialError.isVisible = !nomeOk
        if (!nomeOk) {
            // Reaproveita o hint amigável
            tvNomeMaterialError.text = getString(R.string.material_hint_name)
        }
        val changed = lastNomeErrorVisible != tvNomeMaterialError.isVisible
        lastNomeErrorVisible = tvNomeMaterialError.isVisible

        // Ajusta a margem-top da Descrição conforme erro visível/oculto
        adjustSpacingAfterView(
            tvNomeMaterialError,
            tilDescMaterial,
            animate = changed
        )

        // Garante que não usemos o caption interno do TIL
        tilNomeMaterial.error = null

        // Habilita botão (exceto durante salvamento/fechamento)
        if (!isSaving && !shouldCloseAfterSave) {
            btnSaveMaterial.isEnabled = nomeOk
        }
        return nomeOk
    }


    /*──────────────────── Salvar ────────────────────*/
    private fun onSave() {
        if (!validateForm()) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.generic_error),
                getString(R.string.material_save_error),
                getString(R.string.generic_ok_upper_case)
            )
            return
        }

        val nome = binding.etNomeMaterial.text.toString().trim()
        if (viewModel.isDuplicateName(nome, args.materialId)) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.generic_error),
                getString(R.string.material_duplicate_name),
                getString(R.string.generic_ok_upper_case)
            )
            return
        }

        val status = getCheckedStatus()
        val material = Material(
            id = args.materialId ?: "",
            nome = nome,
            descricao = binding.etDescMaterial.text?.toString()?.trim().orEmpty().ifBlank { null },
            quantidade = quantidade,
            status = status
        )

        // Ativa o fluxo de loading como Nota/Cronograma/Funcionário
        shouldCloseAfterSave = true
        isSaving = true
        progress(true)

        // Dispara operação; Toast + navegação ficam no collectOperationState()
        if (isEdit) viewModel.updateMaterial(material) else viewModel.addMaterial(material)
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

    private fun collectOperationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.opState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            // Só mostra spinner quando a ação partiu do botão (isSaving)
                            if (isSaving) progress(true)
                        }

                        is UiState.Success -> {
                            isSaving = false
                            if (shouldCloseAfterSave) {
                                val msgRes = if (isEdit)
                                    R.string.material_update_success
                                else
                                    R.string.material_save_success
                                Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()

                                // navega no próximo frame pra garantir que o spinner apareça
                                binding.root.post {
                                    findNavController().navigateUp()
                                    shouldCloseAfterSave = false
                                }
                            } else {
                                // operações que não fecham a tela
                                progress(false)
                                binding.btnSaveMaterial.isEnabled = true
                            }
                        }

                        is UiState.ErrorRes -> {
                            // erro: some loading e reabilita UI
                            progress(false)
                            isSaving = false
                            shouldCloseAfterSave = false
                            binding.btnSaveMaterial.isEnabled = true
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.generic_error),
                                getString(state.resId),
                                getString(R.string.generic_ok_upper_case)
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    // ---------------- Verificação de Edição -----------------

    private fun handleBackPress() {
        if (hasUnsavedChanges()) {
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.generic_attention),
                msg = getString(R.string.generic_unsaved_confirm_msg),
                btnText = getString(R.string.generic_yes_upper_case), // SIM
                onAction = { findNavController().navigateUp() },
                btnNegativeText = getString(R.string.generic_no_upper_case), // NÃO
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

    private fun progress(show: Boolean) = with(binding) {
        val saving = show && isSaving
        materialRegScroll.isEnabled = !saving
        btnSaveMaterial.isEnabled = if (saving) false else !shouldCloseAfterSave

        // Mostra o indicador logo abaixo do botão
        progressSaveMaterial.visibility = if (saving) View.VISIBLE else View.GONE

        if (saving) {
            // 1) Limpa foco para evitar auto-scroll do sistema
            requireActivity().currentFocus?.clearFocus()
            root.clearFocus()

            // 2) Segura o foco no container não editável
            materialRegScroll.isFocusableInTouchMode = true
            materialRegScroll.requestFocus()

            // 3) Fecha o teclado
            root.hideKeyboard()

            // 4) Rola até o spinner para garantir visibilidade
            progressSaveMaterial.post {
                materialRegScroll.smoothScrollTo(0, progressSaveMaterial.bottom)
            }
        }
    }

    // ------------- Helpers de espaçamento -------------

    // dp helper
    private fun Int.dp(): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()

    /** Ajusta a margem-top de nextView conforme a visibilidade de precedingView (erro externo). */
    private fun adjustSpacingAfterView(
        precedingView: View,
        nextView: View,
        visibleTopDp: Int = 8,
        goneTopDp: Int = 12,
        animate: Boolean = true
    ) {
        val parent = nextView.parent as? ViewGroup ?: return

        // encerra transições pendentes para evitar "pulos"
        TransitionManager.endTransitions(parent)

        if (animate) {
            TransitionManager.beginDelayedTransition(
                parent,
                AutoTransition().apply { duration = 150 }
            )
        }

        (nextView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            val newTop = if (precedingView.isVisible) visibleTopDp.dp() else goneTopDp.dp()
            if (lp.topMargin != newTop) {
                lp.topMargin = newTop
                nextView.layoutParams = lp
                parent.requestLayout()
                nextView.requestLayout()
            }
        }
    }

    /** Limpa erros (mantém o padrão anti-flash idêntico ao cronograma). */
    private fun clearErrors() = with(binding) {
        tilNomeMaterial.error = null // não usamos caption interno
        tvNomeMaterialError.isVisible = false
    }


    /*──────────────────── Lifecycle ────────────────────*/
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}