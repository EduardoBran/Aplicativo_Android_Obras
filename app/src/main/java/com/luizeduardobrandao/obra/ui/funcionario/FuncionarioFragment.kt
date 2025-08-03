package com.luizeduardobrandao.obra.ui.funcionario

import android.os.Bundle
import android.view.*
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
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
        binding.toolbarFuncionario.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Toolbar Menu Provider (opção de resumo)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_funcionario, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_func_to_resumo -> {
                        findNavController().navigate(
                            FuncionarioFragmentDirections
                                .actionFuncToResumo(args.obraId)
                        )
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)


        // ViewPager2 + TabLayout
        pagerAdapter = FuncionarioPagerAdapter(this, args.obraId)
        binding.pagerFuncionario.adapter = pagerAdapter
        TabLayoutMediator(binding.tabFuncionario, binding.pagerFuncionario) { tab, pos ->
            tab.text = if (pos == 0) getString(R.string.func_tab_active)
            else getString(R.string.func_tab_inactive)
        }.attach()


        // FAB: visível apenas na aba 'Ativos' (pos 0)
        binding.fabNewFuncionario.setOnClickListener {
            findNavController().navigate(
                FuncionarioFragmentDirections.actionFuncToRegister(args.obraId, null)
            )
        }
        binding.pagerFuncionario.registerOnPageChangeCallback(object
            : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.fabNewFuncionario.visibility =
                    if (position == 0) View.VISIBLE else View.GONE
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