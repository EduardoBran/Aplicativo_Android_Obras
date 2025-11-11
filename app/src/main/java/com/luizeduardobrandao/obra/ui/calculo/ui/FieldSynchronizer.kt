package com.luizeduardobrandao.obra.ui.calculo.ui

import android.widget.RadioGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel

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
     *
     * Detecta reset geral e limpa todos os campos quando inputs == Inputs()
     */
    fun syncAllFields(
        inputs: CalcRevestimentoViewModel.Inputs,
        etComp: TextInputEditText,
        etLarg: TextInputEditText,
        etAlt: TextInputEditText,
        etParedeQtd: TextInputEditText,
        etAbertura: TextInputEditText,
        etAreaInformada: TextInputEditText,
        etPecaComp: TextInputEditText,
        etPecaLarg: TextInputEditText,
        etPecaEsp: TextInputEditText,
        etJunta: TextInputEditText,
        etSobra: TextInputEditText,
        etPecasPorCaixa: TextInputEditText,
        etDesnivel: TextInputEditText,
        etRodapeAltura: TextInputEditText,
        etRodapeAbertura: TextInputEditText,
        etRodapeCompComercial: TextInputEditText,
        tilComp: TextInputLayout,
        tilLarg: TextInputLayout,
        tilAltura: TextInputLayout,
        tilParedeQtd: TextInputLayout,
        tilAbertura: TextInputLayout,
        tilAreaInformada: TextInputLayout,
        tilPecaComp: TextInputLayout,
        tilPecaLarg: TextInputLayout,
        tilPecaEsp: TextInputLayout,
        tilJunta: TextInputLayout,
        tilPecasPorCaixa: TextInputLayout,
        tilDesnivel: TextInputLayout,
        tilSobra: TextInputLayout,
        tilRodapeAltura: TextInputLayout,
        tilRodapeAbertura: TextInputLayout,
        tilRodapeCompComercial: TextInputLayout,
        rgPastilhaTamanho: RadioGroup,
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
                rgPastilhaTamanho
            )
            return
        }

        // Sincroniza campos individuais
        if (hasAnyFieldValue(inputs)) {
            syncField(etComp, inputs.compM)
            syncField(etLarg, inputs.largM)
            syncField(etAlt, inputs.altM)
            syncField(etAreaInformada, inputs.areaInformadaM2)
            syncIntField(etParedeQtd, inputs.paredeQtd)
            syncField(etAbertura, inputs.aberturaM2)
            syncFieldPeca(etPecaComp, inputs.pecaCompCm, isMG)
            syncFieldPeca(etPecaLarg, inputs.pecaLargCm, isMG)
            syncEspessuraField(etPecaEsp, inputs.pecaEspMm, inputs.revest)
            syncField(etJunta, inputs.juntaMm)
            syncField(etSobra, inputs.sobraPct)
            syncField(etRodapeAltura, inputs.rodapeAlturaCm)
            syncField(etRodapeAbertura, inputs.rodapeDescontarVaoM.takeIf { it > 0.0 })
            syncIntField(etPecasPorCaixa, inputs.pecasPorCaixa)
        }
    }

    /**
     * Verifica se há algum valor de campo nos inputs
     */
    private fun hasAnyFieldValue(inputs: CalcRevestimentoViewModel.Inputs): Boolean {
        return inputs.compM != null || inputs.largM != null || inputs.altM != null ||
                inputs.areaInformadaM2 != null || inputs.pecaCompCm != null ||
                inputs.pecaLargCm != null || inputs.juntaMm != null
    }

    /**
     * Limpa todos os campos e erros
     */
    private fun clearAllFields(
        etComp: TextInputEditText,
        etLarg: TextInputEditText,
        etAlt: TextInputEditText,
        etParedeQtd: TextInputEditText,
        etAbertura: TextInputEditText,
        etAreaInformada: TextInputEditText,
        etPecaComp: TextInputEditText,
        etPecaLarg: TextInputEditText,
        etPecaEsp: TextInputEditText,
        etJunta: TextInputEditText,
        etSobra: TextInputEditText,
        etPecasPorCaixa: TextInputEditText,
        etDesnivel: TextInputEditText,
        etRodapeAltura: TextInputEditText,
        etRodapeAbertura: TextInputEditText,
        etRodapeCompComercial: TextInputEditText,
        tilComp: TextInputLayout,
        tilLarg: TextInputLayout,
        tilAltura: TextInputLayout,
        tilParedeQtd: TextInputLayout,
        tilAbertura: TextInputLayout,
        tilAreaInformada: TextInputLayout,
        tilPecaComp: TextInputLayout,
        tilPecaLarg: TextInputLayout,
        tilPecaEsp: TextInputLayout,
        tilJunta: TextInputLayout,
        tilPecasPorCaixa: TextInputLayout,
        tilDesnivel: TextInputLayout,
        tilSobra: TextInputLayout,
        tilRodapeAltura: TextInputLayout,
        tilRodapeAbertura: TextInputLayout,
        tilRodapeCompComercial: TextInputLayout,
        rgPastilhaTamanho: RadioGroup
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

        // Limpa RadioGroup de pastilha
        rgPastilhaTamanho.clearCheck()
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

    /**
     * Sincroniza campo inteiro
     */
    private fun syncIntField(et: TextInputEditText, value: Int?) {
        if (et.hasFocus()) return
        value?.let {
            val current = et.text?.toString()?.toIntOrNull()
            if (current != it) et.setText(it.toString())
        }
    }

    /**
     * Sincroniza campo de peça (com conversão de unidade)
     * MG: valor em cm, exibição em m (÷100)
     * Outros: valor em cm, exibição em cm (ou detecta m)
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
     * Intertravado: armazenado em mm, exibido em cm (÷10)
     * Outros: armazenado em mm, exibido em mm
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
}