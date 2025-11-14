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
import androidx.appcompat.widget.PopupMenu
import android.view.Gravity
import android.view.ContextThemeWrapper

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

        // Pega o botão customizado do actionLayout
        val menuItem = toolbarNotas.menu.findItem(R.id.action_nota_menu)
        val anchor = menuItem.actionView?.findViewById<View>(R.id.btnSummaryMenu)

        anchor?.setOnClickListener {
            val themedCtx =
                ContextThemeWrapper(requireContext(), R.style.PopupMenu_WhiteBg_BlackText)
            val popup = PopupMenu(themedCtx, anchor, Gravity.END).apply {
                menuInflater.inflate(R.menu.menu_nota_popup, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_notas_to_resumo -> {
                            anchor.post {
                                if (isAdded) {
                                    findNavController().navigate(
                                        NotasFragmentDirections.actionNotasToResumo(args.obraId)
                                    )
                                }
                            }
                            true
                        }

                        else -> false
                    }
                }
            }
            popup.show()
        }

        /* Tabs & Pager */
        pagerAdapter = NotaPagerAdapter(this@NotasFragment, args.obraId)
        pagerNotas.adapter = pagerAdapter

        TabLayoutMediator(tabNotas, pagerNotas) { tab, pos ->
            tab.text = if (pos == 0) getString(R.string.nota_tab_due)
            else getString(R.string.nota_tab_paid)
        }.attach()

        pagerNotas.post {
            val target = args.startTab.coerceIn(0, 1)
            pagerNotas.currentItem = target
        }

        // FAB — criar nota
        fabNewNota.setOnClickListener {
            findNavController().navigate(
                NotasFragmentDirections.actionNotasToRegister(args.obraId, null)
            )
        }

        // FAB visível em todas as abas
        fun updateFabVisibility() {
            fabNewNota.isVisible = true
        }
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
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.generic_attention),
            msg = getString(R.string.nota_snack_delete_msg),
            btnText = getString(R.string.generic_yes_upper_case),          // SIM
            onAction = {
                viewModel.deleteNota(nota)
                Toast.makeText(
                    requireContext(),
                    R.string.nota_toast_removed,
                    Toast.LENGTH_SHORT
                ).show()
            },
            btnNegativeText = getString(R.string.generic_no_upper_case),   // NÃO
            onNegative = { /* nada: só fecha o SnackbarFragment */ }
        )
    }
}