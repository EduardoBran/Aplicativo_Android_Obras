package com.luizeduardobrandao.obra.ui.material

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Material
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentMaterialBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.material.adapter.MaterialPagerAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MaterialFragment : Fragment(), MaterialActions {

    private var _binding: FragmentMaterialBinding? = null
    private val binding get() = _binding!!

    private val args: MaterialFragmentArgs by navArgs()
    private val viewModel: MaterialViewModel by viewModels()

    private lateinit var pagerAdapter: MaterialPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMaterialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar back
        toolbarMaterial.setNavigationOnClickListener { findNavController().navigateUp() }

        // Pager + Tabs
        pagerAdapter = MaterialPagerAdapter(this@MaterialFragment, args.obraId)
        pagerMaterial.adapter = pagerAdapter

        TabLayoutMediator(tabMaterial, pagerMaterial) { tab, pos ->
            tab.text = when (pos) {
                0 -> getString(R.string.material_status_active)   // "Ativo"
                else -> getString(R.string.material_status_inactive) // "Inativo"
            }
        }.attach()

        // FAB só visível na aba “Ativo” (index 0)
        fun updateFab(pos: Int) {
            fabNewMaterial.visibility = if (pos == 0) View.VISIBLE else View.GONE
        }
        updateFab(0)
        pagerMaterial.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateFab(position)
        })

        // Clique do FAB → tela de cadastro
        fabNewMaterial.setOnClickListener {
            findNavController().navigate(
                MaterialFragmentDirections.actionMaterialToRegister(args.obraId, null)
            )
        }

        // Observa apenas erros globais (os lists tratam Loading/Empty)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    if (ui is UiState.ErrorRes) {
                        showSnackbarFragment(
                            Constants.SnackType.ERROR.name,
                            getString(R.string.snack_error),
                            getString(ui.resId),
                            getString(R.string.snack_button_ok)
                        )
                    }
                }
            }
        }

        // dispara o listener
        viewModel.loadMateriais()
    }

    /*────────────  MaterialActions (callbacks do Adapter) ────────────*/
    override fun onEdit(material: Material) {
        findNavController().navigate(
            MaterialFragmentDirections.actionMaterialToRegister(args.obraId, material.id)
        )
    }

    override fun onDetail(material: Material) {
        findNavController().navigate(
            MaterialFragmentDirections.actionMaterialToDetail(args.obraId, material.id)
        )
    }

    override fun onDelete(material: Material) {
        showSnackbarFragment(
            Constants.SnackType.WARNING.name,
            getString(R.string.snack_warning),
            getString(R.string.material_delete_confirm),
            getString(R.string.snack_button_yes)
        ) {
            viewModel.deleteMaterial(material.id)
            Toast.makeText(
                requireContext(),
                getString(R.string.material_delete_success),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}