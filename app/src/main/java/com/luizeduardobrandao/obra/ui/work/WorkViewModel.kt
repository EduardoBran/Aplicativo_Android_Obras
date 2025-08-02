package com.luizeduardobrandao.obra.ui.work

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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsável por orquestrar a lógica da WorkFragment:
 * 1. Observar em tempo real a lista de obras do usuário;
 * 2. Expor o estado dessa lista via UiState (Loading, Success, ErrorRes);
 * 3. Criar novas obras e expor o estado do envio via UiState (Loading, Success, ErrorRes).
 */

@HiltViewModel
class WorkViewModel @Inject constructor(
    private val obraRepo: ObraRepository,               // Repositório para CRUD de obras
    @IoDispatcher private val io: CoroutineDispatcher   // Dispatcher de I/O para operações de rede
) : ViewModel() {

    // ─────────────── Fluxo de Estado das Obras ───────────────

    // Estado interno: lista de obras. (Inicialmente em Loading enquanto busca no Firebase.)
    private val _obrasState = MutableStateFlow<UiState<List<Obra>>>(UiState.Loading)
    /**
     * Estado exposto para a UI:
     * • Loading  – carregamento em andamento
     * • Success  – lista de obras disponível
     * • ErrorRes – falha ao carregar, com string-resource de mensagem
     */
    val obrasState: StateFlow<UiState<List<Obra>>> = _obrasState.asStateFlow()

    // ─────────────── Fluxo de Estado de Criação ───────────────

    /**
     * Estado interno do envio de nova obra:
     * • Idle     – nenhum envio em andamento
     * • Loading  – envio em progresso
     * • Success  – obra criada com sucesso (retorna a key gerada)
     * • ErrorRes – falha ao criar, com string-resource de mensagem
     */
    private val _createState = MutableStateFlow<UiState<String>>(UiState.Idle)
    // Exposição imutável para a UI reagir ao resultado do createObra().
    val createState: StateFlow<UiState<String>> = _createState.asStateFlow()


    // ───────────────────── Init: Observação ─────────────────────
    init {
        viewModelScope.launch(io) {
            obraRepo.observeObras()
                .catch {
                    // Primeiro trata erro de rede/DB
                    _obrasState.value = UiState.ErrorRes(R.string.work_load_error)
                }
                .onEach { list ->
                    // Depois emite a lista com sucesso
                    _obrasState.value = UiState.Success(list)
                }
                .collect() // materializa o fluxo
        }
    }


    // ─────────────────────── API Pública ────────────────────────

    /**
     * Dispara a criação de uma nova obra no Firebase.
     * A UI deve observar [createState] para reagir ao fluxo:
     *  - Loading: mostra ProgressBar
     *  - Success: fecha o Card e navega/adiciona na lista
     *  - ErrorRes: exibe Snackbar com a mensagem apropriada
     *
     * @param obra Objeto preenchido com os dados do formulário
     */
    fun createObra(obra: Obra){
        // Emite Loading antes de chamar o repositório
        _createState.value = UiState.Loading

        viewModelScope.launch(io) {
            // Chama o repositório para adicionar a obra
            val result = obraRepo.addObra(obra)

            // Converte Result<String> em UiState<String>
            _createState.value = result.fold(
                onSuccess = { key ->
                    UiState.Success(key)               // Sucesso, retorna key da obra criada
                },
                onFailure = {
                    UiState.ErrorRes(R.string.work_create_error)
                }
            )
        }
    }


    /**
     * Reseta o fluxo de criação para Idle. Deve ser chamado pela UI
     * após consumir um sucesso ou erro (por exemplo, ao fechar o Card).
     */
    fun resetCreateState() {
        _createState.value = UiState.Idle
    }
}