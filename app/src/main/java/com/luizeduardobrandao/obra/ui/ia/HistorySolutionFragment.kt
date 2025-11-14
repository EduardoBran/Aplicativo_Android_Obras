package com.luizeduardobrandao.obra.ui.ia

import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.view.ViewTreeObserver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.SolutionHistory
import com.luizeduardobrandao.obra.databinding.FragmentHistorySolutionBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.ia.adapter.HistoryAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistorySolutionFragment : Fragment() {

    private var _binding: FragmentHistorySolutionBinding? = null
    private val binding get() = _binding!!

    private val args: HistorySolutionFragmentArgs by navArgs()
    private val viewModel: HistorySolutionViewModel by viewModels()

    // FAB
    private var detailGlobalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val adapter by lazy {
        HistoryAdapter(
            onClick = { showDetail(it) },
            onDelete = { confirmDelete(it) }
        )
    }

    // controla exibição do loader na primeira carga
    private var firstLoad = true

    // controla o timestamp de quando o loader foi exibido
    private var progressShownAt: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistorySolutionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        toolbarHistory.setNavigationOnClickListener { findNavController().navigateUp() }

        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = adapter

        // estado inicial: mostra loader, esconde lista e vazio
        progressHistory.isVisible = true
        progressShownAt = SystemClock.elapsedRealtime() // 1 segundo de loading
        rvHistory.isGone = true
        tvEmptyHistory.isGone = true

        collectHistory()
        setupDetailOverlay()
        setupDetailFabBehavior()

        // Restaura o card aberto (se estava visível) e o scroll/FABs
        savedInstanceState?.let { st ->
            if (st.getBoolean(STATE_DETAIL_OPEN, false)) {
                binding.tvDetailTitle.text = st.getString(STATE_DETAIL_TITLE).orEmpty()
                binding.tvDetailDate.text = st.getString(STATE_DETAIL_DATE).orEmpty()
                binding.tvDetailContent.text = st.getString(STATE_DETAIL_CONTENT).orEmpty()
                binding.detailOverlay.isVisible = true

                val y = st.getInt(STATE_DETAIL_SCROLL_Y, 0)
                binding.scrollDetail.post {
                    binding.scrollDetail.scrollTo(0, y)
                    updateDetailFabVisibility()
                }
            }
        }

        Unit
    }

    private fun collectHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.history.collect { list ->
                    // a partir da 1ª emissão, some o loader
                    if (firstLoad) {
                        firstLoad = false
                        val elapsed = SystemClock.elapsedRealtime() - progressShownAt
                        val remaining = 1_000L - elapsed
                        if (remaining > 0) delay(remaining)
                        binding.progressHistory.isGone = true
                    }

                    val hasItems = list.isNotEmpty()
                    binding.rvHistory.isVisible = hasItems
                    binding.tvEmptyHistory.isVisible = !hasItems

                    adapter.submitList(list)
                }
            }
        }
    }

    private fun confirmDelete(item: SolutionHistory) {
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.history_delete_title),
            msg = getString(R.string.history_delete_msg),
            btnText = getString(R.string.generic_yes_upper_case),
            onAction = {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.delete(item.id)
                }
            },
            btnNegativeText = getString(R.string.generic_no_upper_case),
            onNegative = { /* nada */ }
        )
    }

    private fun setupDetailOverlay() = with(binding) {
        detailOverlay.setOnClickListener { detailOverlay.isGone = true }
        btnCloseDetail.setOnClickListener { detailOverlay.isGone = true }

        // ✅ Novo: copiar TÍTULO + CONTEÚDO
        btnCopyDetail.setOnClickListener {
            val title = tvDetailTitle.text?.toString().orEmpty()
            val content = tvDetailContent.text?.toString().orEmpty()
            val toCopy = buildString {
                appendLine(title)
                appendLine()
                append(content)
            }
            val clipboard = requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText("IA History", toCopy)
            )
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.generic_copy_success),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showDetail(item: SolutionHistory) = with(binding) {
        tvDetailTitle.text = item.title
        tvDetailDate.text = item.date
        tvDetailContent.text = item.content
        detailOverlay.isVisible = true

        // Sempre abre no topo e recalcula os FABs (como no IaFragment)
        scrollDetail.post {
            scrollDetail.scrollTo(0, 0)
            updateDetailFabVisibility()
        }
    }

    /** Comportamento Fab */
    private fun setupDetailFabBehavior() = with(binding) {
        // Estado inicial
        fabScrollDownDetail.isGone = true
        fabScrollUpDetail.isGone = true

        // Mostra/oculta conforme rolagem do usuário
        scrollDetail.setOnScrollChangeListener { _, _, _, _, _ ->
            updateDetailFabVisibility()
        }

        // Reavalia quando o layout “assentar” (ex.: texto grande)
        detailGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            updateDetailFabVisibility()
        }
        scrollDetail.viewTreeObserver.addOnGlobalLayoutListener(detailGlobalLayoutListener)

        // Clique: descer até o fim → troca pro FAB de subir
        fabScrollDownDetail.setOnClickListener {
            scrollDetail.post {
                val bottom = scrollDetail.getChildAt(0)?.bottom ?: 0
                scrollDetail.smoothScrollTo(0, bottom)
                fabScrollDownDetail.isGone = true
                scrollDetail.post { updateDetailFabVisibility() }
            }
        }

        // Clique: subir ao topo → troca pro FAB de descer
        fabScrollUpDetail.setOnClickListener {
            scrollDetail.post {
                scrollDetail.smoothScrollTo(0, 0)
                fabScrollUpDetail.isGone = true
                scrollDetail.post { updateDetailFabVisibility() }
            }
        }
    }

    private fun updateDetailFabVisibility() {
        val b = _binding ?: return  // view já destruída -> não faz nada
        with(b) {
            val child = scrollDetail.getChildAt(0)
            val hasScrollable = child != null && child.height > scrollDetail.height
            val atBottom = !scrollDetail.canScrollVertically(1)

            val showDown = hasScrollable && !atBottom
            val showUp = hasScrollable && atBottom

            fabScrollDownDetail.toggleFabAnimated(showDown)
            fabScrollUpDetail.toggleFabAnimated(showUp)
        }
    }

    // Mesma animação do IaFragment, para manter a aparência
    private fun View.toggleFabAnimated(show: Boolean) {
        val interp = FastOutSlowInInterpolator()
        if (show && isGone) {
            isGone = false
            alpha = 0f
            scaleX = 0.85f
            scaleY = 0.85f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(160L)
                .setInterpolator(interp)
                .start()
        } else if (!show && !isGone) {
            animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(140L)
                .setInterpolator(interp)
                .withEndAction {
                    isGone = true
                    alpha = 1f
                    scaleX = 1f
                    scaleY = 1f
                }
                .start()
        }
    }

    // --- Estado do overlay para sobreviver à rotação
    companion object {
        private const val STATE_DETAIL_OPEN = "history_detail_open"
        private const val STATE_DETAIL_TITLE = "history_detail_title"
        private const val STATE_DETAIL_DATE = "history_detail_date"
        private const val STATE_DETAIL_CONTENT = "history_detail_content"
        private const val STATE_DETAIL_SCROLL_Y = "history_detail_scroll_y"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val open = binding.detailOverlay.isVisible
        outState.putBoolean(STATE_DETAIL_OPEN, open)
        if (open) {
            outState.putString(STATE_DETAIL_TITLE, binding.tvDetailTitle.text?.toString())
            outState.putString(STATE_DETAIL_DATE, binding.tvDetailDate.text?.toString())
            outState.putString(STATE_DETAIL_CONTENT, binding.tvDetailContent.text?.toString())
            outState.putInt(STATE_DETAIL_SCROLL_Y, binding.scrollDetail.scrollY)
        }
    }

    override fun onDestroyView() {
        // Remover listeners associados à view para evitar callbacks após destruir a view
        _binding?.let { b ->
            // 2.1) Remover listener de rolagem
            b.scrollDetail.setOnScrollChangeListener(null as View.OnScrollChangeListener?)

            // 2.2) Remover o GlobalLayoutListener com segurança
            detailGlobalLayoutListener?.let { gl ->
                val vto = b.scrollDetail.viewTreeObserver
                if (vto.isAlive) {
                    vto.removeOnGlobalLayoutListener(gl)
                }
            }
        }
        detailGlobalLayoutListener = null

        _binding = null
        super.onDestroyView()
    }
}