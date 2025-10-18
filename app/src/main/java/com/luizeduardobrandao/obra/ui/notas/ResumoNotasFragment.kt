package com.luizeduardobrandao.obra.ui.notas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.databinding.FragmentResumoNotasBinding
import com.luizeduardobrandao.obra.ui.extensions.showSnackbarFragment
import com.luizeduardobrandao.obra.ui.notas.adapter.NotaAdapter
import com.luizeduardobrandao.obra.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface
import com.luizeduardobrandao.obra.utils.savePdfToDownloads
import com.luizeduardobrandao.obra.utils.showMaterialDateRangePickerBrBounded
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.core.view.size
import androidx.core.view.get

@AndroidEntryPoint
class ResumoNotasFragment : Fragment() {

    private var _binding: FragmentResumoNotasBinding? = null
    private val binding get() = _binding!!

    private val args: ResumoNotasFragmentArgs by navArgs()
    private val viewModel: NotasViewModel by viewModels()

    /* Adapter sem ações – só exibição */
    private val adapter by lazy { NotaAdapter(showActions = false) }

    // Estado das abas (preservado em rotação/processo)
    private var isTotalsExpanded = false
    private var isTiposExpanded = false

    // ─── Cache de notas atuais (último UiState.Success) ───
    private var cachedNotas: List<Nota> = emptyList()

    // Origem do comando (Compartilhar ou Download) para o fluxo pós-diálogo
    private enum class ExportOrigin { SHARE, DOWNLOAD }

    // Opções do diálogo de filtro
    private enum class ExportFilter { A_RECEBER, PAGO }

