package com.luizeduardobrandao.obra.ui.notas

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.DatePicker
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentNotaRegisterBinding
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.notas.adapter.NotaPagerAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.*

@AndroidEntryPoint
class NotaRegisterFragment : Fragment(), DatePickerDialog.OnDateSetListener {

    private var _binding: FragmentNotaRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: NotaRegisterFragmentArgs by navArgs()
    private val viewModel: NotasViewModel by viewModels()

    private var isEdit = false
    private lateinit var notaOriginal: Nota   // usado em edição

    private val calendar = Calendar.getInstance()


    /*──────────────────────── Ciclo de Vida ────────────────────────*/

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentNotaRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            toolbarNotaReg.setNavigationOnClickListener { findNavController().navigateUp() }

            /* Date picker */
            etDataNota.setOnClickListener { showDatePicker() }

            btnSaveNota.setOnClickListener { onSaveClick() }

            /* Se vier notaId → modo edição */
            args.notaId?.let { notaId ->
                isEdit = true
                observeNota(notaId)
            }

            // <-- chama o collector para tratar add/update
            collectOperationState()
        }
    }


    /*────────────────────── Observa Nota para edição ───────────────────────*/
    private fun observeNota(notaId: String) {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeNota(args.obraId, notaId).collect { nota ->
                    nota ?: return@collect
                    notaOriginal = nota
                    prefillFields(nota)
                }
            }
        }
    }

    /*────────────────────── Validação & Salvar ─────────────────────────────*/
    private fun onSaveClick() = with(binding) {
        /* Validações */
        val nome = etNomeMaterial.text.toString().trim()
        val loja = etLoja.text.toString().trim()
        val data = etDataNota.text.toString()
        val valor = etValorNota.text.toString().toDoubleOrNull() ?: -1.0
        val status = if (rbStatusPagar.isChecked) NotaPagerAdapter.STATUS_A_PAGAR
        else NotaPagerAdapter.STATUS_PAGO

        val tipos = mutableListOf<String>()
        listOf(cbPintura, cbPedreiro, cbHidraulica, cbEletrica, cbOutros)
            .filter { it.isChecked }
            .forEach { tipos.add(it.text.toString()) }

        if (nome.isBlank() || loja.isBlank() || data.isBlank() ||
            tipos.isEmpty() || valor <= 0.0
        ) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.nota_load_error),
                getString(R.string.snack_button_ok)
            )
            return
        }

        val nota = Nota(
            id = args.notaId.orEmpty(),
            nomeMaterial = nome,
            descricao = etDescricaoMaterial.text.toString().trim(),
            loja = loja,
            tipos = tipos,
            data = data,
            status = status,
            valor = valor
        )

        /* Aciona o ViewModel */
        if (isEdit) viewModel.updateNota(notaOriginal, nota)
        else viewModel.addNota(nota)

        btnSaveNota.isEnabled = false
        progress(true)
        /* Feedback & navegação no collector abaixo */
    }

    /*────────────────────── State collector ───────────────────────────────*/
    private fun collectOperationState() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    if (ui !is UiState.Loading) progress(false)
                    if (ui is UiState.Success || ui is UiState.ErrorRes) binding.btnSaveNota.isEnabled =
                        true

                    when (ui) {
                        is UiState.Success -> {
                            val msgRes =
                                if (isEdit) R.string.nota_toast_updated
                                else R.string.nota_toast_added
                            Toast.makeText(requireContext(), msgRes, Toast.LENGTH_SHORT).show()
                            binding.root.hideKeyboard()
                            findNavController().navigateUp()
                        }

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
        }
    }

    /*────────────────────── Utilitários ─────────────────────────────*/
    private fun prefillFields(n: Nota) = with(binding) {
        toolbarNotaReg.title = getString(R.string.nota_detail_title)
        btnSaveNota.setText(R.string.generic_update)

        etNomeMaterial.setText(n.nomeMaterial)
        etDescricaoMaterial.setText(n.descricao)
        etLoja.setText(n.loja)
        etDataNota.setText(n.data)
        etValorNota.setText(n.valor.toString())

        listOf(cbPintura, cbPedreiro, cbHidraulica, cbEletrica, cbOutros).forEach {
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
    }

    private fun progress(show: Boolean) = with(binding) {
        notaRegScroll.isEnabled = !show
        btnSaveNota.isEnabled   = !show
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}