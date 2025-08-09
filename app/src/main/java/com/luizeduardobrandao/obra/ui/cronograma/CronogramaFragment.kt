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

    /*---------- boilerplate ----------*/
    private var _binding: FragmentCronogramaBinding? = null
    private val binding get() = _binding!!
    private val args: CronogramaFragmentArgs by navArgs()
    private val viewModel: CronogramaViewModel by viewModels()
    /*---------------------------------*/

    private lateinit var pagerAdapter: CronogramaPagerAdapter

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

            // controla visibilidade do FAB: só na aba “Pendente” (index 0)
            fun updateFab(pos: Int) {
                fabNewEtapa.visibility = if (pos == 0) View.VISIBLE else View.GONE
            }
            updateFab(0)
            pagerCronograma.registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        updateFab(position)
                    }
                }
            )

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
            Constants.SnackType.WARNING.name,
            getString(R.string.snack_warning),
            getString(R.string.cron_snack_delete_msg),
            getString(R.string.snack_button_yes)
        ) {                               // onAction →
            viewModel.deleteEtapa(etapa)
            Toast.makeText(
                requireContext(),
                getString(R.string.cron_toast_removed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }
}