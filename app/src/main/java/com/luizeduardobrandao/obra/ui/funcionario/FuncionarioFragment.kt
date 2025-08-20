package com.luizeduardobrandao.obra.ui.funcionario

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.tabs.TabLayoutMediator
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.databinding.FragmentFuncionarioBinding
import com.luizeduardobrandao.obra.ui.funcionario.adapter.FuncionarioPagerAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FuncionarioFragment : Fragment() {

    private var _binding: FragmentFuncionarioBinding? = null
    private val binding get() = _binding!!

    private val args: FuncionarioFragmentArgs by navArgs()

    private val viewModel: FuncionarioViewModel by viewModels()

    private lateinit var pagerAdapter: FuncionarioPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFuncionarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar navegação
        // Mantemos apenas o listener direto da Toolbar
        binding.toolbarFuncionario.setNavigationOnClickListener { findNavController().navigateUp() }

        // anchor = o ImageButton do actionView
        val menuItem = binding.toolbarFuncionario.menu.findItem(R.id.action_func_menu)
        val anchor = menuItem.actionView?.findViewById<View>(R.id.btnSummaryMenu)

        anchor?.setOnClickListener {
            val themed = ContextThemeWrapper(requireContext(), R.style.PopupMenu_WhiteBg_BlackText)
            val popup = PopupMenu(themed, anchor, Gravity.END).apply {
                // ⚠️ Use o menu SEM actionLayout
                menuInflater.inflate(R.menu.menu_funcionario_popup, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_func_to_resumo -> {
                            // posta a navegação p/ evitar conflito com o fechamento do popup
                            anchor.post {
                                if (isAdded) {
                                    findNavController().navigate(
                                        FuncionarioFragmentDirections.actionFuncToResumo(args.obraId)
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


        // ViewPager2 + TabLayout
        pagerAdapter = FuncionarioPagerAdapter(this, args.obraId)
        binding.pagerFuncionario.adapter = pagerAdapter
        TabLayoutMediator(binding.tabFuncionario, binding.pagerFuncionario) { tab, pos ->
            tab.text = if (pos == 0) getString(R.string.func_tab_active)
            else getString(R.string.func_tab_inactive)
        }.attach()

        binding.pagerFuncionario.post {
            binding.pagerFuncionario.currentItem = args.startTab.coerceIn(0, 1)
        }

        binding.fabNewFuncionario.visibility = View.VISIBLE


        // FAB: visível apenas na aba 'Ativos' (pos 0)
        binding.fabNewFuncionario.setOnClickListener {
            findNavController().navigate(
                FuncionarioFragmentDirections.actionFuncToRegister(args.obraId, null)
            )
        }
        binding.pagerFuncionario.registerOnPageChangeCallback(object
            : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.fabNewFuncionario.visibility = View.VISIBLE
            }
        })


        // Inicia listener de funcionários (uma vez)
        viewModel.loadFuncionarios()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}