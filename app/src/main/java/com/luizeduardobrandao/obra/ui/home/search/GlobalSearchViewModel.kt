package com.luizeduardobrandao.obra.ui.home.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.data.model.Etapa
import com.luizeduardobrandao.obra.data.model.Funcionario
import com.luizeduardobrandao.obra.data.model.Material
import com.luizeduardobrandao.obra.data.model.Nota
import com.luizeduardobrandao.obra.data.repository.CronogramaRepository
import com.luizeduardobrandao.obra.data.repository.FuncionarioRepository
import com.luizeduardobrandao.obra.data.repository.MaterialRepository
import com.luizeduardobrandao.obra.data.repository.NotaRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    cronRepo: CronogramaRepository,
    funcRepo: FuncionarioRepository,
    matRepo: MaterialRepository,
    notaRepo: NotaRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val obraId: String = checkNotNull(savedStateHandle.get<String>("obraId")) {
        "Safe-Args deve conter obraId"
    }
    val query: String = savedStateHandle.get<String>("query").orEmpty()

    sealed interface Ui {
        data object Loading : Ui
        data class Success(
            val query: String,
            val etapas: List<Etapa>,
            val funcionarios: List<Funcionario>,
            val materiais: List<Material>,
            val notas: List<Nota>,
            val total: Int
        ) : Ui

        data class Error(val message: String) : Ui
    }

    private val _ui = MutableStateFlow<Ui>(Ui.Loading)
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private val _latestFuncionarios = MutableStateFlow<List<Funcionario>>(emptyList())
    val latestFuncionarios: StateFlow<List<Funcionario>> = _latestFuncionarios.asStateFlow()

    init {
        val etapasFlow: Flow<List<Etapa>> = cronRepo.observeEtapas(obraId)
        val funcsFlow: Flow<List<Funcionario>> = funcRepo.observeFuncionarios(obraId)
        val matsFlow: Flow<List<Material>> = matRepo.observeMateriais(obraId)
        val notasFlow: Flow<List<Nota>> = notaRepo.observeNotas(obraId)

        val searchFlow: Flow<Ui> =
            combine(etapasFlow, funcsFlow, matsFlow, notasFlow) { etapas, funcs, mats, notas ->
                _latestFuncionarios.value = funcs

                val q = query.trim()
                if (q.isBlank()) {
                    return@combine Ui.Success(
                        q,
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        0
                    )
                }

                // ─── NOVO: normalização e tokenização por palavras ───
                val tokens: List<String> = q
                    .normalized()
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }

                fun matchAnyToken(s: String?): Boolean {
                    if (s.isNullOrBlank()) return false
                    val norm = s.normalized()
                    // OR: basta 1 token bater
                    return tokens.any { norm.contains(it) }
                    // Se quiser comportamento AND (todos tokens):
                    // return tokens.all { norm.contains(it) }
                }

                val e = etapas.filter { matchAnyToken(it.titulo) }
                val f = funcs.filter { matchAnyToken(it.nome) }
                val m = mats.filter { matchAnyToken(it.nome) }
                val n = notas.filter { matchAnyToken(it.nomeMaterial) }

                Ui.Success(q, e, f, m, n, e.size + f.size + m.size + n.size)
            }

        searchFlow
            .flowOn(io)
            .catch { _ui.value = Ui.Error(it.message ?: "Falha ao buscar.") }
            .onEach { _ui.value = it }
            .launchIn(viewModelScope)
    }
}

/* ───────────────── helpers ───────────────── */
private fun String.normalized(): String {
    // remove acentos e baixa caixa
    val tmp = Normalizer.normalize(this, Normalizer.Form.NFD)
    return tmp.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.getDefault())
}