package com.luizeduardobrandao.obra.ui.dadosobra

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Obra
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.data.repository.ObraRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsável pela tela **DadosObraFragment**.
 *
 * • [obraState] – acompanha a obra em tempo real (Idle/Loading/Success/ErrorRes)
 * • [opState]   – resultado das operações Salvar, Atualizar Saldo ou Excluir
 */

@HiltViewModel
class DadosObraViewModel @Inject constructor(
    private val obraRepo: ObraRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // obraId vindo via SafeArgs/SavedStateHandle
    private val obraId: String =
        savedStateHandle["obraId"]
            ?: error("Safe-Args deve fornecer obraId para DadosObraFragment")

    // Estado da obra (lido via observeObras).
    private val _obraState = MutableStateFlow<UiState<Obra>>(UiState.Loading)
    val obraState: StateFlow<UiState<Obra>> = _obraState.asStateFlow()

    // Estado de operações (Salvar, Atualizar Saldo, Excluir).
    private val _opState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val opState: StateFlow<UiState<Unit>> = _opState.asStateFlow()


    // Escuta contínua da obra para refletir mudanças em tempo real
    init {
        obraRepo.observeObras()
            .map { list -> list.firstOrNull { it.obraId == obraId } }
            .catch { _obraState.value = UiState.ErrorRes(R.string.dados_obra_load_error) }
            .onEach { obra ->
                _obraState.value = obra
                    ?.let { UiState.Success(it) }
                    ?: UiState.ErrorRes(R.string.dados_obra_not_found)
            }
            .flowOn(io)
            .launchIn(viewModelScope)
    }

    // Salva campos editáveis da obra (não altera saldoInicial nem gastoTotal).
    fun salvarObra(
        nome: String,
        endereco: String,
        descricao: String,
        dataInicio: String,
        dataFim: String
    ) {
        viewModelScope.launch(io) {
            _opState.value = UiState.Loading

            val campos = mapOf(
                "nomeCliente" to nome.trim(),
                "endereco"    to endereco.trim(),
                "descricao"   to descricao.trim(),
                "dataInicio"  to dataInicio,
                "dataFim"     to dataFim
            )

            _opState.value = obraRepo.updateObraCampos(obraId, campos)
                .fold(
                    onSuccess = { UiState.Success(Unit) },
                    onFailure = { UiState.ErrorRes(R.string.dados_obra_update_error) }
                )
        }
    }


    // Atualiza apenas o campo saldoAjustado.
    fun atualizarSaldoAjustado(novoValor: Double) {
        viewModelScope.launch(io) {
            _opState.value = UiState.Loading
            _opState.value = obraRepo.updateSaldoAjustado(obraId, novoValor)
                .fold(
                    onSuccess = { UiState.Success(Unit) },
                    onFailure = { UiState.ErrorRes(R.string.dados_obra_update_balance_error) }
                )
        }
    }

    // Exclui completamente a obra.
    fun excluirObra() {
        viewModelScope.launch(io) {
            _opState.value = UiState.Loading
            _opState.value = obraRepo.deleteObra(obraId)
                .fold(
                    onSuccess = { UiState.Success(Unit) },
                    onFailure = { UiState.ErrorRes(R.string.dados_obra_delete_error) }
                )
        }
    }

    // Reseta o estado de operação após consumido.
    fun resetOpState() {
        _opState.value = UiState.Idle
    }
}