package com.luizeduardobrandao.obra.ui.notas

import android.Manifest
import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentNotaRegisterBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.notas.adapter.NotaPagerAdapter
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.core.widget.doAfterTextChanged
import java.util.*
import java.text.NumberFormat
import androidx.activity.addCallback

@AndroidEntryPoint
class NotaRegisterFragment : Fragment(), DatePickerDialog.OnDateSetListener {

    private var _binding: FragmentNotaRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: NotaRegisterFragmentArgs by navArgs()
    private val viewModel: NotasViewModel by viewModels()

    private var isEdit = false
    private lateinit var notaOriginal: Nota   // usado em edição

    private val calendar = Calendar.getInstance()

    // ─────────────── Foto (galeria/câmera) ───────────────
    private var tempCameraUri: Uri? = null

    // controla exclusivamente o loading do botão (salvar/alterar)
    private var isSaving = false

    // Fecha a tela apenas quando o sucesso vier de um salvamento (criar/atualizar)
    private var shouldCloseAfterSave = false

    // Pré-visualização local (antes de salvar a nota)
    private var localPreviewBytes: ByteArray? = null
    private var localPreviewMine: String? = null

    private val hasLocalPreview get() = localPreviewBytes != null
    private val hasRemotePhoto
        get() =
            isEdit && ::notaOriginal.isInitialized && notaOriginal.fotoUrl != null

    // Launcher: abrir seletor de imagens (galeria/arquivos)
    private val openImagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        // Lê bites + MINE
        val bytes = FileUtils.readBytes(requireContext(), uri)
        val mine = FileUtils.detectMime(requireContext(), uri)

