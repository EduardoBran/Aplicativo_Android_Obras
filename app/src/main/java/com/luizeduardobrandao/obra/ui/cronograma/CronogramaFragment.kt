package com.luizeduardobrandao.obra.ui.cronograma

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
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentCronogramaBinding
import com.luizeduardobrandao.obra.ui.cronograma.adapter.CronogramaPagerAdapter
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CronogramaFragment : Fragment(), EtapaActions {

    // boilerplate
    private var _binding: FragmentCronogramaBinding? = null
    private val binding get() = _binding!!
    private val args: CronogramaFragmentArgs by navArgs()
    private val viewModel: CronogramaViewModel by viewModels()

    // adapter
    private lateinit var pagerAdapter: CronogramaPagerAdapter

    // controle do fab
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCronogramaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            // toolbar back
            toolbarCronograma.setNavigationOnClickListener {
                findNavController().navigateUp()
            }

            // Pega o actionView do item de menu e registra o clique no botão interno
            val menuItem = toolbarCronograma.menu.findItem(R.id.action_open_gantt)
            val anchor = menuItem.actionView?.findViewById<View>(R.id.btnSummaryMenu)

            // Clique no botão custom da action view
            anchor?.setOnClickListener {
                findNavController().navigate(
                    CronogramaFragmentDirections.actionCronogramaToGantt(args.obraId)
                )
            }

            // pager + tabs
            pagerAdapter = CronogramaPagerAdapter(this@CronogramaFragment, args.obraId)
            pagerCronograma.adapter = pagerAdapter
            TabLayoutMediator(tabCronograma, pagerCronograma) { tab, pos ->
                tab.text = when (pos) {
                    0 -> getString(R.string.cron_tab_pending)
                    1 -> getString(R.string.cron_tab_progress)
                    else -> getString(R.string.cron_tab_done)
                }
            }.attach()

            // controla visibilidade do FAB (exibe nas 3 abas)
            fun updateFab(@Suppress("UNUSED_PARAMETER") pos: Int) {
                fabNewEtapa.show()
            }
            pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = updateFab(position)
            }.also { pagerCronograma.registerOnPageChangeCallback(it) }

            // posição inicial + garante FAB visível no primeiro draw
            pagerCronograma.post {
                val target = args.startTab.coerceIn(0, 2)
                pagerCronograma.currentItem = target
                updateFab(target)
            }

            // clique do FAB: cria nova etapa
            fabNewEtapa.setOnClickListener {
                findNavController().navigate(
                    CronogramaFragmentDirections
                        .actionCronogramaToRegister(args.obraId, null)
                )
            }
        }

        // observa erros de carregamento globais – os lists cuidam de Loading + Empty
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

        viewModel.loadEtapas()
    }

    override fun onResume() {
        super.onResume()
        // se a view ainda existe, reexibe com animação
        _binding?.fabNewEtapa?.show()
    }

    /*────────────────────────  EtapaActions (callbacks do Adapter) ────────────────────────*/
    override fun onEdit(etapa: Etapa) {
        findNavController().navigate(
            CronogramaFragmentDirections
                .actionCronogramaToRegister(args.obraId, etapa.id)
        )
    }

    override fun onDetail(etapa: Etapa) {
        findNavController().navigate(
            CronogramaFragmentDirections
                .actionCronogramaToDetail(args.obraId, etapa.id)
        )
    }

    override fun onDelete(etapa: Etapa) {
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.snack_warning),
            msg = getString(R.string.cron_snack_delete_msg),
            btnText = getString(R.string.snack_button_yes),       // SIM
            onAction = {
                viewModel.deleteEtapa(etapa)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.cron_toast_removed),
                    Toast.LENGTH_SHORT
                ).show()
            },
            btnNegativeText = getString(R.string.snack_button_no), // NÃO
            onNegative = { /* nada: apenas fecha o snackbar */ }
        )
    }

    override fun onDestroyView() {
        // desregistrar callback e soltar adapter
        pageChangeCallback?.let { binding.pagerCronograma.unregisterOnPageChangeCallback(it) }
        pageChangeCallback = null
        binding.pagerCronograma.adapter = null

        _binding = null
        super.onDestroyView()
    }
}