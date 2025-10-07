package com.luizeduardobrandao.obra.ui.resumo

import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.textview.MaterialTextView
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentExportSummaryBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.utils.Constants
import com.luizeduardobrandao.obra.utils.savePdfToDownloads
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

@AndroidEntryPoint
class ExportSummaryFragment : Fragment() {

    private var _binding: FragmentExportSummaryBinding? = null
    private val binding get() = _binding!!

    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val args: ExportSummaryFragmentArgs by navArgs()
    private val viewModel: ExportSummaryViewModel by viewModels()

    private val brLocale = Locale("pt", "BR")
    private val moneyFmt: NumberFormat by lazy { NumberFormat.getCurrencyInstance(brLocale) }
    private val dateBr: SimpleDateFormat by lazy { SimpleDateFormat("dd/MM/yyyy", brLocale) }
    private val dateIso: SimpleDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupFabBehavior()
        collectStateAndRender()
    }

    // ───────────────────────── Toolbar & Menu ─────────────────────────
    private fun setupToolbar() = with(binding.toolbarExportSummary) {
        setNavigationOnClickListener { findNavController().navigateUp() }
        inflateMenu(R.menu.menu_export_summary)

        // Anchor do botão custom
        val saveItem = menu.findItem(R.id.action_save_pdf)
        val btnSave = saveItem.actionView?.findViewById<View>(R.id.btnSavePdf)
        btnSave?.setOnClickListener { askSavePdf() }

        // (opcional) fallback via listener tradicional
        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save_pdf -> {
                    askSavePdf()
                    true
                }

                else -> false
            }
        }
    }

    private fun askSavePdf() {
        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.export_summary_snack_title),
            msg = getString(R.string.export_summary_snack_msg),
            btnText = getString(R.string.export_summary_snack_yes),
            onAction = { savePdfNow() },
            btnNegativeText = getString(R.string.export_summary_snack_no),
            onNegative = { /* fica na página */ }
        )
    }

    // ───────────────────────── State & Render ─────────────────────────

    private fun collectStateAndRender() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> {
                            binding.progressExportSummary.isVisible = true
                            binding.scrollExportSummary.isVisible = false
                        }

                        is UiState.Success -> {
                            binding.progressExportSummary.isVisible = false
                            binding.scrollExportSummary.isVisible = true
                            render(ui.data)
                        }

                        is UiState.ErrorRes -> {
                            binding.progressExportSummary.isVisible = false
                            binding.scrollExportSummary.isVisible = false
                            showSnackbarFragment(
                                type = Constants.SnackType.ERROR.name,
                                title = getString(R.string.snack_error),
                                msg = getString(ui.resId),
                                btnText = getString(R.string.snack_button_ok)
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun render(data: ExportSummaryViewModel.ExportSummaryData) = with(binding) {
        // Cabeçalho
        tvObraName.text = getString(R.string.export_obraname_title, data.obra.nomeCliente)

        // Informações da Obra
        tvCliente.text = getString(R.string.export_client_name, data.obra.nomeCliente)
        tvEndereco.text = getString(R.string.export_address, data.obra.endereco)
        tvContato.text = getString(R.string.export_contact, data.obra.contato)
        tvDescricao.text = getString(R.string.export_description, data.obra.descricao)
        tvDataInicio.text = getString(
            R.string.export_date_start_line,
            fmtBr(data.obra.dataInicio)
        )
        tvDataFim.text = getString(
            R.string.export_date_end_line,
            fmtBr(data.obra.dataFim)
        )

        // Funcionários
        containerFuncAtivos.removeAllViews()
        if (data.funcionariosAtivos.isEmpty()) {
            addBodyLine(containerFuncAtivos, getString(R.string.func_none_active), italic = true)
        } else {
            data.funcionariosAtivos.forEach { ft ->
                addBodyLine(
                    containerFuncAtivos,
                    getString(
                        R.string.export_func_item_paid_fmt,
                        ft.funcionario.nome,
                        moneyFmt.format(ft.totalPago)
                    )
                )
            }
        }

        containerFuncInativos.removeAllViews()
        if (data.funcionariosInativos.isEmpty()) {
            addBodyLine(
                containerFuncInativos,
                getString(R.string.func_none_inactive),
                italic = true
            )
        } else {
            data.funcionariosInativos.forEach { ft ->
                addBodyLine(
                    containerFuncInativos,
                    getString(
                        R.string.export_func_item_paid_fmt,
                        ft.funcionario.nome,
                        moneyFmt.format(ft.totalPago)
                    )
                )
            }
        }

        tvFuncTotalGasto.text = getString(
            R.string.export_func_total_paid,
            moneyFmt.format(data.totalGastoFuncionarios)
        )

        // Notas
        containerNotasReceber.removeAllViews()
        if (data.notasAReceber.isEmpty()) {
            addBodyLine(containerNotasReceber, getString(R.string.nota_none_summary), italic = true)
        } else {
            data.notasAReceber.forEach { n ->
                addBodyLine(
                    containerNotasReceber,
                    getString(
                        R.string.export_nota_line,
                        n.nomeMaterial,
                        fmtBr(n.data),
                        moneyFmt.format(n.valor)
                    )
                )
            }
        }
        containerNotasPagas.removeAllViews()
        if (data.notasPagas.isEmpty()) {
            addBodyLine(containerNotasPagas, getString(R.string.nota_none_summary2), italic = true)
        } else {
            data.notasPagas.forEach { n ->
                addBodyLine(
                    containerNotasPagas,
                    getString(
                        R.string.export_nota_line,
                        n.nomeMaterial,
                        fmtBr(n.data),
                        moneyFmt.format(n.valor)
                    )
                )
            }
        }
        tvTotalNotasReceber.text = getString(
            R.string.export_total_notas_due,
            moneyFmt.format(data.totalNotasAReceber)
        )
        tvTotalNotasPagas.text = getString(
            R.string.export_total_notas_paid,
            moneyFmt.format(data.totalNotasPagas)
        )
        tvTotalNotasGeral.text = getString(
            R.string.export_total_notas_all,
            moneyFmt.format(data.totalNotasGeral)
        )

        // Cronogramas
        containerCronPendentes.removeAllViews()
        if (data.cronPendentes.isEmpty()) {
            addBodyLine(
                containerCronPendentes,
                getString(R.string.cron_none_pending_summary),
                italic = true
            )
        } else {
            data.cronPendentes.forEach { e ->
                addBodyLine(
                    containerCronPendentes,
                    getString(
                        R.string.export_cron_line,
                        e.titulo,
                        fmtBr(e.dataInicio),
                        fmtBr(e.dataFim)
                    )
                )
            }
        }
        containerCronAndamento.removeAllViews()
        if (data.cronAndamento.isEmpty()) {
            addBodyLine(
                containerCronAndamento,
                getString(R.string.cron_none_progress_summary),
                italic = true
            )
        } else {
            data.cronAndamento.forEach { e ->
                addBodyLine(
                    containerCronAndamento,
                    getString(
                        R.string.export_cron_line,
                        e.titulo,
                        fmtBr(e.dataInicio),
                        fmtBr(e.dataFim)
                    )
                )
            }
        }
        containerCronConcluidos.removeAllViews()
        if (data.cronConcluidos.isEmpty()) {
            addBodyLine(
                containerCronConcluidos,
                getString(R.string.cron_none_done_summary),
                italic = true
            )
        } else {
            data.cronConcluidos.forEach { e ->
                addBodyLine(
                    containerCronConcluidos,
                    getString(
                        R.string.export_cron_line,
                        e.titulo,
                        fmtBr(e.dataInicio),
                        fmtBr(e.dataFim)
                    )
                )
            }
        }
        tvTotalCronPend.text = getString(R.string.export_total_cron_pend, data.countCronPendentes)
        tvTotalCronAnd.text = getString(R.string.export_total_cron_prog, data.countCronAndamento)
        tvTotalCronConc.text = getString(R.string.export_total_cron_done, data.countCronConcluidos)

        // Materiais
        containerMatAtivos.removeAllViews()
        if (data.materiaisAtivos.isEmpty()) {
            addBodyLine(
                containerMatAtivos,
                getString(R.string.material_empty_summary),
                italic = true
            )
        } else {
            data.materiaisAtivos.forEach { m ->
                addBodyLine(
                    containerMatAtivos,
                    getString(R.string.export_mat_line, m.nome, m.quantidade)
                )
            }
        }
        containerMatInativos.removeAllViews()
        if (data.materiaisInativos.isEmpty()) {
            addBodyLine(
                containerMatInativos,
                getString(R.string.material_empty_summary2),
                italic = true
            )
        } else {
            data.materiaisInativos.forEach { m ->
                addBodyLine(
                    containerMatInativos,
                    getString(R.string.export_mat_line, m.nome, m.quantidade)
                )
            }
        }
        tvTotalMatAtivo.text =
            getString(R.string.export_total_mat_active, data.totalMateriaisAtivos)
        tvTotalMatInativo.text =
            getString(R.string.export_total_mat_inactive, data.totalMateriaisInativos)
        tvTotalMatGeral.text = getString(R.string.export_total_mat_all, data.totalMateriaisGeral)

        // Financeiro / Saldos / Aportes
        tvFinFuncTotal.text = getString(
            R.string.export_fin_func_total,
            moneyFmt.format(data.totalGastoFuncionarios)
        )
        tvFinNotasDue.text = getString(
            R.string.export_fin_notas_due,
            moneyFmt.format(data.totalNotasAReceber)
        )
        tvFinNotasPaid.text = getString(
            R.string.export_fin_notas_paid,
            moneyFmt.format(data.totalNotasPagas)
        )
        tvFinNotasAll.text = getString(
            R.string.export_fin_notas_all,
            moneyFmt.format(data.totalNotasGeral)
        )

        tvSaldoInicial.text = getString(
            R.string.export_saldo_inicial_val,
            moneyFmt.format(data.saldoInicial)
        )

        containerAportes.removeAllViews()
        if (data.aportes.isEmpty()) {
            // Apenas a mensagem e esconder o "Saldo com Aportes"
            addBodyLine(containerAportes, getString(R.string.resumo_aportes_empty), italic = true)
            tvSaldoComAportes.isVisible = false
        } else {
            data.aportes.forEach { a ->
                val line = getString(
                    R.string.export_aporte_line,
                    fmtBr(a.data),
                    a.descricao.ifBlank { "-" },
                    moneyFmt.format(a.valor)
                )
                addBodyLine(containerAportes, line)
            }
            tvSaldoComAportes.isVisible = true
            tvSaldoComAportes.text = getString(
                R.string.export_saldo_com_aportes,
                moneyFmt.format(data.saldoComAportes)
            )
        }

        tvSaldoComAportes.text = getString(
            R.string.export_saldo_com_aportes,
            moneyFmt.format(data.saldoComAportes)
        )
        tvSaldoRestante.text = getString(
            R.string.export_saldo_restante_val,
            moneyFmt.format(data.saldoRestante)
        )

        // Rodapé com data de hoje (BR)
        val today = dateBr.format(Calendar.getInstance().time)
        tvFooterDataHoje.text = getString(R.string.export_footer_today, today)

        // Atualiza visibilidade do FAB após render
        binding.scrollExportSummary.post { updateFabVisibility() }
    }

    // ───────────────────────── PDF ─────────────────────────

    private fun savePdfNow() {
        val container = binding.containerExportSummary
        container.post {
            // ✅ Guardas defensivas: evita rodar se a view já foi destruída/desanexada
            if (!isAdded || _binding == null || !container.isAttachedToWindow) return@post

            val pdfBytes = buildPdfBytesFrom(container)
            if (pdfBytes == null) {
                showSnackbarFragment(
                    type = Constants.SnackType.ERROR.name,
                    title = getString(R.string.snack_error),
                    msg = getString(R.string.export_pdf_error_toast),
                    btnText = getString(R.string.snack_button_ok)
                )
                return@post
            }

            val today =
                SimpleDateFormat("ddMMyyyy", Locale.ROOT).format(Calendar.getInstance().time)
            val displayName = getString(R.string.export_summary_doc_name_fmt, args.obraId, today)

            val uri = savePdfToDownloads(requireContext(), pdfBytes, displayName)
            if (uri != null) {
                showSnackbarFragment(
                    type = Constants.SnackType.SUCCESS.name,
                    title = getString(R.string.snack_success),
                    msg = getString(R.string.export_pdf_saved_toast),
                    btnText = getString(R.string.snack_button_ok)
                )
            } else {
                showSnackbarFragment(
                    type = Constants.SnackType.ERROR.name,
                    title = getString(R.string.snack_error),
                    msg = getString(R.string.export_pdf_error_toast),
                    btnText = getString(R.string.snack_button_ok)
                )
            }
        }
    }

    /**
     * Gera bytes de PDF a partir de uma View longa (paginando em A4 portrait).
     * Mantém a largura da View e fatia a altura em páginas.
     */
    private fun buildPdfBytesFrom(view: View): ByteArray? {
        try {
            // ❌ NÃO re-medimos nem relayoutamos a view que está na tela.
            // Ela já está completamente layoutada quando savePdfNow() chama via post { ... }.

            val contentWidthPx = view.width
            val contentHeightPx = view.height
            if (contentWidthPx <= 0 || contentHeightPx <= 0) return null

            // Página A4: altura ≈ 1.414 * largura (mantendo a largura do conteúdo)
            val pageWidth = contentWidthPx
            val pageHeight = (pageWidth * 1.4142f).toInt().coerceAtLeast(800)

            val totalPages = ceil(contentHeightPx / pageHeight.toFloat()).toInt().coerceAtLeast(1)

            val pdf = PdfDocument()
            for (pageIndex in 0 until totalPages) {
                val top = pageIndex * pageHeight
                val bottom = min(top + pageHeight, contentHeightPx)
                val actualPageHeight = bottom - top

                val pageInfo = PdfDocument.PageInfo
                    .Builder(pageWidth, actualPageHeight, pageIndex + 1)
                    .create()

                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas

                // Recorta a parte visível desta página
                canvas.translate(0f, (-top).toFloat())
                view.draw(canvas)

                pdf.finishPage(page)
            }

            val bos = ByteArrayOutputStream()
            pdf.writeTo(bos)
            pdf.close()
            return bos.toByteArray()
        } catch (_: Throwable) {
            return null
        }
    }

    // ───────────────────────── FAB Scroll ─────────────────────────

    /** Decide qual FAB mostrar:
     *  - Se há rolagem e NÃO está no fim → mostra FAB de descer
     *  - Se há rolagem e ESTÁ no fim     → mostra FAB de subir
     *  - Se não houver rolagem           → esconde ambos
     */
    private fun updateFabVisibility() {
        val b = _binding ?: return  // ← sai se a view já foi destruída
        with(b) {
            val child = scrollExportSummary.getChildAt(0)
            val hasScrollable = child != null && child.height > scrollExportSummary.height
            val atBottom = !scrollExportSummary.canScrollVertically(1)

            when {
                !hasScrollable -> {
                    swapFabWithAnim(fabScrollDown, show = false)
                    swapFabWithAnim(fabScrollUp, show = false)
                }

                atBottom -> {
                    swapFabWithAnim(fabScrollDown, show = false)
                    swapFabWithAnim(fabScrollUp, show = true)
                }

                else -> {
                    swapFabWithAnim(fabScrollUp, show = false)
                    swapFabWithAnim(fabScrollDown, show = true)
                }
            }
        }
    }

    /** Animação simples (fade + leve scale) para trocar visibilidade de FABs. */
    private fun swapFabWithAnim(fab: View, show: Boolean) {
        val currentlyVisible = fab.isVisible
        if (show == currentlyVisible) return

        if (show) {
            fab.alpha = 0f
            fab.scaleX = 0.9f
            fab.scaleY = 0.9f
            fab.visibility = View.VISIBLE
            fab.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150L)
                .withEndAction { /* no-op */ }
                .start()
        } else {
            fab.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(150L)
                .withEndAction {
                    fab.visibility = View.GONE
                    fab.alpha = 1f
                    fab.scaleX = 1f
                    fab.scaleY = 1f
                }
                .start()
        }
    }

    /** Configura listeners (cliques e rolagem) exatamente como no IaFragment. */
    private fun setupFabBehavior() = with(binding) {
        // Ambos começam invisíveis
        fabScrollDown.visibility = View.GONE
        fabScrollUp.visibility = View.GONE

        // Clique: descer até o fim → em seguida, a visibilidade será reavaliada (mostra "subir")
        fabScrollDown.setOnClickListener {
            scrollExportSummary.post {
                scrollExportSummary.smoothScrollTo(0, containerExportSummary.bottom)
                // otimiza a troca visual imediatamente
                updateFabVisibility()
            }
        }

        // Clique: subir ao topo → em seguida, reavalia (volta a mostrar "descer")
        fabScrollUp.setOnClickListener {
            scrollExportSummary.post {
                scrollExportSummary.smoothScrollTo(0, 0)
                updateFabVisibility()
            }
        }

        // Listener de rolagem do usuário: troca FABs conforme posição
        scrollExportSummary.setOnScrollChangeListener { _, _, _, _, _ ->
            updateFabVisibility()
        }

        // Assim que o layout estiver pronto (depois de render), reavaliamos
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            updateFabVisibility()
        }
        scrollExportSummary.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    // ───────────────────────── Helpers ─────────────────────────

    private fun addBodyLine(parent: ViewGroup, text: String, italic: Boolean = false) {
        val tv = MaterialTextView(requireContext()).apply {
            this.text = text
            // Use seu R, não o material.R
            TextViewCompat.setTextAppearance(
                this,
                R.style.Custom_TextAppearance_Material3_BodyMedium
            )
            if (italic) {
                setTypeface(typeface, android.graphics.Typeface.ITALIC)
            }
            val pad = resources.getDimensionPixelSize(R.dimen.spacing_2)
            setPadding(0, pad, 0, 0)
        }
        parent.addView(tv)
    }

    private fun fmtBr(iso: String?): String {
        if (iso.isNullOrBlank()) return "-"
        return try {
            val d = dateIso.parse(iso)
            if (d != null) dateBr.format(d) else iso
        } catch (_: Throwable) {
            iso
        }
    }

    override fun onDestroyView() {
        // limpar listeners explícitos
        binding.scrollExportSummary.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
        binding.fabScrollDown.setOnClickListener(null)
        binding.fabScrollUp.setOnClickListener(null)

        // REMOVA o OnGlobalLayoutListener registrado
        globalLayoutListener?.let { l ->
            binding.scrollExportSummary.viewTreeObserver.removeOnGlobalLayoutListener(l)
            globalLayoutListener = null
        }

        // Limpar os listeners da toolbar
        binding.toolbarExportSummary.setOnMenuItemClickListener(null)
        binding.toolbarExportSummary.setNavigationOnClickListener(null)

        _binding = null
        super.onDestroyView()
    }
}