package com.luizeduardobrandao.obra.ui.notas

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentNotaListBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.notas.adapter.NotaAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotaListFragment : Fragment() {

    private var _binding: FragmentNotaListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotasViewModel by viewModels({ requireParentFragment() })

    private lateinit var obraId: String
    private lateinit var status: String

    // call-backs para o adapter (delegamos ao fragmento pai NotasFragment)
    private var actions: NotaActions? = null

    private val adapter by lazy {
        NotaAdapter(
            onEdit = { actions?.onEdit(it) },
            onDetail = { actions?.onDetail(it) },
            onDelete = { actions?.onDelete(it) }
        )
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // parent fragment (NotasFragment) deve implementar NotaActions
        actions = parentFragment as? NotaActions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            obraId = it.getString(ARG_OBRA) ?: error("obraId ausente")
            status = it.getString(ARG_STATUS) ?: error("status ausente")
        }
        // Dispara o carregamento
        viewModel.loadNotas()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentNotaListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            // 👇 sem isso a lista não aparece
            rvNotas.layoutManager =
                androidx.recyclerview.widget.LinearLayoutManager(requireContext())

            rvNotas.adapter = adapter
            observeViewModel()
        }
    }

    private fun observeViewModel() = with(binding) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> {
                            progressNotasList.visibility = View.VISIBLE
                        }

                        is UiState.Success -> {
                            progressNotasList.visibility = View.GONE
                            val comparator = compareBy<Nota> { dataToEpoch(it.data) }
                                .thenBy { it.nomeMaterial.lowercase(java.util.Locale.ROOT) }

                            val list = ui.data
                                .filter { it.status == status }
                                .sortedWith(comparator)

                            adapter.submitList(list.toList())

                            rvNotas.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                            tvEmptyNotas.visibility =
                                if (list.isEmpty()) View.VISIBLE else View.GONE
                        }

                        is UiState.ErrorRes -> {
                            progressNotasList.visibility = View.GONE
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

    private fun dataToEpoch(data: String?): Long {
        if (data.isNullOrBlank()) return Long.MIN_VALUE
        val s = data.trim()
        val formatos = arrayOf(
            "dd/MM/yyyy",   // 01/08/2025
            "d/M/yyyy",     // 1/8/2025
            "d/MM/yyyy",    // 1/08/2025
            "dd/M/yyyy"     // 01/8/2025
        )
        for (f in formatos) {
            try {
                val sdf = java.text.SimpleDateFormat(f, java.util.Locale("pt", "BR"))
                sdf.isLenient = false
                val time = sdf.parse(s)?.time
                if (time != null) return time
            } catch (_: Exception) { /* tenta o próximo formato */
            }
        }
        return Long.MIN_VALUE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /* ---------- companion / factory ---------- */
    companion object {
        private const val ARG_OBRA = "obraId"
        private const val ARG_STATUS = "status"

        /** Factory para o *ViewPager* */
        fun newInstance(obraId: String, status: String): NotaListFragment =
            NotaListFragment().apply {
                arguments = bundleOf(
                    ARG_OBRA to obraId,
                    ARG_STATUS to status
                )
            }
    }
}