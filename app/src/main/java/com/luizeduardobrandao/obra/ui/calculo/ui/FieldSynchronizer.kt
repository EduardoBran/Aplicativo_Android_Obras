package com.luizeduardobrandao.obra.ui.calculo.ui

import android.widget.RadioGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel
import com.luizeduardobrandao.obra.ui.calculo.domain.specifications.RevestimentoSpecifications

/**
 * Gerencia a sincronização de campos de texto com o estado do ViewModel
 *
 * Responsável por:
 * - Sincronizar valores de EditText sem disparar listeners
 * - Limpar campos quando necessário
 * - Tratar conversões de unidades (m→cm, cm→mm)
 * - Evitar loops infinitos de atualização
 */
class FieldSynchronizer {

    /**
     * Sincroniza todos os campos com o estado atual dos Inputs
     * Detecta reset geral e limpa todos os campos quando inputs == Inputs()
     */
    fun syncAllFields(
        inputs: CalcRevestimentoViewModel.Inputs,
        etComp: TextInputEditText, etLarg: TextInputEditText, etAlt: TextInputEditText,
        etParedeQtd: TextInputEditText, etAbertura: TextInputEditText,
        etAreaInformada: TextInputEditText, etPecaComp: TextInputEditText,
        etPecaLarg: TextInputEditText, etPecaEsp: TextInputEditText, etJunta: TextInputEditText,
        etSobra: TextInputEditText, etPecasPorCaixa: TextInputEditText,
        etDesnivel: TextInputEditText, etRodapeAltura: TextInputEditText,
        etRodapeAbertura: TextInputEditText, etRodapeCompComercial: TextInputEditText,
        tilComp: TextInputLayout, tilLarg: TextInputLayout, tilAltura: TextInputLayout,
        tilParedeQtd: TextInputLayout, tilAbertura: TextInputLayout,
        tilAreaInformada: TextInputLayout, tilPecaComp: TextInputLayout,
        tilPecaLarg: TextInputLayout, tilPecaEsp: TextInputLayout, tilJunta: TextInputLayout,
        tilPecasPorCaixa: TextInputLayout, tilDesnivel: TextInputLayout, tilSobra: TextInputLayout,
        tilRodapeAltura: TextInputLayout, tilRodapeAbertura: TextInputLayout,
        tilRodapeCompComercial: TextInputLayout,
        rgPastilhaTamanho: RadioGroup,
        rgPastilhaPorcelanatoTamanho: RadioGroup,
        isMG: Boolean
    ) {
        // Detecta reset geral
        if (inputs == CalcRevestimentoViewModel.Inputs()) {
            clearAllFields(
                etComp, etLarg, etAlt, etParedeQtd, etAbertura, etAreaInformada,
                etPecaComp, etPecaLarg, etPecaEsp, etJunta, etSobra, etPecasPorCaixa,
                etDesnivel, etRodapeAltura, etRodapeAbertura, etRodapeCompComercial,
                tilComp, tilLarg, tilAltura, tilParedeQtd, tilAbertura, tilAreaInformada,
                tilPecaComp, tilPecaLarg, tilPecaEsp, tilJunta, tilPecasPorCaixa,
                tilDesnivel, tilSobra, tilRodapeAltura, tilRodapeAbertura, tilRodapeCompComercial,
                rgPastilhaTamanho, rgPastilhaPorcelanatoTamanho
            )
            return
        }

        // Se não há nenhum valor em nenhum campo relevante, não precisa sincronizar
        if (!hasAnyFieldValue(inputs)) return

        // Informação se o cenário atual possui rodapé (para mostrar/limpar campos de rodapé)
        val hasRodapeStep = RevestimentoSpecifications.hasRodapeStep(inputs)

        // ─── Medidas da Área ───
        syncField(etComp, inputs.compM)
        syncField(etLarg, inputs.largM)
        syncField(etAlt, inputs.altM)
        syncField(etAreaInformada, inputs.areaInformadaM2)
        syncIntField(etParedeQtd, inputs.paredeQtd)
        syncField(etAbertura, inputs.aberturaM2)
        // ─── Medidas da Peça ───
        syncFieldPeca(etPecaComp, inputs.pecaCompCm, isMG)
        syncFieldPeca(etPecaLarg, inputs.pecaLargCm, isMG)
        syncEspessuraField(etPecaEsp, inputs.pecaEspMm, inputs.revest)
        syncField(etJunta, inputs.juntaMm)
        syncField(etSobra, inputs.sobraPct)
        syncIntField(etPecasPorCaixa, inputs.pecasPorCaixa)
        syncField(etDesnivel, inputs.desnivelCm)
        // ─── Rodapé na Tela de Medidas da Peça ───
        if (hasRodapeStep) {
            // Cenário suporta rodapé → sincroniza campos normalmente
            syncField(etRodapeAltura, inputs.rodapeAlturaCm)
            syncField(
                etRodapeAbertura,
                inputs.rodapeDescontarVaoM.takeIf { it > 0.0 }
            )
            syncRodapeCompComercialField(etRodapeCompComercial, inputs.rodapeCompComercialM)
        } else {
            // Cenário NÃO tem rodapé → limpa apenas campos de rodapé, não toca nos outros campos
            clearRodapeFields(
                etRodapeAltura, etRodapeAbertura, etRodapeCompComercial,
                tilRodapeAltura, tilRodapeAbertura, tilRodapeCompComercial
            )
        }
    }

