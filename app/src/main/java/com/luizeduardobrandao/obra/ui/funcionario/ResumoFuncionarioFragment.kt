package com.luizeduardobrandao.obra.ui.funcionario

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
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentResumoFuncionarioBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.funcionario.adapter.FuncionarioAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResumoFuncionarioFragment : Fragment() {

    private var _binding: FragmentResumoFuncionarioBinding? = null
    private val binding get() = _binding!!

    private val args: ResumoFuncionarioFragmentArgs by navArgs()
    private val viewModel: FuncionarioViewModel by viewModels()

    private val adapter by lazy { FuncionarioAdapter(showActions = false) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentResumoFuncionarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    /* ───────────────────────── lifecycle ───────────────────────── */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentResumoFuncionarioBinding.bind(view)

        binding.toolbarResumoFuncionario.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.rvFuncionarios.adapter = adapter

        observeViewModel()

        /*  disparo único do listener  */
        viewModel.loadFuncionarios()
    }

    /* ───────────────────────── observers ───────────────────────── */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading   -> showLoading()
                        is UiState.Success   -> renderList(ui.data)
                        is UiState.ErrorRes  -> showError(ui.resId)
                        else -> Unit
                    }
                }
            }
        }
    }

    /* ───────────────────────── UI helpers ──────────────────────── */
    private fun showLoading() = with(binding) {
        progressFuncList.visibility = View.VISIBLE
        rvFuncionarios .visibility  = View.GONE
        llTotalGeral    .visibility = View.GONE
        tvEmptySum      .visibility = View.GONE
    }

    private fun renderList(list: List<Funcionario>) = with(binding) {
        progressFuncList.visibility = View.GONE

        if (list.isEmpty()) {
            tvEmptySum.visibility      = View.VISIBLE
            rvFuncionarios.visibility  = View.GONE
            llTotalGeral.visibility    = View.GONE
        } else {
            tvEmptySum.visibility      = View.GONE
            rvFuncionarios.visibility  = View.VISIBLE
            llTotalGeral.visibility    = View.VISIBLE

            adapter.submitList(list)

            val total = list.sumOf { it.totalGasto }
            tvTotalGeral.text = getString(R.string.money_mask, total)
        }
    }

    private fun showError(resId: Int) {
        binding.progressFuncList.visibility = View.GONE
        showSnackbarFragment(
            Constants.SnackType.ERROR.name,
            getString(R.string.snack_error),
            getString(resId),
            getString(R.string.snack_button_ok)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}