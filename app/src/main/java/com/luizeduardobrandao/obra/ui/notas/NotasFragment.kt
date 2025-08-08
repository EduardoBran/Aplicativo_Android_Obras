package com.luizeduardobrandao.obra.ui.notas

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.databinding.FragmentNotasBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.notas.adapter.NotaPagerAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NotasFragment : Fragment(), NotaActions {

    private var _binding: FragmentNotasBinding? = null
    private val binding get() = _binding!!

    private val args: NotasFragmentArgs by navArgs()
    private val viewModel: NotasViewModel by viewModels()

    // NÃO manter adapter como lazy de campo imutável
    private var pagerAdapter: NotaPagerAdapter? = null
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        /* Toolbar */
        toolbarNotas.setNavigationOnClickListener { findNavController().navigateUp() }
        toolbarNotas.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_notas_to_resumo) {
                findNavController().navigate(
                    NotasFragmentDirections.actionNotasToResumo(args.obraId)
                )
                true
            } else false
        }

        /* Tabs & Pager */
        pagerAdapter = NotaPagerAdapter(this@NotasFragment, args.obraId)
        pagerNotas.adapter = pagerAdapter

        TabLayoutMediator(tabNotas, pagerNotas) { tab, pos ->
            tab.text = if (pos == 0) getString(R.string.nota_tab_due)
            else                     getString(R.string.nota_tab_paid)
        }.attach()

        // FAB — criar nota
        fabNewNota.setOnClickListener {
            findNavController().navigate(
                NotasFragmentDirections.actionNotasToRegister(args.obraId, null)
            )
        }

        // FAB visível somente na aba 0 (A Pagar)
        fun updateFabVisibility() { fabNewNota.isVisible = pagerNotas.currentItem == 0 }
        updateFabVisibility()

        pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateFabVisibility()
        }.also { pagerNotas.registerOnPageChangeCallback(it) }
    }

    override fun onDestroyView() {
        // Desanexar tudo para não reaproveitar referências da old view (evita o crash)
        pageChangeCallback?.let { binding.pagerNotas.unregisterOnPageChangeCallback(it) }
        pageChangeCallback = null
        binding.pagerNotas.adapter = null
        pagerAdapter = null
        _binding = null
        super.onDestroyView()
    }

    /*────────────── NotaActions (Adapter callbacks) ─────────────*/
    override fun onEdit(nota: Nota) {
        findNavController().navigate(
            NotasFragmentDirections.actionNotasToRegister(args.obraId, nota.id)
        )
    }

    override fun onDetail(nota: Nota) {
        findNavController().navigate(
            NotasFragmentDirections.actionNotasToDetail(args.obraId, nota.id)
        )
    }

    override fun onDelete(nota: Nota) {
        showSnackbarFragment(
            Constants.SnackType.WARNING.name,
            getString(R.string.snack_attention),
            getString(R.string.nota_snack_delete_msg),
            getString(R.string.snack_button_yes)
        ) {
            viewModel.deleteNota(nota)
            Toast.makeText(
                requireContext(), R.string.nota_toast_removed, Toast.LENGTH_SHORT
            ).show()
        }
    }
}