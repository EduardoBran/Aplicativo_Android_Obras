package com.luizeduardobrandao.obra.ui.material

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.databinding.FragmentDetalheMaterialBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DetalheMaterialFragment : Fragment() {

    private var _binding: FragmentDetalheMaterialBinding? = null
    private val binding get() = _binding!!

    private val args: DetalheMaterialFragmentArgs by navArgs()
    private val viewModel: MaterialViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetalheMaterialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            toolbarDetMaterial.setNavigationOnClickListener { findNavController().navigateUp() }

            // Observa APENAS o material pelo ID (não precisa carregar a lista toda)
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.observeMaterial(args.materialId)
                        .catch {
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.generic_error),
                                getString(R.string.material_load_error),
                                getString(R.string.generic_ok_upper_case)
                            )
                        }
                        .collect { material ->
                            if (material == null) {
                                // Não encontrado — mostra feedback e volta
                                showSnackbarFragment(
                                    Constants.SnackType.ERROR.name,
                                    getString(R.string.generic_error),
                                    getString(R.string.material_load_error),
                                    getString(R.string.generic_ok_upper_case)
                                )
                                return@collect
                            }

                            // Preenche UI
                            toolbarDetMaterial.title =
                                material.nome.ifBlank { getString(R.string.material_detail_title) }
                            tvDetNomeMaterial.text = material.nome.ifBlank { "—" }
                            tvDetDescricaoMaterial.text = material.descricao?.ifBlank { "—" } ?: "—"
                            tvDetQuantidadeMaterial.text = material.quantidade.toString()
                            tvDetStatusMaterial.text = material.status.ifBlank { "—" }
                        }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}