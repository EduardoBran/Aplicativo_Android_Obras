package com.luizeduardobrandao.obra.ui.material

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentMaterialListBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.material.adapter.MaterialAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Uma das “páginas” do ViewPager2 de Materiais.
 * Recebe [status] (“Ativo” ou “Inativo”) via arguments.
 */

@AndroidEntryPoint
class MaterialListFragment : Fragment() {

    private var _binding: FragmentMaterialListBinding? = null
    private val binding get() = _binding!!

    // ViewModel compartilhado com o fragmento pai
    private val viewModel: MaterialViewModel by viewModels({ requireParentFragment() })

    private lateinit var obraId: String
    private lateinit var status: String

    /* Call-backs delegadas ao MaterialFragment */
    private var actions: MaterialActions? = null

    private val adapter by lazy {
        MaterialAdapter(
            onEdit = { actions?.onEdit(it) },
            onDetail = { actions?.onDetail(it) },
            onDelete = { actions?.onDelete(it) }
        )
    }

    /*──────────── Attach / Args ────────────*/
    override fun onAttach(context: Context) {
        super.onAttach(context)
        actions = parentFragment as? MaterialActions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            obraId  = it.getString(ARG_OBRA)   ?: error("obraId ausente")
            status  = it.getString(ARG_STATUS) ?: error("status ausente")
        }
    }

    /*──────────── UI ────────────*/
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMaterialListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            rvMateriais.apply {
                layoutManager = LinearLayoutManager(requireContext())
                setHasFixedSize(true)
                itemAnimator = DefaultItemAnimator().apply { supportsChangeAnimations = false }
                adapter = this@MaterialListFragment.adapter
            }
        }

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
                            val list = ui.data.filter { it.status == status }
                            adapter.submitList(list)

                            rvMateriais.isVisible     = list.isNotEmpty()
                            tvEmptyMateriais.isVisible = list.isEmpty()
                        }
                        is UiState.ErrorRes -> {
                            progress(false)
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.generic_error),
                                getString(R.string.material_load_error),
                                getString(R.string.generic_ok_upper_case)
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun progress(show: Boolean) = with(binding) {
        progressMateriais.isVisible = show
        rvMateriais.isVisible       = !show
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /*──────────── Companion / Factory ────────────*/
    companion object {
        private const val ARG_OBRA   = "obraId"
        private const val ARG_STATUS = "status"

        fun newInstance(obraId: String, status: String) =
            MaterialListFragment().apply {
                arguments = bundleOf(
                    ARG_OBRA   to obraId,
                    ARG_STATUS to status
                )
            }
    }

}