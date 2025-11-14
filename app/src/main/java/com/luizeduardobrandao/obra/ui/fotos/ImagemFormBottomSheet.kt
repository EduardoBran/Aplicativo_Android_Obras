package com.luizeduardobrandao.obra.ui.fotos

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Imagem
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.BottomsheetImagemFormBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.applyResponsiveButtonSizingGrowShrink
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrToday
import com.luizeduardobrandao.obra.utils.syncTextSizesGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ImagemFormBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetImagemFormBinding? = null
    private val binding get() = _binding!!

    // ✅ mesma instância do FotosFragment + mesmo SavedStateHandle (obraId) e mesma factory Hilt
    private val viewModel: FotosViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = {
            (requireParentFragment() as androidx.lifecycle.HasDefaultViewModelProviderFactory)
                .defaultViewModelProviderFactory
        }
    )

    private var saving = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetImagemFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)
        setupForm()
    }

    private fun setupForm() = with(binding) {

        // Tipos padrão
        if (rgTipos.checkedRadioButtonId == View.NO_ID) {
            rgTipos.check(R.id.rbPintura)
        }

        // Data padrão (hoje)
        if (etDataImagem.text.isNullOrBlank()) {
            etDataImagem.setText(todayPtBr())
        }

        etDataImagem.setOnClickListener {
            showMaterialDatePickerBrToday { chosen ->
                etDataImagem.setText(chosen)
                validateForm()
            }
        }

        // --- LISTENERS ---
        etNomeImagem.doAfterTextChanged { validateForm() }
        rgTipos.setOnCheckedChangeListener { _, _ -> validateForm() }

        btnSalvarImagem.setOnClickListener {
            if (!validateForm()) return@setOnClickListener
            confirmAndSave()
        }

        btnCancelarImagem.setOnClickListener { dismissAllowingStateLoss() }

        // ── Responsividade dos botões "Salvar" / "Voltar" (lado a lado)
        root.doOnPreDraw {
            btnSalvarImagem.applyResponsiveButtonSizingGrowShrink()
            btnCancelarImagem.applyResponsiveButtonSizingGrowShrink()
            // Nivelar os dois pelo menor tamanho final
            root.syncTextSizesGroup(btnSalvarImagem, btnCancelarImagem)
        }

        // Habilita/Desabilita o botão inicialmente
        validateForm()
    }

    /** Regras:
     *  - foto escolhida
     *  - nome obrigatório com pelo menos 3 caracteres
     *  - data no formato dd/MM/yyyy
     *  - algum tipo selecionado
     */
    private fun validateForm(): Boolean = with(binding) {
        val (bytes, mime) = viewModel.consumePendingPhoto()
        val hasPhoto = bytes != null && mime != null

        // --- NOME: erro no EditText (balão preto + ícone vermelho)
        val nome = etNomeImagem.text?.toString()?.trim().orEmpty()
        val nomeOk = nome.length >= 3
        etNomeImagem.error = if (!nomeOk) {
            getString(R.string.imagens_error_nome_min)
        } else null

        // --- DATA: erro no EditText (balão preto + ícone vermelho)
        val dataStr = etDataImagem.text?.toString()?.trim().orEmpty()
        val dataOk = dataStr.matches(Regex("""\d{2}/\d{2}/\d{4}"""))
        etDataImagem.error = if (!dataOk) {
            getString(R.string.imagens_error_data_obrigatoria)
        } else null

        // --- TIPO
        val tipoOk = rgTipos.checkedRadioButtonId != View.NO_ID

        val ok = hasPhoto && nomeOk && dataOk && tipoOk
        if (!saving) btnSalvarImagem.isEnabled = ok
        ok
    }

    private fun confirmAndSave() = with(binding) {
        val (bytes, mime) = viewModel.consumePendingPhoto()
        if (bytes == null || mime == null) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.generic_error),
                getString(R.string.imagens_save_error),
                getString(R.string.generic_ok_upper_case)
            )
            return@with
        }

        showSnackbarFragment(
            Constants.SnackType.WARNING.name,
            getString(R.string.nota_photo_confirm_title),
            getString(R.string.imagem_save_confirm),
            getString(R.string.generic_yes_upper_case),
            onAction = {
                if (saving) return@showSnackbarFragment
                saving = true

                // 🔽 preparar visual para o "loading"
                btnSalvarImagem.isEnabled = false
                btnCancelarImagem.isEnabled = false
                root.hideKeyboard()
                // torna o indicador focalizável para dar destaque e acessibilidade
                progressSalvarImagem.isFocusable = true
                progressSalvarImagem.isFocusableInTouchMode = true
                progressSalvarImagem.isVisible = true
                progressSalvarImagem.bringToFront()
                progressSalvarImagem.requestFocus()
                // (opcional) anúncio de acessibilidade
                progressSalvarImagem.announceForAccessibility(getString(R.string.generic_loading))

                val imagem = Imagem(
                    id = "",
                    nome = etNomeImagem.text!!.trim().toString(),
                    descricao = etDescricaoImagem.text?.trim()?.toString(),
                    tipo = currentTipo(),
                    data = etDataImagem.text!!.toString(),
                    fotoUrl = null,
                    fotoPath = null
                )

                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.addImagem(imagem, bytes, mime).collectLatest { st ->
                        when (st) {
                            is UiState.Loading -> {
                                progressSalvarImagem.isVisible = true
                                btnSalvarImagem.isEnabled = false
                                btnCancelarImagem.isEnabled = false
                            }

                            is UiState.Success -> {
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.imagens_save_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                                viewModel.clearPendingPhoto()
                                dismissAllowingStateLoss()
                            }

                            is UiState.ErrorRes -> {
                                progressSalvarImagem.isVisible = false
                                btnSalvarImagem.isEnabled = true
                                btnCancelarImagem.isEnabled = true
                                saving = false
                                showSnackbarFragment(
                                    Constants.SnackType.ERROR.name,
                                    getString(R.string.generic_error),
                                    getString(st.resId),
                                    getString(R.string.generic_ok_upper_case)
                                )
                            }

                            else -> Unit
                        }
                    }
                }
            },
            btnNegativeText = getString(R.string.generic_no_upper_case),
            onNegative = { /* mantém o sheet aberto */ }
        )
    }

    private fun currentTipo(): String {
        val rbId = binding.rgTipos.checkedRadioButtonId
        val rb = binding.rgTipos.findViewById<RadioButton>(rbId)
        return rb?.text?.toString() ?: getString(R.string.imagens_filter_pintura)
    }

    private fun todayPtBr(): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR"))
        return sdf.format(java.util.Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog ?: return
        val bottomSheet =
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return

        val behavior = BottomSheetBehavior.from(bottomSheet)

        // Abrir expandido e impedir fechar por gesto/toque fora
        behavior.skipCollapsed = true
        behavior.isFitToContents = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false
        dialog.setCanceledOnTouchOutside(false)
        isCancelable = false

        // Landscape: ocupa ~92% da altura
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                height = (resources.displayMetrics.heightPixels * 0.92f).toInt()
            }
            bottomSheet.requestLayout()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    companion object {
        fun show(fm: androidx.fragment.app.FragmentManager) {
            ImagemFormBottomSheet().show(fm, "ImagemFormBottomSheet")
        }
    }
}