    /** Verifica se há algum valor de campo nos inputs */
    private fun hasAnyFieldValue(inputs: CalcRevestimentoViewModel.Inputs): Boolean {
        return inputs.compM != null ||
                inputs.largM != null || inputs.altM != null || inputs.areaInformadaM2 != null ||
                inputs.pecaCompCm != null || inputs.pecaLargCm != null || inputs.juntaMm != null ||
                inputs.sobraPct != null || inputs.desnivelCm != null || inputs.paredeQtd != null ||
                inputs.aberturaM2 != null || inputs.rodapeAlturaCm != null ||
                inputs.rodapeCompComercialM != null || inputs.rodapeDescontarVaoM > 0.0
    }

    /** Limpa todos os campos e erros */
    private fun clearAllFields(
        etComp: TextInputEditText, etLarg: TextInputEditText, etAlt: TextInputEditText,
        etParedeQtd: TextInputEditText, etAbertura: TextInputEditText,
        etAreaInformada: TextInputEditText, etPecaComp: TextInputEditText,
        etPecaLarg: TextInputEditText, etPecaEsp: TextInputEditText,
        etJunta: TextInputEditText, etSobra: TextInputEditText, etPecasPorCaixa: TextInputEditText,
        etDesnivel: TextInputEditText, etRodapeAltura: TextInputEditText,
        etRodapeAbertura: TextInputEditText, etRodapeCompComercial: TextInputEditText,
        tilComp: TextInputLayout, tilLarg: TextInputLayout, tilAltura: TextInputLayout,
        tilParedeQtd: TextInputLayout, tilAbertura: TextInputLayout,
        tilAreaInformada: TextInputLayout, tilPecaComp: TextInputLayout,
        tilPecaLarg: TextInputLayout, tilPecaEsp: TextInputLayout, tilJunta: TextInputLayout,
        tilPecasPorCaixa: TextInputLayout, tilDesnivel: TextInputLayout, tilSobra: TextInputLayout,
        tilRodapeAltura: TextInputLayout, tilRodapeAbertura: TextInputLayout,
        tilRodapeCompComercial: TextInputLayout,
        rgPastilhaTamanho: RadioGroup, rgPastilhaPorcelanatoTamanho: RadioGroup
    ) {
        // Limpa textos
        listOf(
            etComp, etLarg, etAlt, etParedeQtd, etAbertura, etAreaInformada,
            etPecaComp, etPecaLarg, etPecaEsp, etJunta, etSobra, etPecasPorCaixa,
            etDesnivel, etRodapeAltura, etRodapeAbertura, etRodapeCompComercial
        ).forEach { it.text?.clear() }
        // Limpa erros
        listOf(
            tilComp, tilLarg, tilAltura, tilParedeQtd, tilAbertura, tilAreaInformada,
            tilPecaComp, tilPecaLarg, tilPecaEsp, tilJunta, tilPecasPorCaixa,
            tilDesnivel, tilSobra, tilRodapeAltura, tilRodapeAbertura, tilRodapeCompComercial
        ).forEach { it.error = null }
        // Limpa RadioGroups de pastilha
        rgPastilhaTamanho.clearCheck()
        rgPastilhaPorcelanatoTamanho.clearCheck()
    }

