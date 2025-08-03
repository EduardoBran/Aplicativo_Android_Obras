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
    private val viewModel: FuncionarioViewModel by viewModels({ requireParentFragment() })

    private val adapter by lazy { FuncionarioAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentResumoFuncionarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbarResumoFuncionario.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.rvFuncionarios.adapter = adapter
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> binding.progressFuncList.visibility = View.VISIBLE
                        is UiState.Success -> showList(ui.data)
                        is UiState.ErrorRes -> showError(ui.resId)
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun showList(list: List<Funcionario>) = with(binding) {
        progressFuncList.visibility = View.GONE
        adapter.submitList(list)
        val total = list.sumOf { it.totalGasto }
        tvTotalGeral.text = getString(R.string.money_mask, total)
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