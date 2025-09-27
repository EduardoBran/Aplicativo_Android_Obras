package com.luizeduardobrandao.obra.ui.cronograma.gantt

import android.annotation.SuppressLint
import android.os.Bundle
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
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentCronogramaGanttBinding
import com.luizeduardobrandao.obra.ui.cronograma.CronogramaViewModel
import com.luizeduardobrandao.obra.ui.cronograma.gantt.adapter.GanttRowAdapter
import com.luizeduardobrandao.obra.ui.cronograma.gantt.view.GanttTimelineView
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
    private var headerStart: LocalDate? = null
    private var headerEnd: LocalDate? = null
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
            row.setPadding(leftWidth + inset, row.paddingTop, row.paddingRight, row.paddingBottom)
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

                // 1) Obra (datas do cabeçalho)
                launch {
                    viewModel.obraState.collect { obra ->
                        val ini = GanttUtils.brToLocalDateOrNull(obra?.dataInicio)
                        val fim = GanttUtils.brToLocalDateOrNull(obra?.dataFim)
                        headerDays = if (ini != null && fim != null && !fim.isBefore(ini)) {
                            GanttUtils.daysBetween(ini, fim) // TODAS as datas da obra
                        } else emptyList()

                        // (re)constrói o cabeçalho sempre que a obra mudar (ex.: alterou término)
                        buildHeaderViews()

                        // pede rebind para alinhar as timelines ao novo cabeçalho
                        adapter.notifyDataSetChanged()
                    }
                }

                // 2) Etapas
                launch {
                    viewModel.state.collect { ui ->
                        when (ui) {
                            is UiState.Loading -> renderLoading(true)
                            is UiState.Success -> {
                                renderLoading(false)
                                // só preenche lista; header é controlado pela obra
                                adapter.submitList(ui.data)
                                binding.textEmpty.isVisible = ui.data.isEmpty()
                                if (ui.data.isEmpty()) binding.textEmpty.setText(R.string.gantt_empty)
                            }
                            is UiState.ErrorRes -> {
                                renderLoading(false)
                                binding.textEmpty.isVisible = true
                                binding.textEmpty.setText(ui.resId)
                                adapter.submitList(emptyList())
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }


    private fun renderLoading(show: Boolean) = with(binding) {
        progressGantt.isVisible = show
        rvGantt.isVisible = !show
        headerContainer.isVisible = !show
        textEmpty.isVisible = false
    }

    private fun buildHeaderViews() = with(binding) {
        headerRow.removeAllViews()

        if (headerDays.isEmpty()) {
            headerContainer.isVisible = false
            return@with
        }
        headerContainer.isVisible = true

        val inflater = LayoutInflater.from(root.context)
        headerDays.forEach { d ->
            val tv = inflater.inflate(R.layout.item_gantt_header_day, headerRow, false) as ViewGroup
            val label = tv.findViewById<android.widget.TextView>(R.id.tvDayLabel)
            // Domingos ficam em branco e são "não marcáveis" (o View cuidará disso)
            label.text = if (GanttUtils.isSunday(d)) "" else GanttUtils.formatDayForHeader(d)
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