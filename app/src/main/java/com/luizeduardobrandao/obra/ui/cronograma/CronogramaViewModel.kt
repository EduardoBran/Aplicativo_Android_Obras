package com.luizeduardobrandao.obra.ui.cronograma

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.Obra
import com.luizeduardobrandao.obra.data.repository.ObraRepository
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.data.repository.CronogramaRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import com.luizeduardobrandao.obra.utils.GanttUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*      // para flowOn, catch, onEach, launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt
import java.time.LocalDate


/**
 * ViewModel responsável pelo cronograma da obra.
 *
 * • Recebe [obraId] via Safe-Args/SavedStateHandle.
 * • Mantém um único listener (Job cancelável) para não duplicar coleta.
 * • Não altera custos ("gastoTotal") — só gerencia etapas.
 */

@HiltViewModel
class CronogramaViewModel @Inject constructor(
    private val repo: CronogramaRepository,
    private val obraRepo: ObraRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // 1) obraId chega via Safe-Args, sem lateinit
    private val obraId: String = savedStateHandle["obraId"]
        ?: error("Safe-Args deve conter obraId")

    // 2) Estado interno de lista de etapas
    private val _state = MutableStateFlow<UiState<List<Etapa>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Etapa>>> = _state.asStateFlow()

    private val _opState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val opState: StateFlow<UiState<Unit>> = _opState.asStateFlow()

    // 3) Job cancelável para não acumular listeners
    private var loadJob: Job? = null

    // Obra observada (datas do cabeçalho)
    private val _obraState = MutableStateFlow<Obra?>(null)
    val obraState: StateFlow<Obra?> = _obraState.asStateFlow()


    /**
     * Inicia ou reinicia o listener em /cronograma/{obraId}/etapas.
     *
     * • Usa `flowOn(io)` para trocar para o dispatcher de I/O.
     * • `catch { … }` intercepta erros do Firebase e emite `ErrorRes`.
     * • `onEach { … }` emite `Success`.
     * • `launchIn(viewModelScope)` dispara a coleta sem precisar de `collect { }`.
     */
    fun loadEtapas() {
        // cancela listener anterior, se houver
        loadJob?.cancel()

        loadJob = repo.observeEtapas(obraId)
            .flowOn(io) // garante que o listener rode no dispatcher de I/O
            .catch {
                // em caso de erro ao buscar etapas
                _state.value = UiState.ErrorRes(R.string.cronograma_load_error)
            }
            .onEach { etapas ->
                // emite lista de etapas obtida
                _state.value = UiState.Success(etapas)
            }
            .launchIn(viewModelScope)
    }

    /** Observa a obra para refletir alterações de datas na tela Gantt **/
    init {
        viewModelScope.launch(io) {
            obraRepo.observeObra(obraId)
                .catch { _: Throwable -> /* opcional: log */ }
                .collect { obra: Obra? ->
                    _obraState.value = obra
                }
        }
    }

    /**
     * Adiciona uma nova [Etapa] e, ao concluir, recarrega a lista.
     */
    fun addEtapa(etapa: Etapa) = viewModelScope.launch(io) {
        _opState.value = UiState.Loading
        try {
            repo.addEtapa(obraId, etapa)
            _opState.value = UiState.Success(Unit)
            loadEtapas()
        } catch (_: Throwable) {
            _opState.value = UiState.ErrorRes(R.string.etapa_save_error)
        }
    }

    /**
     * Atualiza a etapa existente e, ao concluir, recarrega a lista.
     */
    /** Atualiza uma etapa e, ao concluir, recarrega a lista. */
    fun updateEtapa(etapaAtualizada: Etapa) = viewModelScope.launch(io) {
        _opState.value = UiState.Loading
        try {
            repo.updateEtapa(obraId, etapaAtualizada)
            _opState.value = UiState.Success(Unit)
            loadEtapas()
        } catch (_: Throwable) {
            _opState.value = UiState.ErrorRes(R.string.etapa_update_error)
        }
    }

    /**
     * Remove uma etapa e, ao concluir, recarrega a lista.
     */
    fun deleteEtapa(etapa: Etapa) = viewModelScope.launch(io) {
        _opState.value = UiState.Loading
        try {
            repo.deleteEtapa(obraId, etapa.id)
            _opState.value = UiState.Success(Unit)
            loadEtapas()
        } catch (_: Throwable) {
            _opState.value = UiState.ErrorRes(R.string.etapa_delete_error)
        }
    }

    /**
     * Commit a alteração dos "quadradinhos" (dias concluídos) de uma Etapa.
     * - Normaliza o set para ficar DENTRO do intervalo planejado [dataInicio até dataFim]
     * - Recalcula progresso (0..100)
     * - Deriva status ("Pendente" | "Andamento" | "Concluído")
     * - Persiste via update parcial (diasConcluidos, progresso, status)
     *
     * Sem "flash" de loading: a tela receberá a atualização pelo observeEtapas().
     */
    fun commitDias(etapa: Etapa, novoSetUtc: Set<String>) = viewModelScope.launch(io) {
        // Range planejado em LocalDate
        val range: List<LocalDate> = GanttUtils.diasPlanejados(etapa.dataInicio, etapa.dataFim)
        val total = range.size
        val rangeSet = range.toSet()

        // Normaliza: mantém apenas dias dentro do range planejado
        val normalizadoUtc: Set<String> = novoSetUtc
            .mapNotNull { GanttUtils.utcStringToLocalDate(it) }
            .filter { it in rangeSet }
            .map { GanttUtils.localDateToUtcString(it) }
            .toSet()

        // Recalcula progresso e status
        val done = normalizadoUtc
            .mapNotNull { GanttUtils.utcStringToLocalDate(it) }
            .count { it in rangeSet }

        val progresso = if (total == 0) 0
        else (100.0 * done / total.toDouble()).roundToInt().coerceIn(0, 100)

        // Update parcial (seguro contra sobrescrever outros campos)
        val campos: Map<String, Any?> = mapOf(
            "diasConcluidos" to normalizadoUtc.toList().ifEmpty { null }, // << troquei para .toList()
            "progresso"      to progresso,
            "status"         to CronStatus.statusAuto(done, total)        // << usa CronStatus
        )

        // Prefira update parcial para evitar condições de corrida.
        repo.updateEtapaCampos(obraId, etapa.id, campos)
            .onFailure {
                // Opcional: notificar erro temporário (sem "flash" de UI)
                // _opState.value = UiState.ErrorRes(R.string.etapa_update_error)
            }
        // Sucesso: a lista será atualizada pelo observeEtapas()
    }

    /**
     * Opcional: permite "forçar" um percentual via slider/controle.
     * - Converte % → subconjunto de dias (primeiros N dias do intervalo)
     * - Reutiliza commitDias(...) para persistir e recalcular tudo.
     */
    fun commitProgresso(etapa: Etapa, novoPercentual: Int) = viewModelScope.launch(io) {
        val pct = novoPercentual.coerceIn(0, 100)

        val range: List<LocalDate> = GanttUtils.diasPlanejados(etapa.dataInicio, etapa.dataFim)
        val total = range.size
        if (total <= 0) {
            // Sem range válido: zera tudo
            commitDias(etapa, emptySet())
            return@launch
        }

        val n = (total * (pct / 100.0)).roundToInt().coerceIn(0, total)
        val subsetUtc: Set<String> = range
            .take(n)
            .map { GanttUtils.localDateToUtcString(it) }
            .toSet()

        commitDias(etapa, subsetUtc)
    }
}