    /** Limpa apenas os campos de rodapé (texto + erros), sem mexer nos demais campos da tela. */
    private fun clearRodapeFields(
        etRodapeAltura: TextInputEditText,
        etRodapeAbertura: TextInputEditText,
        etRodapeCompComercial: TextInputEditText,
        tilRodapeAltura: TextInputLayout,
        tilRodapeAbertura: TextInputLayout,
        tilRodapeCompComercial: TextInputLayout
    ) {
        listOf(etRodapeAltura, etRodapeAbertura, etRodapeCompComercial).forEach {
            it.text?.clear()
        }
        listOf(tilRodapeAltura, tilRodapeAbertura, tilRodapeCompComercial).forEach {
            it.error = null
        }
    }

    /**
     * Sincroniza campo simples (Double)
     * Só atualiza se o campo não tiver foco e o valor for diferente
     */
    private fun syncField(et: TextInputEditText, value: Double?) {
        if (et.hasFocus()) return
        if (value != null) {
            val currentNum = et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
            if (currentNum != value) {
                et.setText(value.toString().replace(".", ","))
            }
        }
    }

    /** Sincroniza campo inteiro */
    private fun syncIntField(et: TextInputEditText, value: Int?) {
        if (et.hasFocus()) return
        value?.let {
            val current = et.text?.toString()?.toIntOrNull()
            if (current != it) et.setText(it.toString())
        }
    }

    /**
     * Sincroniza campo de peça (com conversão de unidade)
     * MG: valor em cm, exibição em m (÷100); Outros: valor em cm, exibição em cm (ou detecta m)
     */
    private fun syncFieldPeca(et: TextInputEditText, valueCm: Double?, isMG: Boolean) {
        if (et.hasFocus() || valueCm == null) return

        val raw = et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()

        // Se usuário digitou em metros (<1.0), não atualiza se já está correto
        if (!isMG && raw != null && raw < 1.0) {
            val asCm = raw * 100.0
            if (kotlin.math.abs(asCm - valueCm) < 1e-6) return
        }

        // Display: MG em metros, outros em cm
        val display = if (isMG) valueCm / 100.0 else valueCm
        if (raw == null || kotlin.math.abs(raw - display) > 1e-6) {
            et.setText(display.toString().replace(".", ","))
        }
    }

    /**
     * Sincroniza campo de espessura
     * Intertravado: armazena em mm, exibe em cm; Outros: armazenado em mm, exibido em mm
     */
    private fun syncEspessuraField(
        et: TextInputEditText,
        valueMm: Double?,
        revest: CalcRevestimentoViewModel.RevestimentoType?
    ) {
        if (et.hasFocus() || valueMm == null) return

        val display = if (revest == CalcRevestimentoViewModel.RevestimentoType.PISO_INTERTRAVADO) {
            valueMm / 10.0 // mm → cm
        } else {
            valueMm
        }
        syncField(et, display)
    }

    /** Sincroniza comprimento comercial do rodapé. ViewModel armazena em metros, tela exibe em cm
     */
    private fun syncRodapeCompComercialField(
        et: TextInputEditText,
        valueM: Double?
    ) {
        if (et.hasFocus() || valueM == null) return
        val valueCm = valueM * 100.0
        val currentNum = et.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
        if (currentNum == null || kotlin.math.abs(currentNum - valueCm) > 1e-6) {
            et.setText(valueCm.toString().replace(".", ","))
        }
    }
}