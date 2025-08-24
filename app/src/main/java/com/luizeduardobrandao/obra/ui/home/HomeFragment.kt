package com.luizeduardobrandao.obra.ui.home

import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentHomeBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    // Binding do layout (FragmentHomeBinding gerado)
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ViewModel injetado pelo Hilt
    private val viewModel: HomeViewModel by viewModels()

    // SafeArgs: pega o parâmetro obraId de forma tipada
    private val args: HomeFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Infla o layout e guarda o binding
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbarMenu()
        setupSectionClicks()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // evita memory leak
    }


    /**
     * Configura o menu de logout na Toolbar.
     * Atributo `app:menu="@menu/menu_home_logout"` deve estar definido em fragment_home.xml.
     */
    private fun setupToolbarMenu() {
        // 1) Clique no item de menu (fallback)
        binding.toolbarHome.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    confirmLogout()
                    true
                }

                else -> false
            }
        }

        // 2) Clique no actionView (layout customizado com ícone + texto)
        val logoutItem = binding.toolbarHome.menu.findItem(R.id.action_logout)
        logoutItem.actionView?.setOnClickListener {
            confirmLogout()
        }
    }

    private fun confirmLogout() {
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.snack_warning),
            msg = getString(R.string.home_logout_confirm_msg),
            btnText = getString(R.string.snack_button_yes),     // "SIM"
            onAction = { viewModel.logout() },                  // executa logout
            btnNegativeText = getString(R.string.snack_button_no), // "NÃO"
            onNegative = { /* opcional: nada; o sheet já é fechado */ }
        )
    }

    /**
     * Configura os cliques nos botões de cada seção,
     * passando o mesmo obraId para as navegações.
     */
    private fun setupSectionClicks() = with(binding) {
        val obraId = args.obraId

        // Funcionários
        btnEmployees.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToFuncionario(obraId)
            )
        }

        // Notas
        btnNotes.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToNotas(obraId)
            )
        }

        // Cronograma
        btnSchedule.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToCronograma(obraId)
            )
        }

        // Materiais
        btnMaterials.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToMaterial(obraId)
            )
        }

        // Dados da Obra
        btnProjectData.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToDadosObra(obraId)
            )
        }

        // Resumo
        btnSummary.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToResumo(obraId)
            )
        }

        // Fotos
        btnPhotos.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToFotos(obraId)
            )
        }
    }

    /**
     * Observa dois fluxos do ViewModel:
     * 1) obraState  → para atualizar o título da Toolbar
     * 2) logoutEvent → para navegar de volta ao login após o logout
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // 1) Observa alterações da Obra selecionada
                launch {
                    viewModel.obraState.collect { state ->
                        when (state) {
                            is UiState.Loading -> {
                                // enquanto carrega, mostra o ProgressBar e oculta o conteúdo
                                binding.progressHome.isVisible = true
                                binding.homeScroll.isGone = true
                            }

                            is UiState.Success -> {
                                // quando chegar o dado, esconde o ProgressBar e mostra o conteúdo
                                binding.progressHome.isGone = true
                                binding.homeScroll.isVisible = true

                                // atualiza título da Toolbar com nomeCliente
                                binding.toolbarHome.title =
                                    getString(R.string.home_toolbar_title, state.data.nomeCliente)
                            }

                            is UiState.ErrorRes -> {
                                // em erro, também esconde o loader e exibe o conteúdo (ou placeholder)
                                binding.progressHome.isGone = true
                                binding.homeScroll.isVisible = true

                                // exibe snackbar de erro
                                showSnackbarFragment(
                                    Constants.SnackType.ERROR.name,
                                    getString(R.string.snack_error),
                                    getString(state.resId),
                                    getString(R.string.snack_button_ok)
                                )
                            }

                            else -> Unit
                        }
                    }
                }

                // 2) Evento único de logout
                launch {
                    viewModel.logoutEvent.collect {
                        // navega limpando a pilha até LoginFragment
                        findNavController().navigate(
                            HomeFragmentDirections.actionHomeLogout()
                        )
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.home_toast_logout),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

        }
    }
}