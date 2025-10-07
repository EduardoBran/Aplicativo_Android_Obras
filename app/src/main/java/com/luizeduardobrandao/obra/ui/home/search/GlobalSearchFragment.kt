package com.luizeduardobrandao.obra.ui.home.search

import android.os.Bundle
import android.view.View
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
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.Material
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.databinding.FragmentGlobalSearchBinding
import com.luizeduardobrandao.obra.ui.cronograma.adapter.EtapaAdapter
import com.luizeduardobrandao.obra.ui.funcionario.adapter.FuncionarioAdapter
import com.luizeduardobrandao.obra.ui.material.adapter.MaterialAdapter
import com.luizeduardobrandao.obra.ui.notas.adapter.NotaAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GlobalSearchFragment : Fragment(R.layout.fragment_global_search) {

    private var _binding: FragmentGlobalSearchBinding? = null
    private val binding get() = _binding!!

    private val args: GlobalSearchFragmentArgs by navArgs()
    private val viewModel: GlobalSearchViewModel by viewModels()

    // Adapters (sem botão excluir)
    private lateinit var etapaAdapter: EtapaAdapter
    private lateinit var funcAdapter: FuncionarioAdapter
    private lateinit var matAdapter: MaterialAdapter
    private lateinit var notaAdapter: NotaAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGlobalSearchBinding.bind(view)

        /* Toolbar */
        binding.toolbarSearch.apply {
            title = getString(R.string.search_result_title)

            // Cor do título
            setTitleTextColor(
                androidx.core.content.ContextCompat.getColor(context, android.R.color.white)
            )

            // Ícone de navegação e cor
            setNavigationIcon(R.drawable.ic_back)
            navigationIcon?.setTint(
                androidx.core.content.ContextCompat.getColor(context, android.R.color.white)
            )

            setNavigationOnClickListener { findNavController().navigateUp() }
        }

        // Subtítulo “Resultados da busca: …”
        binding.tvSubtitle.text =
            getString(R.string.search_results_subtitle, args.query)

        setupLists()
        observeUi()
    }

    private fun setupLists() = with(binding) {
        // Cronograma
        etapaAdapter = EtapaAdapter(
            getFuncionarios = { viewModel.latestFuncionarios.value },
            onEdit   = ::onEditEtapa,
            onDetail = ::onDetailEtapa,
            onDelete = { /* nunca usado aqui */ }
        ).apply { showDelete = false } // << flag que adicionaremos no adapter

        rvCronograma.layoutManager = LinearLayoutManager(requireContext())
        rvCronograma.adapter = etapaAdapter
        rvCronograma.setHasFixedSize(false)
        rvCronograma.isNestedScrollingEnabled = false

        // Funcionário (oculta somente excluir)
        funcAdapter = FuncionarioAdapter(
            onEdit   = ::onEditFuncionario,
            onDetail = ::onDetailFuncionario,
            onDelete = { /* oculto */ },
            showActions = true,
            showDelete = false
        )

        rvFuncionario.layoutManager = LinearLayoutManager(requireContext())
        rvFuncionario.adapter = funcAdapter
        rvFuncionario.setHasFixedSize(false)
        rvFuncionario.isNestedScrollingEnabled = false

        // Material (oculta somente excluir)
        matAdapter = MaterialAdapter(
            onEdit   = ::onEditMaterial,
            onDetail = ::onDetailMaterial,
            onDelete = { /* oculto */ },
            showActions = true,
            showDelete = false
        )

        rvMaterial.layoutManager = LinearLayoutManager(requireContext())
        rvMaterial.adapter = matAdapter
        rvMaterial.setHasFixedSize(false)
        rvMaterial.isNestedScrollingEnabled = false

        // Nota (oculta somente excluir)
        notaAdapter = NotaAdapter(
            onEdit   = ::onEditNota,
            onDetail = ::onDetailNota,
            onDelete = { /* oculto */ },
            showActions = true,
            showDelete = false
        )

        rvNota.layoutManager = LinearLayoutManager(requireContext())
        rvNota.adapter = notaAdapter
        rvNota.setHasFixedSize(false)
        rvNota.isNestedScrollingEnabled = false
    }

    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ui.collect { ui ->
                    when (ui) {
                        is GlobalSearchViewModel.Ui.Loading -> showLoading()
                        is GlobalSearchViewModel.Ui.Success -> showSuccess(
                            ui.etapas, ui.funcionarios, ui.materiais, ui.notas, ui.total, ui.query
                        )
                        is GlobalSearchViewModel.Ui.Error -> showError(ui.message)
                    }
                }
            }
        }
    }

    private fun showLoading() = with(binding) {
        progressBar.isVisible = true
        scroll.isGone = true          // << o pai do conteúdo
        emptyGroup.isGone = true
    }

    private fun showSuccess(
        etapas: List<Etapa>,
        funcs: List<Funcionario>,
        mats: List<Material>,
        notas: List<Nota>,
        total: Int,
        q: String
    ) = with(binding) {
        progressBar.isGone = true
        tvSubtitle.text = getString(R.string.search_results_subtitle, q)

        if (total == 0) {
            scroll.isGone = true
            emptyGroup.isVisible = true
            tvEmpty.text = getString(R.string.search_empty)
            return@with
        }

        emptyGroup.isGone = true
        scroll.isVisible = true       // << MOSTRA o NestedScrollView

        // preenche listas normalmente…
        etapaAdapter.submitList(etapas)
        funcAdapter.submitList(funcs)
        matAdapter.submitList(mats)
        notaAdapter.submitList(notas)

        sectionCronograma.isVisible = etapas.isNotEmpty()
        rvCronograma.isVisible = etapas.isNotEmpty()
        dividerCronograma.isVisible = etapas.isNotEmpty()

        sectionFuncionario.isVisible = funcs.isNotEmpty()
        rvFuncionario.isVisible = funcs.isNotEmpty()
        dividerFuncionario.isVisible = funcs.isNotEmpty()

        sectionMaterial.isVisible = mats.isNotEmpty()
        rvMaterial.isVisible = mats.isNotEmpty()
        dividerMaterial.isVisible = mats.isNotEmpty()

        sectionNota.isVisible = notas.isNotEmpty()
        rvNota.isVisible = notas.isNotEmpty()
        dividerNota.isVisible = notas.isNotEmpty()

        tvTotal.text = resources.getQuantityString(
            R.plurals.search_total_elements, total, total
        )
    }

    private fun showError(msg: String) = with(binding) {
        progressBar.isGone = true
        scroll.isGone = true          // << oculta o container
        emptyGroup.isVisible = true
        tvEmpty.text = msg
    }

    /* ───────────── Navegações (Editar / Detalhes) ───────────── */

    private fun onEditEtapa(etapa: Etapa) {
        findNavController().navigate(
            GlobalSearchFragmentDirections.actionGlobalSearchToEditEtapa(
                args.obraId, etapa.id
            )
        )
    }

    private fun onDetailEtapa(etapa: Etapa) {
        findNavController().navigate(
            GlobalSearchFragmentDirections.actionGlobalSearchToDetailEtapa(
                args.obraId, etapa.id
            )
        )
    }

    private fun onEditFuncionario(func: Funcionario) {
        findNavController().navigate(
            GlobalSearchFragmentDirections.actionGlobalSearchToEditFuncionario(
                args.obraId, func.id
            )
        )
    }

    private fun onDetailFuncionario(func: Funcionario) {
        findNavController().navigate(
            GlobalSearchFragmentDirections.actionGlobalSearchToDetailFuncionario(
                args.obraId, func.id
            )
        )
    }

    private fun onEditMaterial(mat: Material) {
        findNavController().navigate(
            GlobalSearchFragmentDirections.actionGlobalSearchToEditMaterial(
                args.obraId, mat.id
            )
        )
    }

    private fun onDetailMaterial(mat: Material) {
        findNavController().navigate(
            GlobalSearchFragmentDirections.actionGlobalSearchToDetailMaterial(
                args.obraId, mat.id
            )
        )
    }

    private fun onEditNota(nota: Nota) {
        findNavController().navigate(
            GlobalSearchFragmentDirections.actionGlobalSearchToEditNota(
                args.obraId, nota.id
            )
        )
    }

    private fun onDetailNota(nota: Nota) {
        findNavController().navigate(
            GlobalSearchFragmentDirections.actionGlobalSearchToDetailNota(
                args.obraId, nota.id
            )
        )
    }

    override fun onDestroyView() {
        binding.rvCronograma.adapter = null
        binding.rvFuncionario.adapter = null
        binding.rvMaterial.adapter = null
        binding.rvNota.adapter = null
        _binding = null
        super.onDestroyView()
    }
}