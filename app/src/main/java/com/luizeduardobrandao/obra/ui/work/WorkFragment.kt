package com.luizeduardobrandao.obra.ui.work

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Obra
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentWorkBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.attachDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.ConnectivityManager
import com.luizeduardobrandao.obra.utils.isConnectedToInternet
import com.luizeduardobrandao.obra.utils.registerConnectivityCallback
import com.luizeduardobrandao.obra.utils.unregisterConnectivityCallback

@AndroidEntryPoint
class WorkFragment : Fragment() {

    private var _binding: FragmentWorkBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WorkViewModel by viewModels()

    /** obra selecionada no dropdown */
    private var selectedObra: Obra? = null
    // guarda a lista ordenada para mapear posição -> Obra
    private var obrasOrdenadas: List<Obra> = emptyList()

    // Formato dd/MM/yyyy sem leniência
    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }

    // Conexão a Internet
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStart() {
        super.onStart()

        if (!requireContext().isConnectedToInternet()) {
            showSnackbarFragment(
                Constants.SnackType.WARNING.name,
                getString(R.string.snack_warning),
                getString(R.string.error_no_internet),
                getString(R.string.snack_button_ok)
            )
        }

        netCallback = requireContext().registerConnectivityCallback(
            onAvailable = { /* opcional */ },
            onLost = {
                if (view != null) {
                    showSnackbarFragment(
                        Constants.SnackType.WARNING.name,
                        getString(R.string.snack_warning),
                        getString(R.string.error_no_internet),
                        getString(R.string.snack_button_ok)
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
        setupListeners()
        collectViewModel()
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

        // Date pickers no card
        etDataInicio.attachDatePicker()
        etDataFim.attachDatePicker()

        // TextWatchers em todos os campos do card
        listOf(etCliente, etEndereco, etDescricao, etSaldo, etDataInicio, etDataFim)
            .forEach { it.doAfterTextChanged { validateCard() } }
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
                                    getString(R.string.snack_error),
                                    getString(state.resId),
                                    getString(R.string.snack_button_ok)
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
                                    getString(R.string.snack_error),
                                    getString(state.resId),
                                    getString(R.string.snack_button_ok)
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
            layoutEmpty.isVisible = true
            layoutSelect.isGone = true
            return@with
        }

        layoutEmpty.isGone = true
        layoutSelect.isVisible = true

        obrasOrdenadas = lista.sortedBy { it.nomeCliente.lowercase(Locale.ROOT) }
        val nomes = obrasOrdenadas.map { it.nomeCliente }

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner,           // linha “fechada”
            nomes
        ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }

        autoObras.setAdapter(adapter)

        // começa vazio (só hint visível)
        autoObras.setText("", false)
        selectedObra = null
        btnContinue.isEnabled = false
    }

    private fun toggleNewWorkCard(show: Boolean) = with(binding) {
        cardNewWork.isVisible = show
        workScroll.scrollTo(0, 0)
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

        // descrição
        val descricao = etDescricao.text?.toString().orEmpty()
        hasError = etDescricao.validate(descricao.isBlank(), R.string.work_error_desc) || hasError

        // saldo
        val saldo = etSaldo.text?.toString()?.toDoubleOrNull()
        hasError = etSaldo.validate(
            saldo == null || saldo < Constants.Validation.MIN_SALDO,
            R.string.work_error_saldo
        ) || hasError

        // datas (presença)
        val dataInicio = etDataInicio.text?.toString().orEmpty()
        val dataFim = etDataFim.text?.toString().orEmpty()
        val faltouInicio = etDataInicio.validate(dataInicio.isBlank(), R.string.work_error_data_inicio)
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
            descricao = etDescricao.text!!.trim().toString(),
            saldoInicial = etSaldo.text!!.toString().toDouble(),
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
}