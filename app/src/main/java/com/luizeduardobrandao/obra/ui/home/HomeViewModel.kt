package com.luizeduardobrandao.obra.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.obra.R
import com.luizeduardobrandao.obra.data.model.Obra
import com.luizeduardobrandao.obra.data.model.UiState
import com.luizeduardobrandao.obra.data.repository.AuthRepository
import com.luizeduardobrandao.obra.data.repository.ObraRepository
import com.luizeduardobrandao.obra.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel da **HomeFragment** – ponto central da obra selecionada.
 *
 *  • Mantém a [Obra] atual em fluxo.
 *  • Escuta alterações de Firebase (gastoTotal, saldoRestante…).
 *  • Exponde evento de *logout* para a UI.
 */

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val obraRepo: ObraRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle                    // recebe obraId via SafeArgs
) : ViewModel() {

    // Identificador da obra selecionada, obtido via SafeArgs
    private val obraId: String =
        savedStateHandle.get<String>("obraId")
            ?: throw IllegalArgumentException("SafeArgs deve conter obraId para HomeFragment.")

    /*──────────────────────────  Estado da Obra Selecionada  ──────────────────────────*/

    // Estado interno da obra: Loading, Success(com Obra) ou ErrorRes(com recurso de string)
    private val _obraState = MutableStateFlow<UiState<Obra>>(UiState.Loading)
    /**
     * Estado exposto para a UI:
     * • Loading    – carregando dados da obra
     * • Success    – dados da obra disponíveis
     * • ErrorRes   – falha ao obter dados (usando String-res)
     */
    val obraState: StateFlow<UiState<Obra>> = _obraState.asStateFlow()


    // Inicia a observação contínua da lista de obras
    init {
        // Inicia a observação contínua da lista de obras
        viewModelScope.launch(io) {
            obraRepo.observeObras()
                // Filtra a obra que corresponde ao obraId selecionado
                .map { list -> list.firstOrNull { it.obraId == obraId } }
                // Em caso de falha de rede ou Firebase, emite ErrorRes genérico
                .catch {
                    _obraState.value = UiState.ErrorRes(R.string.home_error_load)
                }
                // A cada atualização, emite Success ou ErrorRes se não encontrar a obra
                .onEach { obra ->
                    _obraState.value = obra
                        ?.let { UiState.Success(it) }
                        ?: UiState.ErrorRes(R.string.home_error_missing_obra)
                }
                // Coleta efetivamente o fluxo
                .collect()
        }
    }


    /*──────────────────────────────  Evento de Logout  ───────────────────────────────*/

    // Canal para emitir evento único de logout (navegação + feedback)
    private val _logoutEvent = Channel<Unit>(Channel.CONFLATED)
    // Fluxo que a UI deve observar para reagir ao logout.
    val logoutEvent: Flow<Unit> = _logoutEvent.receiveAsFlow()

    // Executa logout global e notifica a UI para navegar de volta ao Login.
    fun logout() {
        viewModelScope.launch(io) {
            authRepo.signOut()            // Desloga via Firebase
            _logoutEvent.trySend(Unit)    // Emite evento sem bloquear
        }
    }
}