        // Confirma com snackbarfragment
        confirmAddPhoto(bytes, mine)
    }

    // Launcher: câmera (grava na Uri temporária do FileProvider)
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) return@registerForActivityResult

        val uri = tempCameraUri ?: return@registerForActivityResult
        val bytes = FileUtils.readBytes(requireContext(), uri)
        val mine = FileUtils.detectMime(requireContext(), uri)

        confirmAddPhoto(bytes, mine)
    }

    // (Opcional) Permissão de câmera quando necessário (SDK 23+)
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCameraCapture() else {
            showSnackbarFragment(
                type = Constants.SnackType.ERROR.name,
                title = getString(R.string.snack_error),
                msg = getString(R.string.nota_photo_error_permission_camera),
                btnText = getString(R.string.snack_button_ok)
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotaRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    /* ───────────────────────── lifecycle ───────────────────────── */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            toolbarNotaReg.setNavigationOnClickListener { handleBackPress() }

            etDataNota.setOnClickListener { showDatePicker() }
            btnSaveNota.setOnClickListener { onSaveClick() }

            // Form validation
            btnSaveNota.isEnabled = false
            etNomeMaterial.doAfterTextChanged { validateForm() }
            etLoja.doAfterTextChanged { validateForm() }
            etValorNota.doAfterTextChanged { validateForm() }
            listOf(
                cbPintura,
                cbPedreiro,
                cbHidraulica,
                cbEletrica,
                cbLimpeza,
                cbOutros
            ).forEach { cb ->
                cb.setOnCheckedChangeListener { _, _ -> validateForm() }
            }

            // Foto: cliques dos botões/visualização/exclusão
            btnPickImage.setOnClickListener { startPickImage() }
            btnTakePhoto.setOnClickListener { ensureCameraAndStart() }
            tvPhotoPreview.setOnClickListener { showPhotoPreviewOverlay() }
            btnDeletePhoto.setOnClickListener { onDeletePhotoClick() }

            // Overlay de pré-visualização
            photoPreviewOverlay.setOnClickListener { hidePhotoPreviewOverlay() }
            btnClosePreview.setOnClickListener { hidePhotoPreviewOverlay() }

            // Edição?
            args.notaId?.let { notaId ->
                isEdit = true
                observeNota(notaId)
            } ?: run {
                // Não é edição → inicia UI de foto no modo "sem foto"
                updatePhotoUiFromState()
            }

            collectOperationState()
            validateForm()
        }
        // Intercepta o botão físico/gesto de voltar
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPress()
        }
    }

    /* ────────────────────── Observa Nota (edição) ────────────────────── */
    private fun observeNota(notaId: String) {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeNota(args.obraId, notaId).collect { nota ->
                    nota ?: return@collect
                    notaOriginal = nota
                    prefillFields(nota)
                    updatePhotoUiFromState()  // ajusta botões/cartão de foto conforme estado
                    validateForm()            // revalida depois de preencher
                }
            }
        }
    }

    /* ────────────────────── Validação & Salvar ────────────────────── */
    private fun onSaveClick() = with(binding) {
        shouldCloseAfterSave = true  // só fecha depois de salvar
        isSaving = true               // <── SOMENTE aqui ligamos o loading do botão

        progress(true) // ALTERAÇÃO AQUI

        val nome = etNomeMaterial.text.toString().trim()
        val loja = etLoja.text.toString().trim()
        val data = etDataNota.text.toString()
        val valor = etValorNota.text.toString().replace(',', '.').toDoubleOrNull() ?: -1.0
        val status = if (rbStatusPagar.isChecked) NotaPagerAdapter.STATUS_A_PAGAR
        else NotaPagerAdapter.STATUS_PAGO

        val tipos = mutableListOf<String>()
        listOf(cbPintura, cbPedreiro, cbHidraulica, cbEletrica, cbLimpeza, cbOutros)
            .filter { it.isChecked }
            .forEach { tipos.add(it.text.toString()) }

        if (nome.isBlank() || data.isBlank() || tipos.isEmpty() || valor <= 0.0) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.nota_reg_error_required),
                getString(R.string.snack_button_ok)
            )
            return
        }

        // monta a nota base
        val baseNota = Nota(
            id = args.notaId.orEmpty(),
            nomeMaterial = nome,
            descricao = etDescricaoMaterial.text.toString().trim(),
            loja = loja,
            tipos = tipos,
            data = data,
            status = status,
            valor = valor
        )

        // ► se for edição e você NÃO selecionou nova foto, preserve a foto antiga
        val finalNota =
            if (isEdit && !hasLocalPreview && hasRemotePhoto)
                baseNota.copy(
                    fotoUrl = notaOriginal.fotoUrl,
                    fotoPath = notaOriginal.fotoPath
                )
            else baseNota

        // chamadas mantendo a foto quando apropriado
        if (isEdit) {
            if (hasLocalPreview) {
                viewModel.updateNotaWithOptionalPhoto(notaOriginal, finalNota)
            } else {
                viewModel.updateNota(notaOriginal, finalNota)
            }
        } else {
            if (hasLocalPreview) viewModel.createNotaWithOptionalPhoto(finalNota)
            else viewModel.addNota(finalNota)
        }

        btnSaveNota.isEnabled = false
        // progress(true)
    }

    /* ────────────────────── State collector ────────────────────── */
    private fun collectOperationState() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> {
                            // Só mostra o loading do botão quando estiver salvando
                            if (isSaving) progress(true)
                        }

                        is UiState.Success -> {
                            isSaving = false

                            if (shouldCloseAfterSave) {
                                val msgRes =
                                    if (isEdit) R.string.nota_toast_updated else R.string.nota_toast_added
                                Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
                                binding.root.hideKeyboard()

                                // Não desligue o progress aqui.
                                // Poste a navegação para o próximo frame para a UI ter tempo de desenhar o spinner.
                                binding.root.post {
                                    findNavController().navigateUp()
                                    shouldCloseAfterSave = false
                                }
                            } else {
                                // operações que NÃO fecham a tela (ex.: excluir foto)
                                progress(false)
                                binding.btnSaveNota.isEnabled = true
                            }
                        }

                        is UiState.ErrorRes -> {
                            // erro em qualquer operação: some loading do botão e reabilita
                            progress(false)
                            isSaving = false
                            shouldCloseAfterSave = false
                            binding.btnSaveNota.isEnabled = true
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

    /* ────────────────────── Utilitários de formulário ────────────────────── */
    private fun prefillFields(n: Nota) = with(binding) {
        toolbarNotaReg.title = getString(R.string.nota_reg_button_edit)
        btnSaveNota.setText(R.string.generic_update)

        etNomeMaterial.setText(n.nomeMaterial)
        etDescricaoMaterial.setText(n.descricao)
        etLoja.setText(n.loja)
        etDataNota.setText(n.data)

        val nf = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = false // evita "1.234,56" no campo de edicao
        }
        etValorNota.setText(nf.format(n.valor))

        listOf(cbPintura, cbPedreiro, cbHidraulica, cbEletrica, cbLimpeza, cbOutros).forEach {
            it.isChecked = n.tipos.contains(it.text.toString())
        }
        if (n.status == NotaPagerAdapter.STATUS_A_PAGAR) rbStatusPagar.isChecked = true
        else rbStatusPago.isChecked = true
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(), this,
            calendar[Calendar.YEAR],
            calendar[Calendar.MONTH],
            calendar[Calendar.DAY_OF_MONTH]
        ).show()
    }

    override fun onDateSet(dp: DatePicker, y: Int, m: Int, d: Int) {
        val date = "%02d/%02d/%04d".format(d, m + 1, y)
        binding.etDataNota.setText(date)

        // aviso se data no passado (informativo)
        val sel = Calendar.getInstance().apply {
            set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }
        val hoje = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        binding.tilDataNota.helperText =
            if (sel.before(hoje)) getString(R.string.nota_past_date_warning) else null

        validateForm()
    }

    private fun validateForm(): Boolean = with(binding) {
        // Nome do material
        val nome = etNomeMaterial.text?.toString()?.trim().orEmpty()
        val nomeOk = nome.isNotEmpty()
        tilNomeMaterial.error = if (!nomeOk) getString(R.string.nota_reg_error_nome) else null

        // Loja
        // val loja = etLoja.text?.toString()?.trim().orEmpty()
        // Não exibe erro para loja vazia
        tilLoja.error = null

        // Data (formato dd/MM/yyyy)
        val data = etDataNota.text?.toString().orEmpty()
        val dataOk = data.matches(Regex("""\d{2}/\d{2}/\d{4}"""))
        // Obs.: helperText de "data no passado" continua sendo setado no onDateSet()
        tilDataNota.error = if (!dataOk) getString(R.string.nota_reg_error_data) else null

        // Valor (> 0)
        val valorOk = etValorNota.text?.toString()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.let { it > 0.0 } == true
        tilValorNota.error = if (!valorOk) getString(R.string.nota_reg_error_valor) else null

        // Tipos (ao menos um marcado)
        val algumTipo = listOf(cbPintura, cbPedreiro, cbHidraulica, cbEletrica, cbLimpeza, cbOutros)
            .any { it.isChecked }
        tvTipoError.text = if (!algumTipo) getString(R.string.nota_reg_error_tipo) else null
        tvTipoError.visibility = if (!algumTipo) View.VISIBLE else View.GONE

        val ok = nomeOk && dataOk && valorOk && algumTipo
        // Nao reabilitar o botao enquanto estiver salvando ou depois do clique que vai fechar a tela.
        if (!isSaving && !shouldCloseAfterSave) {
            btnSaveNota.isEnabled = ok
        }
        ok
    }

    private fun progress(show: Boolean) = with(binding) {
        // o scroll e o botão só travam quando estamos salvando
        val saving = show && isSaving
        notaRegScroll.isEnabled = !saving
        btnSaveNota.isEnabled = if (saving) false else !shouldCloseAfterSave

        // o indicador abaixo do botão aparece só durante salvamento
        progressSaveNota.isVisible = saving

        if (saving) {
            // 1) Limpa o foco de qualquer campo (ex.: "Loja") para evitar auto-scroll do sistema
            requireActivity().currentFocus?.clearFocus()
            root.clearFocus()

            // 2) Foca o container não-editável para “segurar” o foco
            notaRegScroll.isFocusableInTouchMode = true
            notaRegScroll.requestFocus()

            // 3) Agora sim, fecha o teclado
            root.hideKeyboard()

            // 4) Garante visibilidade do spinner: rola até ele
            progressSaveNota.post {
                notaRegScroll.smoothScrollTo(0, progressSaveNota.bottom)
            }
        }
    }

    /* ────────────────────── Foto: ações dos botões ────────────────────── */
    private fun startPickImage() {
        // Aceita qualquer imagem
        openImagePicker.launch(arrayOf("image/*"))
    }

    private fun ensureCameraAndStart() {
        // Checa se há app de câmera
        if (!FileUtils.hasCameraApp(requireContext())) {
            showSnackbarFragment(
                type = Constants.SnackType.ERROR.name,
                title = getString(R.string.snack_attention),
                msg = getString(R.string.nota_photo_error_camera),
                btnText = getString(R.string.snack_button_ok)
            )
            return
        }
        // Checa/solicita permissão de CÂMERA (SDK 23+)
        requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startCameraCapture() {
        tempCameraUri = FileUtils.createTempImageUri(requireContext())
        takePictureLauncher.launch(tempCameraUri)
    }

    /** Pergunta se deseja anexar/substituir a foto na nota. */
    private fun confirmAddPhoto(bytes: ByteArray, mime: String) {
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.nota_photo_confirm_title),
            msg = getString(R.string.nota_photo_confirm_msg),
            btnText = getString(R.string.snack_button_yes),
            onAction = {
                // guarda no ViewModel como pendente para subir no salvar
                viewModel.setPendingPhoto(bytes, mime)
                // guarda localmente para pré-visualização e para trocar rótulos
                localPreviewBytes = bytes
                localPreviewMine = mime

                // feedback
                val toastRes = if (isEdit && hasRemotePhoto)
                    R.string.nota_photo_replaced_toast else R.string.nota_photo_added_toast
                Toast.makeText(requireContext(), getString(toastRes), Toast.LENGTH_SHORT).show()

                updatePhotoUiFromState()

                // NOVO: após confirmar a foto, rola automaticamente até o final da página
                binding.notaRegScroll.post {
                    // pós-layout, garante que o card de foto já ficou visível
                    binding.notaRegScroll.smoothScrollTo(
                        0,
                        binding.notaRegScroll.getChildAt(0).bottom
                    )
                }
            },
            btnNegativeText = getString(R.string.snack_button_no),
            onNegative = { /* nada: mantém estado anterior */ }
        )
    }

    /** Excluir (apenas local ou remota) com confirmação. */
    private fun onDeletePhotoClick() {
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.nota_photo_delete_confirm_title),
            msg = getString(R.string.nota_photo_delete_confirm_msg),
            btnText = getString(R.string.snack_button_yes),
            onAction = {
                if (hasLocalPreview) {
                    // Limpa a prévia local e o pending do ViewModel
                    localPreviewBytes = null
                    localPreviewMine = null
                    viewModel.clearPendingPhoto()

                    // Feedback
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.nota_photo_removed_toast),
                        Toast.LENGTH_SHORT
                    ).show()

                    // Some imediatamente com o overlay e reseta rótulos/visibilidade
                    hidePhotoPreviewOverlay()
                    updatePhotoUiFromState()

                } else if (isEdit && hasRemotePhoto) {
                    // Edição: dispara exclusão remota e atualiza UI de forma otimista
                    viewModel.deleteNotaPhotoAndClearField(notaOriginal)
                    notaOriginal = notaOriginal.copy(fotoUrl = null, fotoPath = null)

                    Toast.makeText(
                        requireContext(),
                        getString(R.string.nota_photo_removed_toast),
                        Toast.LENGTH_SHORT
                    ).show()

                    // Some imediatamente com o overlay e reseta rótulos/visibilidade
                    hidePhotoPreviewOverlay()
                    updatePhotoUiFromState()
                }
            },
            btnNegativeText = getString(R.string.snack_button_no),
            onNegative = { /* nada */ }
        )
    }

    /** Atualiza textos dos botões e a visibilidade do cartão/preview. */
    private fun updatePhotoUiFromState() = with(binding) {
        val hasPhoto = hasLocalPreview || hasRemotePhoto

        // Botões: rótulos padrão vs. "trocar"
        btnPickImage.setText(
            if (hasPhoto) R.string.nota_btn_pick_image_replace else R.string.nota_btn_pick_image
        )
        btnTakePhoto.setText(
            if (hasPhoto) R.string.nota_btn_take_photo_replace else R.string.nota_btn_take_photo
        )

        // Card informativo com texto "Pré-visualizar…" + lixeira
        cardPhotoInfo.isVisible = hasPhoto

        // Limpa overlay se não houver mais foto
        if (!hasPhoto) hidePhotoPreviewOverlay()
    }

    /* ────────────────────── Pré-visualização rápida (overlay) ────────────────────── */
    private fun showPhotoPreviewOverlay() = with(binding) {
        // Abre o overlay
        photoPreviewOverlay.isVisible = true

        if (hasLocalPreview) {
            // Foto local: sem loading de rede
            progressPhotoPreview.isVisible = false
            imgPhotoPreviewLarge.visibility = View.VISIBLE
            val bmp = BitmapFactory.decodeByteArray(localPreviewBytes, 0, localPreviewBytes!!.size)
            imgPhotoPreviewLarge.setImageBitmap(bmp)
            return@with
        }

        if (hasRemotePhoto) {
            val url = notaOriginal.fotoUrl!!

            // Mostra spinner e reserva espaço com o ImageView INVISIBLE
            progressPhotoPreview.isVisible = true
            imgPhotoPreviewLarge.visibility = View.INVISIBLE

            imgPhotoPreviewLarge.load(url) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ ->
                        progressPhotoPreview.isVisible = false
                        imgPhotoPreviewLarge.visibility = View.VISIBLE
                    },
                    onError = { _, _ ->
                        progressPhotoPreview.isVisible = false
                        imgPhotoPreviewLarge.setImageResource(R.drawable.ic_broken_image)
                        imgPhotoPreviewLarge.visibility = View.VISIBLE
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.nota_view_image_error_open),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    private fun hidePhotoPreviewOverlay() {
        binding.progressPhotoPreview.isVisible = false
        binding.photoPreviewOverlay.isVisible = false
    }

    // ---------------- Verificação de Edição -----------------

    // Botão Voltar
    private fun handleBackPress() {
        if (hasUnsavedChanges()) {
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.snack_attention),
                msg = getString(R.string.unsaved_confirm_msg),
                btnText = getString(R.string.snack_button_yes),
                onAction = { findNavController().navigateUp() }, // SIM
                btnNegativeText = getString(R.string.snack_button_no),
                onNegative = { /* NÃO → permanece na tela */ }
            )
        } else {
            findNavController().navigateUp()
        }
    }

    // Detecção de alteração não salva
    private fun hasUnsavedChanges(): Boolean = with(binding) {
        val nome = etNomeMaterial.text?.toString()?.trim().orEmpty()
        val desc = etDescricaoMaterial.text?.toString()?.trim().orEmpty()
        val loja = etLoja.text?.toString()?.trim().orEmpty()
        val data = etDataNota.text?.toString().orEmpty()
        val valor = etValorNota.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val status = if (rbStatusPagar.isChecked) NotaPagerAdapter.STATUS_A_PAGAR
        else NotaPagerAdapter.STATUS_PAGO

        val tiposAtual = buildList {
            if (cbPintura.isChecked) add(cbPintura.text.toString())
            if (cbPedreiro.isChecked) add(cbPedreiro.text.toString())
            if (cbHidraulica.isChecked) add(cbHidraulica.text.toString())
            if (cbEletrica.isChecked) add(cbEletrica.text.toString())
            if (cbLimpeza.isChecked) add(cbLimpeza.text.toString())
            if (cbOutros.isChecked) add(cbOutros.text.toString())
        }.toSet()

        // Selecionar uma nova foto (prévia local) conta como alteração pendente
        if (localPreviewBytes != null) return@with true

        if (!isEdit) {
            // Modo criação: compara com estado “vazio”
            return@with nome.isNotEmpty() ||
                    desc.isNotEmpty() ||
                    loja.isNotEmpty() ||
                    data.isNotEmpty() ||
                    (valor != null && valor > 0.0) ||
                    tiposAtual.isNotEmpty()
        }

        // Modo edição: compara com a nota original
        val orig = notaOriginal
        val tiposOrig = orig.tipos.toSet()

        return@with nome != orig.nomeMaterial ||
                desc != orig.descricao ||
                loja != orig.loja ||
                data != orig.data ||
                status != orig.status ||
                (valor != null && valor != orig.valor) ||
                tiposAtual != tiposOrig
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}