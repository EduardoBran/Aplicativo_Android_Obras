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

    private val pagerAdapter by lazy { NotaPagerAdapter(this, args.obraId) }


    /*───────────────────────────── Ciclo de Vida ─────────────────────────────*/

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
        pagerNotas.adapter = pagerAdapter
        TabLayoutMediator(tabNotas, pagerNotas) { tab, pos ->
            tab.text = if (pos == 0) getString(R.string.nota_tab_due)
            else       getString(R.string.nota_tab_paid)
        }.attach()

        /* FAB – visível apenas na aba “A Pagar” (pos 0) */
        fabNewNota.setOnClickListener {
            findNavController().navigate(
                NotasFragmentDirections.actionNotasToRegister(
                    obraId = args.obraId, notaId = null        // novo cadastro
                )
            )
        }
        pagerNotas.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                fabNewNota.isVisible = position == 0
            }
        })
        fabNewNota.isVisible = true         // começa em “A Pagar”
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /*───────────────────────────── NotaActions ───────────────────────────────*/

    override fun onEdit(nota: Nota) {
        NotasFragmentDirections.actionNotasToRegister(args.obraId, nota.id)
    }

    override fun onDetail(nota: Nota) {
        NotasFragmentDirections.actionNotasToDetail(args.obraId, nota.id)
    }

    override fun onDelete(nota: Nota) {
        /* Confirmação via SnackbarFragment */
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