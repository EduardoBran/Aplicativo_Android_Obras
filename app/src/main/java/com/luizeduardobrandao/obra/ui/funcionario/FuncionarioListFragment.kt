package com.luizeduardobrandao.obra.ui.funcionario

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentFuncionarioListBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.funcionario.adapter.FuncionarioAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment que representa **uma aba** (Ativos ou Inativos) no ViewPager2
 * de [FuncionarioFragment].
 *
 * Espera receber, via `arguments`, os mesmos dois parâmetros usados pelo
 * `FuncionarioPagerAdapter.newInstance()`:
 *  • ARG_OBRA   → id da obra
 *  • ARG_STATUS → "ativo" | "inativo"
 *
 * O ViewModel é *compartilhado* com o fragment-pai (`FuncionarioFragment`)
 * usando `viewModels({ requireParentFragment() })`.
 */

@AndroidEntryPoint
class FuncionarioListFragment : Fragment() {

    // -------------------- Arguments --------------------
    private val obraId: String by lazy { requireArguments().getString(ARG_OBRA)!! }
    private val status: String by lazy { requireArguments().getString(ARG_STATUS)!! }

    companion object {
        private const val ARG_OBRA   = "obraId"
        private const val ARG_STATUS = "status"   // "ativo" | "inativo"

        fun newInstance(obraId: String, status: String) =
            FuncionarioListFragment().apply {
                arguments = bundleOf(ARG_OBRA to obraId, ARG_STATUS to status)
            }
    }

    // -------------------- ViewBinding --------------------
    private var _binding: FragmentFuncionarioListBinding? = null
    private val binding get() = _binding!!

    // -------------------- ViewModel (scoped ao fragment pai) --------------------
    private val viewModel: FuncionarioViewModel by viewModels({ requireParentFragment() })

    // -------------------- Adapter --------------------
    private val adapter by lazy {
        FuncionarioAdapter(
            onEdit = ::navigateEdit,
            onDetail = ::navigateDetail,
            onDelete = ::deleteFuncionario
        )
    }


    // -------------------- Lifecycle --------------------
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentFuncionarioListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvFuncionarios.adapter = adapter
        observeViewModel()

        // Pede ao ViewModel (compartilhado) que inicie/continue o listener
        // Apenas o pai dispara `loadFuncionarios()` para não duplicar escutas.
        //  (Portanto, nada a fazer aqui além de observar.)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    // -------------------- Observers --------------------
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.state.collect { ui ->
                        when (ui) {
                            is UiState.Loading -> showLoading()
                            is UiState.Success -> showList(ui.data)
                            is UiState.ErrorRes -> showError(ui.resId)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    // -------------------- UI helpers --------------------

    private fun showLoading() = with(binding) {
        progressFuncList.visibility = View.VISIBLE
        tvEmptyFunc.visibility = View.GONE
        rvFuncionarios.visibility = View.GONE
    }

    private fun showList(all: List<Funcionario>) = with(binding) {

        progressFuncList.visibility = View.GONE

        // filtra de acordo com a aba (“ativo” ou “inativo”)
        val filtered = all.filter { it.status.equals(status, ignoreCase = true) }

        if (filtered.isEmpty()) {
            tvEmptyFunc.visibility = View.VISIBLE
            rvFuncionarios.visibility = View.GONE
        } else {
            tvEmptyFunc.visibility = View.GONE
            rvFuncionarios.visibility = View.VISIBLE
            adapter.submitList(filtered)
        }
    }

    private fun showError(resId: Int) {
        binding.progressFuncList.visibility = View.GONE

        // Exibe mensagem padrão e mantém texto vazio visível
        showSnackbarFragment(
            Constants.SnackType.ERROR.name,
            getString(R.string.snack_error),
            getString(resId),
            getString(R.string.snack_button_ok)
        )
    }


    // -------------------- Adapter Callbacks --------------------

    private fun navigateEdit(func: Funcionario) {
        findNavController().navigate(
            FuncionarioFragmentDirections.actionFuncToRegister(obraId, func.id)
        )
    }

    private fun navigateDetail(func: Funcionario) {
        findNavController().navigate(
            FuncionarioFragmentDirections.actionFuncToDetail(obraId, func.id)
        )
    }

    private fun deleteFuncionario(func: Funcionario){
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.snack_attention),
            msg = getString(R.string.func_snack_delete_msg, func.nome),
            btnText = getString(R.string.snack_button_yes)
        ) {
            // só aqui, quando o usuário clicar em “Sim”, executamos
            viewModel.deleteFuncionario(func.id)
            Toast.makeText(
                requireContext(),
                getString(R.string.func_toast_removed, func.nome),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}