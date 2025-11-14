package com.luizeduardobrandao.obra.ui.work

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.net.ConnectivityManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.doOnPreDraw
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Obra
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentWorkBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.applyResponsiveButtonSizingGrowShrink
import com.luizeduardobrandao.obra.utils.applySoloNewWorkStyle
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.isConnectedToInternet
import com.luizeduardobrandao.obra.utils.registerConnectivityCallback
import com.luizeduardobrandao.obra.utils.showMaterialDatePickerBrToday
import com.luizeduardobrandao.obra.utils.syncTextSizesGroup
import com.luizeduardobrandao.obra.utils.unregisterConnectivityCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class WorkFragment : Fragment() {

    private var _binding: FragmentWorkBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WorkViewModel by viewModels()

    // obra selecionada no dropdown
    private var selectedObra: Obra? = null

    // guarda a lista ordenada para mapear posição -> Obra
    private var obrasOrdenadas: List<Obra> = emptyList()

    // Formato dd/MM/yyyy sem leniência
    private val sdf =
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }

    // Flags Imagens
    private var hasAnimatedEmpty = false
    private var hasAnimatedSelect = false

    // Conexão a Internet
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    // usado para restaurar a seleção depois que a lista chegar
    private var pendingSelectedObraId: String? = null

    private var cardVisibleCache = false
    private var cachedCliente = ""
    private var cachedEndereco = ""
    private var cachedContato = ""
    private var cachedDescricao = ""
    private var cachedSaldo = ""
    private var cachedDataIni = ""
    private var cachedDataFim = ""

    override fun onStart() {
        super.onStart()

        if (!requireContext().isConnectedToInternet()) {
            showSnackbarFragment(
                Constants.SnackType.WARNING.name,
                getString(R.string.generic_warning),
                getString(R.string.generic_error_no_internet),
                getString(R.string.generic_ok_upper_case)
            )
        }

        netCallback = requireContext().registerConnectivityCallback(
            onAvailable = { /* opcional */ },
            onLost = {
                if (view != null) {
                    showSnackbarFragment(
                        Constants.SnackType.WARNING.name,
                        getString(R.string.generic_warning),
                        getString(R.string.generic_error_no_internet),
                        getString(R.string.generic_ok_upper_case)
                    )
                }
            }
        )
    }

    override fun onStop() {
        super.onStop()
        netCallback?.let { requireContext().unregisterConnectivityCallback(it) }
        netCallback = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyHeroVisibility()
        setupListeners()

        // RESTORE: card + campos
        savedInstanceState?.let { st ->
            // Restaurar seleção de obra depois que a lista chegar
            pendingSelectedObraId = st.getString(KEY_SELECTED_OBRA_ID)

            // Se o card estava aberto, reabre e repõe os campos
            if (st.getBoolean(KEY_CARD_VISIBLE, false)) {
                toggleNewWorkCard(true) // NÃO limpa (veremos no passo 4)
                binding.etCliente.setText(st.getString(KEY_ET_CLIENTE, ""))
                binding.etEndereco.setText(st.getString(KEY_ET_ENDERECO, ""))
                binding.etContato.setText(st.getString(KEY_ET_CONTATO, ""))
                binding.etDescricao.setText(st.getString(KEY_ET_DESC, ""))
                binding.etSaldo.setText(st.getString(KEY_ET_SALDO, ""))
                binding.etDataInicio.setText(st.getString(KEY_ET_DATA_INI, ""))
                binding.etDataFim.setText(st.getString(KEY_ET_DATA_FIM, ""))
                validateCard()
            }
        }

        // Responsivdade Botões
        with(binding) {
            // Botão SOLO do cenário vazio mantém o preset atual (pílula)
            btnNewWorkEmpty.applySoloNewWorkStyle()

            // Par "Novo / Continuar" (lado a lado, width=0dp + weight=1)
            // 1) Deixa cada um crescer/encolher de 12..cap (grow-shrink)
            // 2) Nivela os dois pelo menor resultado final (mesmo textSize)
            selectContainer.doOnPreDraw {
                btnNewWork.applyResponsiveButtonSizingGrowShrink()
                btnContinue.applyResponsiveButtonSizingGrowShrink()
                selectContainer.syncTextSizesGroup(btnNewWork, btnContinue)
            }

            // Par "Salvar / Voltar" (dentro do card)
            cardNewWork.doOnPreDraw {
                btnSaveWork.applyResponsiveButtonSizingGrowShrink()
                btnCancelWork.applyResponsiveButtonSizingGrowShrink()
                cardNewWork.syncTextSizesGroup(btnSaveWork, btnCancelWork)
            }
        }

        collectViewModel()

        // Sair do app ao pressionar "voltar" quando WorkFragment estiver no topo
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requireActivity().finishAffinity() // Encerra a activity e sai do app
                }
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED_OBRA_ID, selectedObra?.obraId)
        outState.putBoolean(KEY_CARD_VISIBLE, cardVisibleCache)
        if (cardVisibleCache) {
            outState.putString(KEY_ET_CLIENTE, cachedCliente)
            outState.putString(KEY_ET_ENDERECO, cachedEndereco)
            outState.putString(KEY_ET_CONTATO, cachedContato)
            outState.putString(KEY_ET_DESC, cachedDescricao)
            outState.putString(KEY_ET_SALDO, cachedSaldo)
            outState.putString(KEY_ET_DATA_INI, cachedDataIni)
            outState.putString(KEY_ET_DATA_FIM, cachedDataFim)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /* ───────────────────────────── listeners / UI ───────────────────────────── */
    private fun setupListeners() = with(binding) {

        // "Nova Obra" (cenário vazio e com obras)
        btnNewWorkEmpty.setOnClickListener { toggleNewWorkCard(true); validateCard() }
        btnNewWork.setOnClickListener { toggleNewWorkCard(true); validateCard() }

        // Voltar no Card
        btnCancelWork.setOnClickListener { toggleNewWorkCard(false) }

        // Salvar nova obra
        btnSaveWork.setOnClickListener {
            root.hideKeyboard()
            if (validateCard()) saveNewWork()
        }

        // Exposed Dropdown – habilita continuar após seleção
        autoObras.setOnItemClickListener { _, _, pos, _ ->
            selectedObra = obrasOrdenadas.getOrNull(pos)
            autoObras.setText(selectedObra?.nomeCliente ?: "", false) // mostra só o nome
            autoObras.clearFocus()                                    // opcional: melhora UX
            tilObras.error = null                                     // opcional: limpa erro
            btnContinue.isEnabled = selectedObra != null
            selectContainer.syncTextSizesGroup(btnNewWork, btnContinue)
        }

        // Continuar → HomeFragment
        btnContinue.setOnClickListener {
            showLoading(true)
            val obra = selectedObra ?: return@setOnClickListener
            Toast.makeText(
                requireContext(),
                getString(R.string.work_toast_selected, obra.nomeCliente),
                Toast.LENGTH_SHORT
            ).show()
            findNavController()
                .navigate(WorkFragmentDirections.actionWorkToHome(obraId = obra.obraId))
        }

        // Date pickers usando MaterialDatePicker (sempre iniciando hoje)
        etDataInicio.setOnClickListener {
            showMaterialDatePickerBrToday { chosen ->
                etDataInicio.setText(chosen)
                validateCard()
            }
        }
        etDataFim.setOnClickListener {
            showMaterialDatePickerBrToday { chosen ->
                etDataFim.setText(chosen)
                validateCard()
            }
        }

        // TextWatchers em todos os campos do card
        listOf(etCliente, etEndereco, etContato, etDescricao, etSaldo, etDataInicio, etDataFim)
            .forEach {
                it.doAfterTextChanged {
                    cachedCliente = binding.etCliente.text?.toString().orEmpty()
                    cachedEndereco = binding.etEndereco.text?.toString().orEmpty()
                    cachedContato = binding.etContato.text?.toString().orEmpty()
                    cachedDescricao = binding.etDescricao.text?.toString().orEmpty()
                    cachedSaldo = binding.etSaldo.text?.toString().orEmpty()
                    cachedDataIni = binding.etDataInicio.text?.toString().orEmpty()
                    cachedDataFim = binding.etDataFim.text?.toString().orEmpty()
                    validateCard()
                }
            }
    }

    /* ──────────────────────────── collect ViewModel ─────────────────────────── */
    private fun collectViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // lista de obras
                launch {
                    viewModel.obrasState.collect { state ->
                        when (state) {
                            is UiState.Loading -> showLoading(true)
                            is UiState.Success -> renderWorks(state.data)
                            is UiState.ErrorRes -> {
                                showLoading(false)
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

                // resultado de criação
                launch {
                    viewModel.createState.collect { state ->
                        when (state) {
                            is UiState.Loading -> {
                                binding.progressCard.isVisible = true
                                binding.btnSaveWork.isEnabled = false
                            }

                            is UiState.Success -> {
                                binding.progressCard.isGone = true
                                toggleNewWorkCard(false)
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.work_toast_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                                viewModel.resetCreateState()
                            }

                            is UiState.ErrorRes -> {
                                binding.progressCard.isGone = true
                                showSnackbarFragment(
                                    Constants.SnackType.ERROR.name,
                                    getString(R.string.generic_error),
                                    getString(state.resId),
                                    getString(R.string.generic_ok_upper_case)
                                )
                                viewModel.resetCreateState()
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    /* ─────────────────────────────── UI helpers ────────────────────────────── */

    private fun showLoading(show: Boolean) = with(binding) {
        progressWork.isVisible = show
        layoutEmpty.isGone = show
        layoutSelect.isGone = show
        cardNewWork.isGone = true
    }

    /** Preenche o dropdown com a lista de obras ordenada alfabeticamente. */
    private fun renderWorks(lista: List<Obra>) = with(binding) {
        progressWork.isGone = true

        if (lista.isEmpty()) {
            // cenário vazio
            layoutSelect.isGone = true
            layoutEmpty.isVisible = true

            btnNewWorkEmpty.applySoloNewWorkStyle()

            // anima só na primeira vez
            if (!hasAnimatedEmpty) {
                root.doOnPreDraw {
                    animateIn(imgEmpty, emptyContainer)
                    hasAnimatedEmpty = true
                }
            }
            return@with
        }

        // cenário com obras
        layoutEmpty.isGone = true
        layoutSelect.isVisible = true

        selectContainer.doOnPreDraw {
            btnNewWork.applyResponsiveButtonSizingGrowShrink()
            btnContinue.applyResponsiveButtonSizingGrowShrink()
            selectContainer.syncTextSizesGroup(btnNewWork, btnContinue)
        }

        obrasOrdenadas = lista.sortedBy { it.nomeCliente.lowercase(Locale.ROOT) }
        val nomes = obrasOrdenadas.map { it.nomeCliente }

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner,
            nomes
        ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }

        autoObras.setAdapter(adapter)

        // >>> NÃO zere o texto aqui se for restaurar seleção
        val restoreId = pendingSelectedObraId
        if (restoreId != null) {
            val idx = obrasOrdenadas.indexOfFirst { it.obraId == restoreId }
            if (idx >= 0) {
                selectedObra = obrasOrdenadas[idx]
                autoObras.setText(nomes[idx], false)
                tilObras.error = null
                btnContinue.isEnabled = true
            } else {
                // id não existe mais; reseta
                selectedObra = null
                autoObras.setText("", false)
                btnContinue.isEnabled = false
            }
            pendingSelectedObraId = null
        } else {
            // fluxo normal sem restauração
            autoObras.setText("", false)
            selectedObra = null
            btnContinue.isEnabled = false
            btnNewWork.isEnabled = true
        }

        if (!hasAnimatedSelect) {
            root.doOnPreDraw {
                animateIn(imgSelect, selectContainer)
                hasAnimatedSelect = true
            }
        }
    }

    private fun toggleNewWorkCard(show: Boolean) = with(binding) {
        cardVisibleCache = show
        // Se estiver FECHANDO, limpamos tudo para garantir que a próxima abertura seja zerada
        if (!show) {
            // zerar campos + cache
            etCliente.text?.clear(); cachedCliente = ""
            etEndereco.text?.clear(); cachedEndereco = ""
            etContato.text?.clear(); cachedContato = ""
            etDescricao.text?.clear();cachedDescricao = ""
            etSaldo.text?.clear(); cachedSaldo = ""
            etDataInicio.text?.clear(); cachedDataIni = ""
            etDataFim.text?.clear(); cachedDataFim = ""
            tilDataFim.error = null
            btnSaveWork.isEnabled = false
        }

        cardNewWork.isVisible = show
        workScroll.scrollTo(0, 0)

        if (show) {
            cardNewWork.doOnPreDraw {
                btnSaveWork.applyResponsiveButtonSizingGrowShrink()
                btnCancelWork.applyResponsiveButtonSizingGrowShrink()
                cardNewWork.syncTextSizesGroup(btnSaveWork, btnCancelWork)
            }
        }
    }


    /* ───────────────────── validação / envio de nova obra ──────────────────── */

    private fun validateCard(): Boolean = with(binding) {

        fun TextInputEditText.validate(condition: Boolean, resId: Int): Boolean {
            error = if (condition) getString(resId) else null
            return condition
        }

        var hasError = false

        // nome
        val nome = etCliente.text?.toString().orEmpty()
        hasError = etCliente.validate(
            nome.length !in Constants.Validation.MIN_NAME..Constants.Validation.MAX_CLIENT_NAME,
            R.string.work_error_nome
        ) || hasError

        // endereço
        val endereco = etEndereco.text?.toString().orEmpty()
        hasError = etEndereco.validate(endereco.isBlank(), R.string.work_error_endereco) || hasError

        // contato
        val contato = etContato.text?.toString().orEmpty()
        hasError = etContato.validate(contato.isBlank(), R.string.work_error_contato) || hasError

        // descrição
        val descricao = etDescricao.text?.toString().orEmpty()
        hasError = etDescricao.validate(descricao.isBlank(), R.string.work_error_desc) || hasError

        // saldo (aceita "20.500" como 20500.00 e "20.500,75" como 20500.75)
        val saldo = parseLocalizedNumberOrNull(etSaldo.text?.toString())
        hasError = etSaldo.validate(
            saldo == null || saldo < Constants.Validation.MIN_SALDO,
            R.string.work_error_saldo_format
        ) || hasError

        // datas (presença)
        val dataInicio = etDataInicio.text?.toString().orEmpty()
        val dataFim = etDataFim.text?.toString().orEmpty()
        val faltouInicio =
            etDataInicio.validate(dataInicio.isBlank(), R.string.work_error_data_inicio)
        val faltouFim = etDataFim.validate(dataFim.isBlank(), R.string.work_error_data_fim)
        hasError = faltouInicio || faltouFim || hasError

        // tem as duas datas?
        val haveBoth = dataInicio.isNotBlank() && dataFim.isNotBlank()

        // ordem das datas: fim >= início
        val ordemOk = haveBoth && isDateOrderValid(dataInicio, dataFim)

        // erro no TextInputLayout da data fim quando ordem é inválida
        tilDataFim.error = if (haveBoth && !ordemOk)
            getString(R.string.work_error_date_order) else null

        val ok = !hasError && ordemOk
        btnSaveWork.isEnabled = ok
        return ok
    }

    private fun saveNewWork() = with(binding) {
        val obra = Obra(
            nomeCliente = etCliente.text!!.trim().toString(),
            endereco = etEndereco.text!!.trim().toString(),
            contato = etContato.text!!.trim().toString(),
            descricao = etDescricao.text!!.trim().toString(),
            saldoInicial = parseLocalizedNumberOrNull(etSaldo.text!!.toString()) ?: 0.0,
            dataInicio = etDataInicio.text!!.toString(),
            dataFim = etDataFim.text!!.toString()
        )
        viewModel.createObra(obra)
    }

    private fun parseDateOrNull(s: String?): Date? = try {
        if (s.isNullOrBlank()) null else sdf.parse(s)
    } catch (_: ParseException) {
        null
    }

    /** true se dataFim >= dataInicio (ambas válidas), false caso contrário */
    private fun isDateOrderValid(dataInicio: String?, dataFim: String?): Boolean {
        val ini = parseDateOrNull(dataInicio) ?: return false
        val fim = parseDateOrNull(dataFim) ?: return false
        return !fim.before(ini)
    }

    /** Valida e converte número em pt-BR ou en-US.
     * Aceita:
     *  - pt-BR: 20.500,00 | 20500,75 | 20500
     *  - en-US: 20,500.00 | 20500.75 | 20500
     * Rejeita formatos mistos (ex.: "20,500,00").
     */
    private fun parseLocalizedNumberOrNull(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()

        // pt-BR: grupos de milhar com ponto e decimal com vírgula
        val ptBrRegex = Regex("""^\d{1,3}(\.\d{3})*(,\d{1,2})?$|^\d+(,\d{1,2})?$""")
        // en-US: grupos de milhar com vírgula e decimal com ponto
        val enUsRegex = Regex("""^\d{1,3}(,\d{3})*(\.\d{1,2})?$|^\d+(\.\d{1,2})?$""")

        return when {
            ptBrRegex.matches(s) -> {
                try {
                    NumberFormat.getNumberInstance(
                        Locale("pt", "BR")
                    ).parse(s)?.toDouble()
                } catch (_: ParseException) {
                    null
                }
            }

            enUsRegex.matches(s) -> {
                try {
                    NumberFormat.getNumberInstance(Locale.US).parse(s)?.toDouble()
                } catch (_: ParseException) {
                    null
                }
            }

            else -> null // formato inválido (ex.: "20,500,00")
        }
    }

    // Função Animação Imagens
    private fun animateIn(hero: View, container: View) {
        val interp = FastOutSlowInInterpolator()
        val dy = 16f * resources.displayMetrics.density

        hero.alpha = 0f
        hero.translationY = -dy
        container.alpha = 0f
        container.translationY = dy

        hero.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setStartDelay(40L)
            .setInterpolator(interp)
            .start()

        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240L)
            .setStartDelay(80L)
            .setInterpolator(interp)
            .start()
    }

    // Controla exibição da imagem em orientação horizontal
    private fun applyHeroVisibility() = with(binding) {
        val showHero = resources.getBoolean(R.bool.show_hero)
        imgSelect.visibility = if (showHero) View.VISIBLE else View.GONE
        imgEmpty.visibility = if (showHero) View.VISIBLE else View.GONE
    }

    // Sobreviver rotações
    companion object {
        private const val KEY_SELECTED_OBRA_ID = "selectedObraId"
        private const val KEY_CARD_VISIBLE = "cardVisible"
        private const val KEY_ET_CLIENTE = "etCliente"
        private const val KEY_ET_ENDERECO = "etEndereco"
        private const val KEY_ET_CONTATO = "etContato"
        private const val KEY_ET_DESC = "etDescricao"
        private const val KEY_ET_SALDO = "etSaldo"
        private const val KEY_ET_DATA_INI = "etDataInicio"
        private const val KEY_ET_DATA_FIM = "etDataFim"
    }
}