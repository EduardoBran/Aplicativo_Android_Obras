package com.luizeduardobrandao.obra.ui.cronograma

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentDetalheCronogramaBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DetalheCronogramaFragment : Fragment() {

    private var _binding: FragmentDetalheCronogramaBinding? = null
    private val binding get() = _binding!!

    private val args: DetalheCronogramaFragmentArgs by navArgs()
    private val viewModel: CronogramaViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentDetalheCronogramaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            toolbarDetEtapa.setNavigationOnClickListener { findNavController().navigateUp() }

            // ▼ IMPORTANTE: carregar lista para conseguir achar a etapa pelo ID
            viewModel.loadEtapas()

            lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.state.collect { ui ->
                        when (ui) {
                            is UiState.Success -> {
                                val et =
                                    ui.data.firstOrNull { it.id == args.etapaId } ?: return@collect

                                toolbarDetEtapa.title = et.titulo

                                tvDetTitulo.text = et.titulo

                                tvDetDescricao.text = et.descricao?.ifBlank { "—" } ?: "—"

                                /* ► Se foi salvo como EMPRESA: troca o rótulo e exibe o nome da empresa.
                                   ► Caso contrário, mantém Funcionário(s) + CSV já existente. */
                                if (et.responsavelTipo == "EMPRESA") {
                                    tvDetFuncLabel.setText(R.string.det_empresa_label)  // "Nome da empresa"
                                    tvDetFunc.text = et.empresaNome?.ifBlank { "—" } ?: "—"
                                } else {
                                    tvDetFuncLabel.setText(R.string.cron_reg_func_hint) // "Funcionário(s)"
                                    tvDetFunc.text = et.funcionarios?.ifBlank { "—" } ?: "—"
                                }

                                tvDetDataIni.text = et.dataInicio
                                tvDetDataFim.text = et.dataFim
                                tvDetStatus.text = et.status
                            }

                            is UiState.ErrorRes -> {
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}