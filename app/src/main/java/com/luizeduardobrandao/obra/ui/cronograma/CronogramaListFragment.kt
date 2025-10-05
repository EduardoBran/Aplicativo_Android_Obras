package com.luizeduardobrandao.obra.ui.cronograma

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentCronogramaListBinding
import com.luizeduardobrandao.obra.ui.cronograma.adapter.EtapaAdapter
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.funcionario.FuncionarioViewModel
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.GanttUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Um dos “páginas” do ViewPager2 do Cronograma.
 * Recebe [status] (“Pendente”, “Andamento” ou “Concluído”) via arguments.
 */
@AndroidEntryPoint
class CronogramaListFragment : Fragment() {

    private var _binding: FragmentCronogramaListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CronogramaViewModel by viewModels({ requireParentFragment() })

    private val viewModelFuncionario: FuncionarioViewModel by viewModels()
    private val funcionariosCache = mutableListOf<Funcionario>()

    private lateinit var obraId: String
    private lateinit var status: String

    /* Call-backs delegadas ao CronogramaFragment */
    private var actions: EtapaActions? = null

    private val adapter by lazy {
        EtapaAdapter(
            getFuncionarios = { funcionariosCache.toList() },
            onEdit = { actions?.onEdit(it) },
            onDetail = { actions?.onDetail(it) },
            onDelete = { actions?.onDelete(it) }
        )
    }

    /*──────────── Attach / Args ────────────*/
    override fun onAttach(context: Context) {
        super.onAttach(context)
        actions = parentFragment as? EtapaActions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            obraId = it.getString(ARG_OBRA) ?: error("obraId ausente")
            status = it.getString(ARG_STATUS) ?: error("status ausente")
        }
    }

    /*──────────── UI ────────────*/

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentCronogramaListBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvEtapas.apply {
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            itemAnimator = DefaultItemAnimator().apply { supportsChangeAnimations = false }
            adapter = this@CronogramaListFragment.adapter
        }

        // Carrega/escuta funcionários (opcionalmente apenas Ativos)
        viewModelFuncionario.loadFuncionarios()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModelFuncionario.state.collect { ui ->
                    if (ui is UiState.Success) {
                        funcionariosCache.clear()
                        funcionariosCache.addAll(
                            ui.data.filter { it.status.equals("Ativo", ignoreCase = true) }
                        )
                        // Se quiser re-render imediatamente quando os funcionários chegarem:
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }

        // inicia o collector de etapas
        observeState()
    }


    /*──────────── State collector ────────────*/
    private fun observeState() = with(binding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> progress(true)
                        is UiState.Success -> {
                            progress(false)
                            val list = ui.data
                                .filter { it.status == status }
                                .sortedBy {
                                    GanttUtils.brToLocalDateOrNull(it.dataInicio)?.toEpochDay()
                                        ?: Long.MAX_VALUE
                                }
                            adapter.submitList(list)

                            if (isResumed) {
                                binding.rvEtapas.post {
                                    adapter.reanimateVisible(binding.rvEtapas)
                                }
                            }

                            rvEtapas.isVisible = list.isNotEmpty()
                            tvEmptyEtapas.isVisible = list.isEmpty()
                        }

                        is UiState.ErrorRes -> {
                            progress(false)
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

    private fun progress(show: Boolean) = with(binding) {
        progressEtapas.isVisible = show
        rvEtapas.isVisible = !show
    }

    override fun onResume() {
        super.onResume()
        // Garante que ao selecionar a aba, as porcentagens animem
        binding.rvEtapas.post {
            adapter.reanimateVisible(binding.rvEtapas)
        }
    }

    override fun onDestroyView() {
        // ✅ Cancela quaisquer animações em andamento para evitar vazamentos ou "salto" visual
        binding.rvEtapas.let { rv ->
            for (i in 0 until rv.childCount) {
                val holder = rv.getChildViewHolder(rv.getChildAt(i))
                if (holder is EtapaAdapter.VH) {
                    holder.pctAnimator?.cancel()
                }
            }
        }

        super.onDestroyView()
        _binding = null
    }

    /*──────────── Companion / Factory ────────────*/
    companion object {
        private const val ARG_OBRA = "obraId"
        private const val ARG_STATUS = "status"

        fun newInstance(obraId: String, status: String) =
            CronogramaListFragment().apply {
                arguments = bundleOf(
                    ARG_OBRA to obraId,
                    ARG_STATUS to status
                )
            }
    }
}