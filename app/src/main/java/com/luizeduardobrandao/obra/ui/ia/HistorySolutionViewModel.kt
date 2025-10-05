package com.luizeduardobrandao.obra.ui.ia

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.data.model.SolutionHistory
import com.luizeduardobrandao.obra.data.repository.SolutionHistoryRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HistorySolutionViewModel @Inject constructor(
    private val repo: SolutionHistoryRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val obraId: String = savedStateHandle["obraId"] ?: error("obraId ausente")
    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    val history: StateFlow<List<SolutionHistory>> =
        repo.observeHistory(obraId)
            .map { list ->
                list.sortedWith(
                    compareByDescending<SolutionHistory> { item ->
                        // tenta parsear a data (dd/MM/yyyy); em caso de falha, trata como 0
                        runCatching { sdf.parse(item.date)?.time ?: 0L }.getOrElse { 0L }
                    }.thenByDescending { it.date } // desempate estável
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList()
            )

    suspend fun delete(id: String) {
        withContext(io) { repo.delete(obraId, id) }
    }
}