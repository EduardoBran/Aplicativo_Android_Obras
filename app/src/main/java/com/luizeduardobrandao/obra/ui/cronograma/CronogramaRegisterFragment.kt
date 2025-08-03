package com.luizeduardobrandao.obra.ui.cronograma

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentCronogramaRegisterBinding
import com.luizeduardobrandao.obra.ui.cronograma.adapter.CronogramaPagerAdapter
import com.luizeduardobrandao.obra.ui.extensions.hideKeyboard
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.*

@AndroidEntryPoint
class CronogramaRegisterFragment : Fragment() {

    private var _binding: FragmentCronogramaRegisterBinding? = null
    private val binding get() = _binding!!

    private val args: CronogramaRegisterFragmentArgs by navArgs()
    private val viewModel: CronogramaViewModel by viewModels()

    /* Se etapaId == null ⇒ inclusão */
    private val isEdit get() = args.etapaId != null
    private var etapaOriginal: Etapa? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentCronogramaRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            // Toolbar back & title
            toolbarEtapaReg.setNavigationOnClickListener {
                findNavController().navigateUp()
            }
            toolbarEtapaReg.title = if (isEdit)
                getString(R.string.etapa_reg_title_edit)
            else
                getString(R.string.etapa_reg_title)

            // Date pickers
            etDataInicioEtapa.setOnClickListener { pickDate(etDataInicioEtapa) }
            etDataFimEtapa.setOnClickListener { pickDate(etDataFimEtapa) }

            // Save/update button
            btnSaveEtapa.text = getString(
                if (isEdit) R.string.generic_update
                else R.string.generic_save
            )
            btnSaveEtapa.setOnClickListener { onSaveClicked() }

            // If editing, load existing Etapa
            if (isEdit) observeEtapa()
        }
    }

    /*──────────── busca etapa original, caso edição ───────────*/
    private fun observeEtapa() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    if (ui is UiState.Success) {
                        etapaOriginal = ui.data.firstOrNull { it.id == args.etapaId }
                            ?: return@collect
                        fillFields(etapaOriginal!!)
                    }
                }
            }
        }
    }

    private fun fillFields(e: Etapa) = with(binding) {
        etTituloEtapa.setText(e.titulo)
        etDescEtapa.setText(e.descricao)
        etFuncEtapa.setText(e.funcionarios)
        etDataInicioEtapa.setText(e.dataInicio)
        etDataFimEtapa.setText(e.dataFim)
        when (e.status) {
            CronogramaPagerAdapter.STATUS_PENDENTE -> rbStatPend.isChecked = true
            CronogramaPagerAdapter.STATUS_ANDAMENTO -> rbStatAnd.isChecked = true
            else -> rbStatConcl.isChecked = true
        }
    }

    /*──────────── salvar / validar ───────────*/
    private fun onSaveClicked() {
        with(binding) {
            val titulo  = etTituloEtapa.text.toString().trim()
            val dataIni = etDataInicioEtapa.text.toString().trim()
            val dataFim = etDataFimEtapa.text.toString().trim()

            if (titulo.length < Constants.Validation.MIN_NAME) {
                showError(R.string.etapa_error_title)
                return
            }
            if (dataIni.isBlank() || dataFim.isBlank()) {
                showError(R.string.etapa_error_dates)
                return
            }

            val status = when (rgStatusEtapa.checkedRadioButtonId) {
                R.id.rbStatAnd   -> CronogramaPagerAdapter.STATUS_ANDAMENTO
                R.id.rbStatConcl -> CronogramaPagerAdapter.STATUS_CONCLUIDO
                else             -> CronogramaPagerAdapter.STATUS_PENDENTE
            }

            val etapa = Etapa(
                id           = etapaOriginal?.id ?: "",
                titulo       = titulo,
                descricao    = etDescEtapa.text.toString().trim(),
                funcionarios = etFuncEtapa.text.toString().trim(),
                dataInicio   = dataIni,
                dataFim      = dataFim,
                status       = status
            )

            btnSaveEtapa.isEnabled = false
            btnSaveEtapa.text = getString(R.string.generic_saving)
            requireView().hideKeyboard()

            if (isEdit) viewModel.updateEtapa(etapa)
            else        viewModel.addEtapa(etapa)

            Toast.makeText(
                requireContext(),
                getString(if (isEdit) R.string.etapa_update_success else R.string.etapa_add_success),
                Toast.LENGTH_SHORT
            ).show()
            findNavController().navigateUp()
        }
    }

    private fun showError(@androidx.annotation.StringRes res: Int) {
        showSnackbarFragment(
            Constants.SnackType.ERROR.name,
            getString(R.string.snack_error),
            getString(res),
            getString(R.string.snack_button_ok)
        )
    }

    /*──────────── Date picker util ───────────*/
    private fun pickDate(target: View) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                (target as? android.widget.EditText)
                    ?.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month + 1, year))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}