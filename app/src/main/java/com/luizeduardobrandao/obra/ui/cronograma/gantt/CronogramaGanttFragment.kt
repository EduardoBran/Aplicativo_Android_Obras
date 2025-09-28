package com.luizeduardobrandao.obra.ui.cronograma.gantt

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentCronogramaGanttBinding
import com.luizeduardobrandao.obra.ui.cronograma.CronogramaViewModel
import com.luizeduardobrandao.obra.ui.cronograma.gantt.adapter.GanttRowAdapter
import com.luizeduardobrandao.obra.utils.GanttUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate

@AndroidEntryPoint
class CronogramaGanttFragment : Fragment() {

    private var _binding: FragmentCronogramaGanttBinding? = null
    private val binding get() = _binding!!

    private val args: CronogramaGanttFragmentArgs by navArgs()
    private val viewModel: CronogramaViewModel by viewModels()

    private lateinit var adapter: GanttRowAdapter

    // Cabeçalho/timeline global (mínimo comum entre todas as etapas carregadas)
    private var headerDays: List<LocalDate> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCronogramaGanttBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // toolbar
        toolbarGantt.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        toolbarGantt.title = getString(R.string.gantt_title)

        // recycler
        rvGantt.layoutManager = LinearLayoutManager(requireContext())
        adapter = GanttRowAdapter(
            onToggleDay = { etapa, newSetUtc ->
                // Persistir alteração via ViewModel (recalcula % + status)
                viewModel.commitDias(etapa, newSetUtc)
            },
            requestHeaderDays = { headerDays } // fornece o cabeçalho para cada linha
        )

        // aqui você conecta o callback ANTES de setar o adapter no RecyclerView
        adapter.onFirstLeftWidth = { leftWidth ->
            val row = binding.headerRow
            val inset = resources.getDimensionPixelSize(R.dimen.gantt_header_start_inset)
            val startGap = resources.getDimensionPixelSize(R.dimen.gantt_first_cell_margin_start)
            row.setPadding(
                leftWidth + inset + startGap,
                row.paddingTop,
                row.paddingRight,
                row.paddingBottom
            )
        }

        rvGantt.adapter = adapter

        // carrega as etapas
        collectState()

        // dispara primeira carga (se ainda não)
        viewModel.loadEtapas()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Combine obra + etapas para controlarmos um único "loading"
                kotlinx.coroutines.flow.combine(
                    viewModel.obraState,
                    viewModel.state
                ) { obra, etapasUi -> obra to etapasUi }
                    .collect { (obra, etapasUi) ->

                        // 1) Monte/valide o cabeçalho da obra
                        val ini = GanttUtils.brToLocalDateOrNull(obra?.dataInicio)
                        val fim = GanttUtils.brToLocalDateOrNull(obra?.dataFim)
                        val hasHeader = (ini != null && fim != null && !fim.isBefore(ini))
                        headerDays =
                            if (hasHeader) GanttUtils.daysBetween(ini!!, fim!!) else emptyList()

                        // 2) Estado da lista de etapas
                        when (etapasUi) {
                            is UiState.Loading -> {
                                // Enquanto qualquer lado não estiver pronto → loading
                                renderLoading(true)
                                return@collect
                            }

                            is UiState.ErrorRes -> {
                                // Erro: esconde conteúdo e mostra mensagem
                                renderLoading(false)
                                binding.headerContainer.isVisible = false
                                binding.rvGantt.isVisible = false
                                binding.textEmpty.isVisible = true
                                binding.textEmpty.setText(etapasUi.resId)
                                return@collect
                            }

                            is UiState.Success -> {
                                val lista = etapasUi.data
                                // Se o header NÃO estiver pronto ainda, aguarde (loading)
                                if (!hasHeader) {
                                    renderLoading(true)
                                    return@collect
                                }

                                // 3) Já temos header + etapas → renderiza TUDO de uma vez
                                buildHeaderViews() // monta as datas do topo (sem mexer em visibilidade)
                                adapter.submitList(lista) {
                                    // Mostrar tudo só depois que a lista aplicar o diff
                                    renderLoading(false)
                                    binding.textEmpty.isVisible = lista.isEmpty()
                                    if (lista.isEmpty()) binding.textEmpty.setText(R.string.gantt_empty)

                                    binding.headerContainer.isVisible = lista.isNotEmpty()
                                    binding.rvGantt.isVisible = lista.isNotEmpty()
                                }
                            }

                            else -> Unit
                        }
                    }
            }
        }
    }

    private fun renderLoading(show: Boolean) = with(binding) {
        progressGantt.isVisible = show
        // Conteúdo some enquanto carrega
        rvGantt.isVisible = !show
        headerContainer.isVisible = !show
        textEmpty.isVisible = false
    }

    private fun buildHeaderViews() = with(binding) {
        headerRow.removeAllViews()
        if (headerDays.isEmpty()) return@with

        val inflater = LayoutInflater.from(root.context)
        headerDays.forEach { d ->
            val tv = inflater.inflate(R.layout.item_gantt_header_day, headerRow, false) as ViewGroup
            val label = tv.findViewById<android.widget.TextView>(R.id.tvDayLabel)
            label.text = if (GanttUtils.isSunday(d))
                getString(R.string.gantt_sunday_short)      // "D"
            else
                GanttUtils.formatDayForHeader(d)
            if (GanttUtils.isSunday(d)) {
                val big = resources.getDimension(R.dimen.gantt_day_text_size_sunday)
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX, big)
            } else {
                val normal = resources.getDimension(R.dimen.gantt_day_text_size)
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX, normal)
            }
            headerRow.addView(tv)
        }
        adapter.attachHeaderScroll(binding.headerScroll)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter.detachHeaderScroll()
        _binding = null
    }
}