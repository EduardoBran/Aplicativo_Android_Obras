package com.luizeduardobrandao.obra.ui.home

import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.view.get
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentHomeBinding
import com.luizeduardobrandao.obra.ui.cronograma.CronogramaViewModel
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.calcularProgressoGeralPorDias
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private val cronViewModel: CronogramaViewModel by viewModels()

    private val args: HomeFragmentArgs by navArgs()

    // ✅ use um nome diferente do RecyclerView.adapter
    private lateinit var homeAdapter: HomeAdapter

    private var barAnimatedOnce = false
    private var lastPct = 0

    // Estado Barra Pesquisa
    private var searchOpen = false
    private var searchQueryCache: String = ""

    // Controle de tempo mínimo do Lottie (1,5s)
    private var loadingStartMs: Long = 0L

    // Segura o progresso enquanto o Lottie está visível
    private var pendingPct: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)

//        barAnimatedOnce = savedInstanceState?.getBoolean("home_bar_animated_once", false) ?: false
        lastPct = savedInstanceState?.getInt("home_bar_last_pct", 0) ?: 0
//        binding.progressStatus.setProgress(lastPct, animate = false)

        // Restaurar barra de busca aberta na rotação
        val restoredSearchOpen = savedInstanceState?.getBoolean("home_search_open", false) ?: false
        val restoredQuery = savedInstanceState?.getString("home_search_query").orEmpty()
        if (restoredSearchOpen) {
            showSearchBar()                        // reanexa listeners e abre teclado
            binding.etSearch.setText(restoredQuery)
            binding.etSearch.setSelection(restoredQuery.length)
        }

        setupToolbar()
        setupHeader()
        setupList()
        observeViewModels()

        cronViewModel.loadEtapas()
    }

    private fun setupToolbar() = with(binding.toolbarHome) {
        navigationIcon = null

        val iconColor = requireContext().getColor(R.color.toolbarIcon)
        setNavigationIconTint(iconColor)
        navigationIcon?.setTint(iconColor)
        overflowIcon?.setTint(iconColor)
        forceShowOverflowMenuIcons()

        // Tinge ícones do menu
        for (i in 0 until menu.size) menu[i].icon?.setTint(iconColor)

        // Tamanho “N” único
        val iconScale = resources.getFraction(R.fraction.toolbar_icon_scale, 1, 1)

        // 1) Tentativa por drawable (pode ser ignorada pelo clamping do menu)
        scaleNavigationIcon(iconScale)
        scaleMenuItemIcon(R.id.action_search, iconScale)
        scaleOverflowIcon(iconScale)

        // 2) Fallback GARANTIDO: escala as VIEWS (lupa e três pontinhos)
        scaleActionViewsOnLayout(intArrayOf(R.id.action_search), iconScale)

        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    showSearchBar(); true
                }

                R.id.action_change_work -> {
                    findNavController().popBackStack(R.id.workFragment, false); true
                }

                R.id.action_edit -> {
                    findNavController().navigate(HomeFragmentDirections.actionHomeToDadosObra(args.obraId)); true
                }

                R.id.action_laser -> {
                    findNavController().navigate(HomeFragmentDirections.actionHomeToLevelMeter()); true
                }

                R.id.action_export -> {
                    findNavController().navigate(
                        HomeFragmentDirections.actionHomeToExportSummary(
                            args.obraId
                        )
                    ); true
                }

                R.id.action_logout -> {
                    confirmLogout(); true
                }

                else -> false
            }
        }
    }

    private fun setupHeader() = with(binding) {

        // Botão Barra de Progresso
        progressStatus.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToCronogramaGantt(args.obraId)
            )
        }

        // Botão Cronograma
        btnVerCronograma.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToCronograma(args.obraId)
            )
        }
    }

    private fun setupList() = with(binding.rvHome) {
        // ✅ instancie e guarde no fragment
        homeAdapter = HomeAdapter(::onActionClicked)

        layoutManager = LinearLayoutManager(requireContext())
        setHasFixedSize(true)

        // ✅ atribua ao RecyclerView
        adapter = homeAdapter

        val divider = MaterialDividerItemDecoration(
            context, MaterialDividerItemDecoration.VERTICAL
        ).apply { isLastItemDecorated = false }
        addItemDecoration(divider)

        // ✅ use a referência do fragment
        homeAdapter.submitList(HomeAction.defaultList())
    }

    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.obraState.collect { state ->
                        when (state) {
                            is UiState.Loading -> {
                                // Substitui progressHome por Lottie com tempo mínimo
                                showLoadingLottie()
                            }

                            is UiState.Success -> {
                                // Garante tempo mínimo do Lottie antes de mostrar conteúdo
                                hideLoadingLottieThen {
                                    binding.rvHome.isVisible = true
                                    binding.toolbarHome.title = getString(
                                        R.string.home_toolbar_title, state.data.nomeCliente
                                    )
                                }
                            }

                            is UiState.ErrorRes -> {
                                // Garante tempo mínimo do Lottie antes de mostrar erro + lista
                                hideLoadingLottieThen {
                                    binding.rvHome.isVisible = true
                                    showSnackbarFragment(
                                        Constants.SnackType.ERROR.name,
                                        getString(R.string.snack_error),
                                        getString(state.resId),
                                        getString(R.string.snack_button_ok)
                                    )
                                }
                            }

                            else -> Unit
                        }
                    }
                }

                launch {
                    cronViewModel.state.collect { ui ->
                        if (ui is UiState.Success) {
                            val pct = calcularProgressoGeralPorDias(ui.data)
                            lastPct = pct

                            if (binding.loadingHomeLottie.isVisible) {
                                // Adia a animação até o Lottie desaparecer
                                pendingPct = pct
                            } else {
                                // Comportamento original (somente quando Lottie já não está visível)
                                if (barAnimatedOnce) {
                                    binding.progressStatus.setProgress(pct, animate = false)
                                } else {
                                    binding.progressStatus.setProgress(0, animate = false)
                                    binding.progressStatus.setProgress(pct, animate = true)
                                    barAnimatedOnce = true
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.logoutEvent.collect {
                        findNavController().navigate(HomeFragmentDirections.actionHomeLogout())
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

    private fun onActionClicked(id: HomeAction.Id) {
        val obraId = args.obraId
        when (id) {
            HomeAction.Id.CRONOGRAMA ->
                findNavController().navigate(HomeFragmentDirections.actionHomeToCronograma(obraId))

            HomeAction.Id.FOTOS ->
                findNavController().navigate(HomeFragmentDirections.actionHomeToFotos(obraId))

            // ✅ NOVO CASE
            HomeAction.Id.CALC_MATERIAL ->
                // action sem argumentos; navegando por id é simples e robusto
                findNavController().navigate(R.id.action_home_to_calcMaterial)

            HomeAction.Id.IA ->
                findNavController().navigate(HomeFragmentDirections.actionHomeToIa(obraId))

            HomeAction.Id.NOTAS ->
                findNavController().navigate(HomeFragmentDirections.actionHomeToNotas(obraId))

            HomeAction.Id.MATERIAIS ->
                findNavController().navigate(HomeFragmentDirections.actionHomeToMaterial(obraId))

            HomeAction.Id.FUNCIONARIOS ->
                findNavController().navigate(HomeFragmentDirections.actionHomeToFuncionario(obraId))

            HomeAction.Id.RESUMO ->
                findNavController().navigate(HomeFragmentDirections.actionHomeToResumo(obraId))
        }
    }

    private fun confirmLogout() {
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.snack_warning),
            msg = getString(R.string.home_logout_confirm_msg),
            btnText = getString(R.string.snack_button_yes),
            onAction = { viewModel.logout() },
            btnNegativeText = getString(R.string.snack_button_no),
            onNegative = { }
        )
    }

    // Exibição de Campo de Pesquisa
    private fun showSearchBar() = with(binding) {
        searchOpen = true
        // mostra UI
        searchCard.visibility = View.VISIBLE
        searchScrim.visibility = View.VISIBLE

        // foco e teclado
        etSearch.requestFocus()
        val imm = requireContext().getSystemService<InputMethodManager>()
        etSearch.post { imm?.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT) }

        // end icon (chevron) dispara busca
        tilSearch.setEndIconOnClickListener {
            performSearchIfValid()
        }

        // IME action Go dispara busca
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                performSearchIfValid()
                true
            } else false
        }

        // tocar fora fecha busca
        searchScrim.setOnClickListener {
            hideSearchBar()
        }

        etSearch.doOnTextChanged { text, _, _, _ ->
            searchQueryCache = text?.toString() ?: ""
        }
    }

    private fun hideSearchBar() = with(binding) {
        searchOpen = false
        // teclado
        val imm = requireContext().getSystemService<InputMethodManager>()
        imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)

        // limpar foco e esconder UI
        etSearch.clearFocus()
        searchCard.visibility = View.GONE
        searchScrim.visibility = View.GONE
    }

    private fun performSearchIfValid() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        searchQueryCache = query
        if (query.length >= 3) {
            hideSearchBar()
            // Navega para a GlobalSearch (a lógica do query passaremos depois)
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToGlobalSearch(args.obraId, query)
            )
            // Se quiser manter a query para depois:
            // viewModel.setLastSearchQuery(query)
        } else {
            // só fecha o teclado e mantém o campo (ou use Snackbar/erro visual se preferir)
            binding.etSearch.error = getString(R.string.search_min_char_3) // string erro inline
        }
    }

    // --- Lottie helpers (tempo mínimo de 1.5s) ---
    private fun showLoadingLottie() = with(binding) {
        // Mostrar Lottie
        loadingHomeLottie.isVisible = true
        if (!loadingHomeLottie.isAnimating) loadingHomeLottie.playAnimation()

        // Esconder lista + toolbar + header
        rvHome.isGone = true
        toolbarHome.isGone = true
        headerCard.isGone = true

        // Marca início (apenas na primeira vez no ciclo)
        if (loadingStartMs == 0L) {
            loadingStartMs = SystemClock.elapsedRealtime()
        }
    }

    private fun hideLoadingLottieThen(actionAfterHide: () -> Unit) = with(binding) {
        val minMs = 1500L
        val elapsed = SystemClock.elapsedRealtime() - loadingStartMs
        val delay = if (elapsed >= minMs) 0L else (minMs - elapsed)

        loadingHomeLottie.postDelayed({
            // Para e esconde Lottie
            loadingHomeLottie.cancelAnimation()
            loadingHomeLottie.isGone = true
            loadingStartMs = 0L

            // Restaura toolbar e header
            toolbarHome.isVisible = true
            headerCard.isVisible = true

            // >>> Somente agora dispara a animação da barra de progresso, se houver pendência
            pendingPct?.let { pct ->
                if (barAnimatedOnce) {
                    progressStatus.setProgress(pct, animate = false)
                } else {
                    progressStatus.setProgress(0, animate = false)
                    progressStatus.setProgress(pct, animate = true)
                    barAnimatedOnce = true
                }
                pendingPct = null
            }

            // Executa a ação original (ex.: mostrar lista/atualizar título)
            actionAfterHide()
        }, delay)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
//        outState.putBoolean("home_bar_animated_once", barAnimatedOnce)
        outState.putInt("home_bar_last_pct", lastPct)

        outState.putBoolean("home_search_open", searchOpen)
        outState.putString("home_search_query", searchQueryCache)
    }

    override fun onDestroyView() {
        // cancela animação para evitar leaks
        binding.loadingHomeLottie.cancelAnimation()
        // boa prática para evitar leak de Adapter na view antiga
        binding.rvHome.adapter = null
        _binding = null
        super.onDestroyView()
    }
}