    // Flag para confirmar com Snackbar quando o usuário retorna após compartilhar
    private var pendingShareConfirm: Boolean = false
    private var leftAppForShare: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            isTotalsExpanded = it.getBoolean(KEY_TOT_EXPANDED, false)
            isTiposExpanded = it.getBoolean(KEY_TIPOS_EXPANDED, false)
        }
        pendingShareConfirm = savedInstanceState?.getBoolean(KEY_PENDING_SHARE, false) == true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentResumoNotasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            progressNotasList.isVisible = true
            rvNotasResumo.isVisible = false
            tvEmptyNotas.isVisible = false

            toolbarResumoNotas.setNavigationOnClickListener { findNavController().navigateUp() }
            // Ícone de exportar na toolbar (actionView) → popup (Compartilhar / Download)
            val exportItem = binding.toolbarResumoNotas.menu.findItem(R.id.action_resumo_export)
            val exportAnchor = exportItem.actionView?.findViewById<View>(R.id.btnExportMenu)

            exportAnchor?.setOnClickListener {
                val themed = android.view.ContextThemeWrapper(
                    requireContext(),
                    R.style.PopupMenu_WhiteBg_BlackText
                )
                val popup = androidx.appcompat.widget.PopupMenu(
                    themed,
                    exportAnchor,
                    android.view.Gravity.END
                ).apply {
                    menuInflater.inflate(R.menu.menu_resumo_export_popup, menu)
                    val textColor = requireContext().getColor(R.color.toolbarIcon)
                    for (i in 0 until menu.size) {
                        val item = menu[i]
                        val s = android.text.SpannableString(item.title)
                        s.setSpan(
                            android.text.style.ForegroundColorSpan(textColor),
                            0, s.length,
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE
                        )
                        item.title = s
                    }
                    setOnMenuItemClickListener { mi ->
                        when (mi.itemId) {
                            R.id.action_export_share -> {
                                openExportFilterDialog(ExportOrigin.SHARE); true
                            }

                            R.id.action_export_download -> {
                                openExportFilterDialog(ExportOrigin.DOWNLOAD); true
                            }

                            else -> false
                        }
                    }
                }
                /* Habilita divisor entre grupos via reflexão (evita acessar API restrita diretamente) */
                try {
                    val menu = popup.menu
                    val clazz = Class.forName("androidx.appcompat.view.menu.MenuBuilder")
                    if (clazz.isInstance(menu)) {
                        val method = clazz.getDeclaredMethod(
                            "setGroupDividerEnabled",
                            Boolean::class.javaPrimitiveType
                        )
                        method.isAccessible = true
                        method.invoke(menu, true)
                    }
                } catch (_: Exception) {
                    // silenciosamente ignora se falhar; o menu segue funcionando
                }
                // Força mostrar ícones no PopupMenu
                try {
                    // Se sua versão do AppCompat tiver a API pública:
                    popup.setForceShowIcon(true)
                } catch (_: Throwable) {
                    // Fallback via reflexão (para versões antigas do AppCompat)
                    try {
                        val fields =
                            androidx.appcompat.widget.PopupMenu::class.java.getDeclaredField("mPopup")
                        fields.isAccessible = true
                        val menuPopupHelper = fields.get(popup)
                        val classPopupHelper = menuPopupHelper.javaClass
                        val setForceIcons = classPopupHelper.getDeclaredMethod(
                            "setForceShowIcon",
                            Boolean::class.javaPrimitiveType
                        )
                        setForceIcons.isAccessible = true
                        setForceIcons.invoke(menuPopupHelper, true)
                    } catch (_: Exception) { /* ignore */
                    }
                }

                // Garante a cor do ícone no tema claro do popup
                val iconColor = requireContext().getColor(R.color.toolbarIcon)
                for (i in 0 until popup.menu.size) {
                    popup.menu[i].icon?.setTint(iconColor)
                }

                popup.show()
            }

            rvNotasResumo.adapter = adapter

            // Liga as abas expansíveis
            setupExpandable(
                containerRoot = llTotaisContainer,
                header = headerAbaTotais,
                content = contentAbaTotais,
                arrow = ivArrowTotais,
                startExpanded = isTotalsExpanded
            ) { expanded -> isTotalsExpanded = expanded }

            setupExpandable(
                containerRoot = llTotaisContainer,
                header = headerAbaTipos,
                content = contentAbaTipos,
                arrow = ivArrowTipos,
                startExpanded = isTiposExpanded
            ) { expanded -> isTiposExpanded = expanded }
        }

        // Sem isso o estado fica eternamente em Loading
        viewModel.loadNotas()

        observeState()
    }

    /*───────────────────────── Collector ─────────────────────────*/
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { ui ->
                    when (ui) {
                        is UiState.Loading -> progress(true)
                        is UiState.Success -> {
                            cachedNotas = ui.data
                            showNotas(ui.data)
                        }

                        is UiState.ErrorRes -> {
                            progress(false)
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.snack_error),
                                getString(ui.resId),
                                getString(R.string.snack_button_ok)
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }

    private fun showNotas(list: List<Nota>) = with(binding) {
        progress(false)

        if (list.isEmpty()) {
            rvNotasResumo.isVisible = false
            tvEmptyNotas.isVisible = true
            llTotaisContainer.isVisible = false
            return@with
        }

        tvEmptyNotas.isVisible = false
        llTotaisContainer.isVisible = true
        rvNotasResumo.isVisible = true

        // Lista completa nas cards ordenada por Data
        val comparator = compareBy<Nota> { parseBrToEpochOrMin(it.data) }
            .thenBy { it.nomeMaterial.lowercase(Locale.ROOT) }

        val sortedList = list.sortedWith(comparator)
        adapter.submitList(sortedList)

        // ---- Totais (valores) ----
        val totalAPagar = list.filter { it.status == "A Pagar" }.sumOf { it.valor }
        val totalPago = list.filter { it.status == "Pago" }.sumOf { it.valor }
        val totalGeral = totalAPagar + totalPago

        tvResumoTotalAPagar.text = formatMoneyBR(totalAPagar)
        tvResumoTotalPago.text = formatMoneyBR(totalPago)
        tvResumoTotalGeral.text = formatMoneyBR(totalGeral)

        // ---- Totais por combinação de tipos: (A Receber) ----
        containerTiposValoresAPagar.removeAllViews()
        val porComboAPagar = linkedMapOf<String, Double>()
        list.filter { it.status == "A Pagar" }.forEach { n ->
            // Combinação EXATA mantida na ordem salva na nota
            val combo = n.tipos.joinToString(", ")
            porComboAPagar[combo] = (porComboAPagar[combo] ?: 0.0) + n.valor
        }
        // Se o TOTAL A RECEBER for zero, mostra a mensagem e esconde a lista
        if (totalAPagar == 0.0) {
            binding.tvTiposAPagarEmpty.isVisible = true
            binding.containerTiposValoresAPagar.isVisible = false
        } else {
            binding.tvTiposAPagarEmpty.isVisible = false
            binding.containerTiposValoresAPagar.isVisible = true

            porComboAPagar.forEach { (combo, v) ->
                val tv = layoutInflater.inflate(
                    R.layout.item_tipo_valor, containerTiposValoresAPagar, false
                ) as android.widget.TextView

                // Exemplo: "Elétrica: R$ 900,00"
                val label = "$combo: ${formatMoneyBR(v)}"

                val styled = SpannableStringBuilder(label).apply {
                    setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        combo.length, // só o nome do tipo em negrito
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                tv.text = styled
                tv.textSize = 16f   // 16sp
                containerTiposValoresAPagar.addView(tv)
            }
        }

        // ---- Totais por combinação de tipos: (Pago) ----
        containerTiposValoresPago.removeAllViews()
        val porComboPago = linkedMapOf<String, Double>()
        list.filter { it.status == "Pago" }.forEach { n ->
            val combo = n.tipos.joinToString(", ")
            porComboPago[combo] = (porComboPago[combo] ?: 0.0) + n.valor
        }
        if (totalPago == 0.0) {   // ✅ checa totalPago, não totalAPagar
            tvTiposPagoEmpty.isVisible = true
            containerTiposValoresPago.isVisible = false
        } else {
            tvTiposPagoEmpty.isVisible = false
            containerTiposValoresPago.isVisible = true

            porComboPago.forEach { (combo, v) ->
                val tv = layoutInflater.inflate(
                    R.layout.item_tipo_valor, containerTiposValoresPago, false
                ) as android.widget.TextView

                // Ex.: "Elétrica: R$ 100,00"
                val label = "$combo: ${formatMoneyBR(v)}"

                val styled = SpannableStringBuilder(label).apply {
                    setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        combo.length, // deixa só o nome do tipo em negrito
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                tv.text = styled
                tv.textSize = 16f   // 16sp
                containerTiposValoresPago.addView(tv)
            }
        }
    }

    private fun progress(show: Boolean) = with(binding) {
        progressNotasList.isVisible = show
        rvNotasResumo.isVisible = !show
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_TOT_EXPANDED, isTotalsExpanded)
        outState.putBoolean(KEY_TIPOS_EXPANDED, isTiposExpanded)
        outState.putBoolean(KEY_PENDING_SHARE, pendingShareConfirm)
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }

    /*───────────────────────── Helpers ─────────────────────────*/
    private fun setupExpandable(
        containerRoot: ViewGroup,
        header: View,
        content: View,
        arrow: ImageView,
        startExpanded: Boolean,
        onStateChange: (Boolean) -> Unit
    ) {
        fun applyState(expanded: Boolean, animate: Boolean) {
            if (animate) {
                TransitionManager.beginDelayedTransition(
                    containerRoot,
                    AutoTransition().apply { duration = 180 }
                )
            }
            content.isVisible = expanded
            arrow.animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()
            onStateChange(expanded)
        }

        // Estado inicial (sem animação para evitar "piscada" na abertura)
        content.post { applyState(startExpanded, animate = false) }

        header.setOnClickListener {
            val willExpand = !content.isVisible
            applyState(willExpand, animate = true)
        }
    }

    private fun formatMoneyBR(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    /*──────────────────── Export Flow ───────────────────*/
    private fun openExportFilterDialog(origin: ExportOrigin) {
        // Sem notas cadastradas?
        if (cachedNotas.isEmpty()) {
            showSnackbarFragment(
                Constants.SnackType.ERROR.name,
                getString(R.string.snack_error),
                getString(R.string.resumo_export_no_notes),
                getString(R.string.snack_button_ok)
            )
            return
        }

        // Opções do single-choice
        val items = arrayOf(
            getString(R.string.resumo_export_opt_due),   // A Receber
            getString(R.string.resumo_export_opt_paid),  // Pago
            getString(R.string.resumo_export_opt_date)   // Data
        )
        var checked = 0 // default "A Receber"

        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_ObrasApp_FuncDialog
        )
            .setTitle(getString(R.string.resumo_export_choose_title))
            .setSingleChoiceItems(items, checked) { _, which -> checked = which }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                when (checked) {
                    0 -> confirmAndRunFixedStatus(origin, ExportFilter.A_RECEBER)
                    1 -> confirmAndRunFixedStatus(origin, ExportFilter.PAGO)
                    2 -> runDateRangePicker(origin) // no caso de compartilhamento, mensagem muda lá também
                }
            }
            .setNegativeButton(R.string.generic_cancel, null)
            .create()

        dlg.show()
    }

    private fun confirmAndRunFixedStatus(origin: ExportOrigin, filter: ExportFilter) {
        val isReceber = (filter == ExportFilter.A_RECEBER)

        // Mensagem de confirmação distinta para Compartilhar vs Download
        val msg = when (origin) {
            ExportOrigin.SHARE -> getString(
                R.string.resumo_export_confirm_due_share_take2,
                if (isReceber) getString(R.string.resumo_export_opt_due) else getString(R.string.resumo_export_opt_paid)
            )

            ExportOrigin.DOWNLOAD -> getString(
                if (isReceber) R.string.resumo_export_confirm_due
                else R.string.resumo_export_confirm_paid
            )
        }

        showSnackbarFragment(
            type = Constants.SnackType.WARNING.name,
            title = getString(R.string.snack_attention),
            msg = msg,
            btnText = getString(R.string.snack_button_yes),
            onAction = {
                val targetStatus = if (isReceber) "A Pagar" else "Pago"
                val notas = cachedNotas.filter { it.status == targetStatus }
                if (notas.isEmpty()) {
                    view?.post {
                        showSnackbarFragment(
                            Constants.SnackType.ERROR.name,
                            getString(R.string.snack_error),
                            getString(R.string.resumo_export_no_notes_in_filter),
                            getString(R.string.snack_button_ok)
                        )
                    }
                    return@showSnackbarFragment
                }
                exportNotas(notas, origin) // gera/salva e (se share) abre chooser
            },
            btnNegativeText = getString(R.string.snack_button_no),
            onNegative = { /* nada */ }
        )
    }

    private fun runDateRangePicker(origin: ExportOrigin) {
        // limites: menor/maior datas cadastradas
        val (minBr, maxBr) = calcMinMaxDatesOrNull(cachedNotas)
            ?: run {
                showSnackbarFragment(
                    Constants.SnackType.ERROR.name,
                    getString(R.string.snack_error),
                    getString(R.string.resumo_export_no_notes),
                    getString(R.string.snack_button_ok)
                )
                return
            }

        showMaterialDateRangePickerBrBounded(minBr, maxBr) { startBr, endBr ->
            val msg = when (origin) {
                ExportOrigin.SHARE -> getString(R.string.resumo_export_confirm_range_share)
                ExportOrigin.DOWNLOAD -> getString(R.string.resumo_export_confirm_range)
            }
            showSnackbarFragment(
                type = Constants.SnackType.WARNING.name,
                title = getString(R.string.snack_attention),
                msg = msg,
                btnText = getString(R.string.snack_button_yes),
                onAction = {
                    // Filtra "A Pagar" OU "Pago" dentro do intervalo escolhido
                    val notas = cachedNotas.filter { n ->
                        (n.status == "A Pagar" || n.status == "Pago") && isDateWithin(
                            n.data,
                            startBr,
                            endBr
                        )
                    }
                    if (notas.isEmpty()) {
                        // Cenário 2: não há notas no PERÍODO selecionado
                        view?.post {
                            showSnackbarFragment(
                                Constants.SnackType.ERROR.name,
                                getString(R.string.snack_error),
                                getString(R.string.resumo_export_no_notes_in_range),
                                getString(R.string.snack_button_ok)
                            )
                        }
                        return@showSnackbarFragment
                    }
                    exportNotas(notas, origin, customRange = startBr to endBr)
                },
                btnNegativeText = getString(R.string.snack_button_no),
                onNegative = { }
            )
        }
    }

    private fun exportNotas(
        notas: List<Nota>,
        origin: ExportOrigin,
        customRange: Pair<String, String>? = null
    ) {
        // Define período mostrado no PDF
        val (minBr, maxBr) = customRange ?: (calcMinMaxDatesOrNull(notas) ?: ("-" to "-"))

        // Nome do arquivo: "Notas_Obra_[Data de início – Data de Término].pdf"
        val safeStart = minBr.replace("/", "-")
        val safeEnd = maxBr.replace("/", "-")
        val pdfName = "Notas_Obra_${safeStart}-${safeEnd}.pdf"

        val bytes = generateNotasPdf(requireContext(), notas, periodBr = "$minBr até $maxBr")

        if (origin == ExportOrigin.SHARE) {
            // ── Compartilhar SEM salvar em Downloads: usa arquivo temporário no cache ──
            val cacheFile = File(requireContext().cacheDir, pdfName)
            FileOutputStream(cacheFile).use { it.write(bytes) }

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                cacheFile
            )

            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(
                    requireContext().contentResolver,
                    "PDF",
                    uri
                )
            }
            // 👇 Concede a permissão de leitura para todos os apps que podem receber
            val resInfoList = requireContext().packageManager.queryIntentActivities(
                share, 0
            )
            for (ri in resInfoList) {
                requireContext().grantUriPermission(
                    ri.activityInfo.packageName,
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            // Sinaliza confirmação ao retornar ao app
            pendingShareConfirm = true
            startActivity(
                android.content.Intent.createChooser(
                    share,
                    getString(R.string.export_share_chooser)
                )
            )
        } else {
            // ── Download: mantém salvando em Downloads como já era ──
            val uri = savePdfToDownloads(requireContext(), bytes, pdfName)
            if (uri == null) {
                // Erro ao salvar/criar
                view?.post {
                    showSnackbarFragment(
                        Constants.SnackType.ERROR.name,
                        getString(R.string.snack_error),
                        getString(R.string.resumo_export_error_save),
                        getString(R.string.snack_button_ok)
                    )
                }
                return
            }
            // Sucesso
            view?.post {
                showSnackbarFragment(
                    Constants.SnackType.SUCCESS.name,
                    getString(R.string.snack_success),
                    getString(R.string.resumo_export_saved_ok),
                    getString(R.string.snack_button_ok)
                )
            }
        }
    }

    /*──────────────────── PDF ───────────────────*/
    private fun generateNotasPdf(
        ctx: android.content.Context,
        notas: List<Nota>,
        periodBr: String
    ): ByteArray {
        // Ordena por data asc + nome material
        val sorted = notas.sortedWith(
            compareBy<Nota> { parseBrToEpochOrMin(it.data) }
                .thenBy { it.nomeMaterial.lowercase(Locale.ROOT) }
        )

        val doc = android.graphics.pdf.PdfDocument()
        val pageWidth = 595  // A4 ~ 72dpi
        val pageHeight = 842
        val left = 40
        val right = pageWidth - 40
        val lineGap = 14
        val extraAfterItemDivider = 6  // px extra antes da próxima linha pós-divisor
        val extraAfterHeaderDivider = 6

        val titlePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }
        val headerPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 12f
            typeface = Typeface.SANS_SERIF
            color = android.graphics.Color.BLACK
        }
        val dividerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 1f
        }

        var pageNum = 1
        var y = 60

        fun newPage(): android.graphics.pdf.PdfDocument.Page {
            val pageInfo =
                android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum)
                    .create()
            val page = doc.startPage(pageInfo)
            y = 60

            // Cabeçalho
            page.canvas.drawText(
                ctx.getString(R.string.resumo_pdf_title),
                left.toFloat(),
                y.toFloat(),
                titlePaint
            )
            y += lineGap + 2
            page.canvas.drawText(
                ctx.getString(R.string.resumo_pdf_dates, periodBr),
                left.toFloat(),
                y.toFloat(),
                textPaint
            )
            y += lineGap
            page.canvas.drawLine(
                left.toFloat(),
                (y + 4).toFloat(),
                right.toFloat(),
                (y + 4).toFloat(),
                dividerPaint
            )
            y += lineGap + extraAfterHeaderDivider
            return page
        }

        var page = newPage()

        // Formatação monetária
        val nf = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

        var total = 0.0
        sorted.forEachIndexed { idx, n ->
            val data = n.data.takeIf { it.isNotBlank() } ?: "-"
            val nome = n.nomeMaterial.ifBlank { "-" }
            val loja = n.loja.ifBlank { "-" }
            val tipos = if (n.tipos.isEmpty()) "-" else n.tipos.joinToString(", ")
            val valor = nf.format(n.valor)
            total += n.valor

            // Quebra de página?
            if (y > pageHeight - 120) {
                doc.finishPage(page)
                pageNum++
                page = newPage()
            }

            // Bloco da nota
            page.canvas.drawText(
                "• ${ctx.getString(R.string.resumo_pdf_item_date, data)}",
                left.toFloat(),
                y.toFloat(),
                headerPaint
            ); y += lineGap
            page.canvas.drawText(
                ctx.getString(R.string.resumo_pdf_item_material, nome),
                left.toFloat(),
                y.toFloat(),
                textPaint
            ); y += lineGap
            page.canvas.drawText(
                ctx.getString(R.string.resumo_pdf_item_store, loja),
                left.toFloat(),
                y.toFloat(),
                textPaint
            ); y += lineGap
            page.canvas.drawText(
                ctx.getString(R.string.resumo_pdf_item_types, tipos),
                left.toFloat(),
                y.toFloat(),
                textPaint
            ); y += lineGap
            page.canvas.drawText(
                ctx.getString(R.string.resumo_pdf_item_value, valor),
                left.toFloat(),
                y.toFloat(),
                textPaint
            ); y += lineGap

            // divisor entre itens (não no último)
            if (idx < sorted.lastIndex) {
                page.canvas.drawLine(
                    left.toFloat(),
                    (y + 2).toFloat(),
                    right.toFloat(),
                    (y + 2).toFloat(),
                    dividerPaint
                )
                y += lineGap + extraAfterItemDivider   // <— espaço maior antes do próximo item
            }
        }

        // Total
        if (y > pageHeight - 80) {
            doc.finishPage(page)
            pageNum++
            page = newPage()
        }
        val totalStr = nf.format(total)
        page.canvas.drawLine(
            left.toFloat(),
            (y + 6).toFloat(),
            right.toFloat(),
            (y + 6).toFloat(),
            dividerPaint
        )
        y += lineGap + 8
        page.canvas.drawText(
            ctx.getString(R.string.resumo_pdf_total, totalStr),
            left.toFloat(),
            y.toFloat(),
            titlePaint
        )

        // Fecha doc
        doc.finishPage(page)
        val out = java.io.ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /*──────────────────── Utils de data ───────────────────*/
    private fun parseBrToEpochOrMin(d: String?): Long {
        if (d.isNullOrBlank()) return Long.MIN_VALUE
        return try {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                .apply { isLenient = false }
            sdf.parse(d)?.time ?: Long.MIN_VALUE
        } catch (_: Exception) {
            Long.MIN_VALUE
        }
    }

    private fun isDateWithin(dBr: String?, startBr: String, endBr: String): Boolean {
        val d = parseBrToEpochOrMin(dBr)
        val s = parseBrToEpochOrMin(startBr)
        val e = parseBrToEpochOrMin(endBr)
        if (d == Long.MIN_VALUE || s == Long.MIN_VALUE || e == Long.MIN_VALUE) return false
        return d in s..e
    }

    private fun calcMinMaxDatesOrNull(list: List<Nota>): Pair<String, String>? {
        val valid = list.mapNotNull { it.data.takeIf { s -> s.isNotBlank() } }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (valid.isEmpty()) return null
        val sorted = valid.sortedBy { parseBrToEpochOrMin(it) }
        return sorted.first() to sorted.last()
    }

    override fun onResume() {
        super.onResume()
        // Só confirma se o usuário realmente saiu do app para compartilhar
        if (pendingShareConfirm && leftAppForShare) {
            pendingShareConfirm = false
            leftAppForShare = false
            showSnackbarFragment(
                Constants.SnackType.SUCCESS.name,
                getString(R.string.snack_success),
                getString(R.string.resumo_export_shared_ok),
                getString(R.string.snack_button_ok)
            )
        }
    }

    override fun onStop() {
        super.onStop()
        // Considera que o usuário realmente saiu do app durante o fluxo de share
        if (pendingShareConfirm) {
            leftAppForShare = true
        }
    }

    companion object {
        private const val KEY_TOT_EXPANDED = "isTotalsExpanded"
        private const val KEY_TIPOS_EXPANDED = "isTiposExpanded"
        private const val KEY_PENDING_SHARE = "pendingShareConfirm"
    }
}