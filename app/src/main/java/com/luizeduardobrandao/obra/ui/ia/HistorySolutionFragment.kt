package com.luizeduardobrandao.obra.ui.ia

import android.os.Bundle
import android.view.*
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.SolutionHistory
import com.luizeduardobrandao.obra.databinding.FragmentHistorySolutionBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.ia.adapter.HistoryAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistorySolutionFragment : Fragment() {

    private var _binding: FragmentHistorySolutionBinding? = null
    private val binding get() = _binding!!

    private val args: HistorySolutionFragmentArgs by navArgs()
    private val viewModel: HistorySolutionViewModel by viewModels()

    private val adapter by lazy {
        HistoryAdapter(
            onClick = { showDetail(it) },
            onDelete = { confirmDelete(it) }
        )
    }

    // controla exibição do loader na primeira carga
    private var firstLoad = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistorySolutionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        toolbarHistory.setNavigationOnClickListener { findNavController().navigateUp() }

        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = adapter

        // estado inicial: mostra loader, esconde lista e vazio
        progressHistory.isVisible = true
        rvHistory.isGone = true
        tvEmptyHistory.isGone = true

        collectHistory()
        setupDetailOverlay()
    }

    private fun collectHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.history.collect { list ->
                    // a partir da 1ª emissão, some o loader
                    if (firstLoad) {
                        firstLoad = false
                        binding.progressHistory.isGone = true
                    }

                    val hasItems = list.isNotEmpty()
                    binding.rvHistory.isVisible = hasItems
                    binding.tvEmptyHistory.isVisible = !hasItems

                    adapter.submitList(list)
                }
            }
        }
    }

    private fun confirmDelete(item: SolutionHistory) {
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.history_delete_title),
            msg = getString(R.string.history_delete_msg),
            btnText = getString(R.string.snack_button_yes),
            onAction = {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.delete(item.id)
                }
            },
            btnNegativeText = getString(R.string.snack_button_no),
            onNegative = { /* nada */ }
        )
    }

    private fun setupDetailOverlay() = with(binding) {
        detailOverlay.setOnClickListener { detailOverlay.isGone = true }
        btnCloseDetail.setOnClickListener { detailOverlay.isGone = true }
    }

    private fun showDetail(item: SolutionHistory) = with(binding) {
        tvDetailTitle.text = item.title
        tvDetailDate.text = item.date
        tvDetailContent.text = item.content
        detailOverlay.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}