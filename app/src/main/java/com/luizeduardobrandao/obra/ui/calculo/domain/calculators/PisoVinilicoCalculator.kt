package com.luizeduardobrandao.obra.ui.calculo.domain.calculators

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import com.luizeduardobrandao.obra.ui.calculo.domain.rules.CalcRevestimentoRules
import com.luizeduardobrandao.obra.ui.calculo.utils.NumberFormatter
import kotlin.math.ceil
import kotlin.math.max

/**
 * Calculadora específica para Piso Vinílico.
 *
 * Regras principais:
 * - Usa área base (piso) + área de rodapé (quando habilitado).
 * - Trabalha com:
 *      • pisoVinilicoAutoAdesivo        -> define se usa cola ou não
 *      • pisoVinilicoDesnivelAtivo      -> define se usa massa PVA
 *      • pisoVinilicoDesnivelTipo       -> GROSSO / FINO (muda rendimento da massa)
 *      • pisoVinilicoQtdDemaos          -> quantidade de demãos (1..4)
 */
object PisoVinilicoCalculator {

    // ========================= CONSTANTES DE RENDIMENTO =========================

    // Cola para piso vinílico
    private const val RENDIMENTO_COLA_M2_POR_KG =
        CalcRevestimentoRules.PisoVinilico.RENDIMENTO_COLA_M2_POR_KG

    // Massa PVA para regularização
    private const val RENDIMENTO_PVA_GROSSO_M2_POR_KG_DEMAO =
        CalcRevestimentoRules.PisoVinilico.RENDIMENTO_PVA_GROSSO_M2_POR_KG
    private const val RENDIMENTO_PVA_LISO_M2_POR_KG_DEMAO =
        CalcRevestimentoRules.PisoVinilico.RENDIMENTO_PVA_LISO_M2_POR_KG

    // Altura padrão considerada para rodapé na conta de regularização (m)
    private const val ALTURA_RODAPE_PADRAO_M =
        CalcRevestimentoRules.PisoVinilico.ALTURA_RODAPE_PADRAO_M

    /**
     * Processa todos os materiais de Piso Vinílico:
     *
     * @param inputs                Inputs completos do cálculo (ViewModel.Inputs)
     * @param areaRevestimentoM2    Área principal calculada no ViewModel (piso),
     *                              será usada como fallback, mas a lógica principal
     *                              considera a área base do ambiente.
     * @param rodapePerimetroM      Perímetro de rodapé em metros (L_rodape_m),
     *                              já vindo pronto do ViewModel.
     * @param sobra                 Sobra técnica em % (0–50).
     * @param itens                 Lista mutável onde os materiais serão adicionados.
     */
    @Suppress("KotlinConstantConditions")
    fun processarPisoVinilico(
        inputs: Inputs,
        areaRevestimentoM2: Double,
        rodapePerimetroM: Double,
        sobra: Double,
        itens: MutableList<MaterialItem>
    ) {
        // ================= 0) ÁREA BASE DO PISO (SEM RODAPÉ) =================
        // Aqui usamos a área base do ambiente como A_m2.
        // Caso não esteja disponível por algum motivo, usamos o valor recebido.
        val areaBaseM2 = max(0.0, areaRevestimentoM2)

        // ================= 1) ÁREA DA PEÇA (m²) =================
        val compPecaCm = inputs.pecaCompCm ?: 0.0
        val largPecaCm = inputs.pecaLargCm ?: 0.0
        val compPecaM = compPecaCm / 100.0
        val largPecaM = largPecaCm / 100.0

        if (compPecaM <= 0.0 || largPecaM <= 0.0) {
            // Se chegou aqui sem dimensões válidas, não tem como calcular
            return
        }

        val areaPecaM2 = compPecaM * largPecaM

        // ================= 2) ÁREA DE RODAPÉ (m²) =================
        val perRodapeM = if (inputs.rodapeEnable) rodapePerimetroM.coerceAtLeast(0.0) else 0.0
        val areaRodapeM2 = perRodapeM * ALTURA_RODAPE_PADRAO_M

        // ================= 3) ÁREA TOTAL DE REVESTIMENTO (PISO + RODAPÉ) =================
        val areaTotalRevestimentoM2 = areaBaseM2 + areaRodapeM2

        // ================= 4) APLICAR SOBRA TÉCNICA =================
        val fatorSobra = 1.0 + (sobra / 100.0)     // ex.: sobra=10 -> fator=1.10
        val areaComSobraM2 = areaTotalRevestimentoM2 * fatorSobra

        // ================= 5) QUANTIDADE DE PEÇAS =================
        val qtdPecasLiquida = if (areaPecaM2 > 0) areaTotalRevestimentoM2 / areaPecaM2 else 0.0
        val qtdPecasComSobra = ceil(qtdPecasLiquida * fatorSobra)

        // ================= 6) ITEM PRINCIPAL – PISO VINÍLICO =================
        val observacaoRevest = MaterialCalculator.buildObservacaoRevestimento(
            sobra = sobra,
            qtdPecas = qtdPecasComSobra,
            pecasPorCaixa = inputs.pecasPorCaixa,
            pecaCompCm = inputs.pecaCompCm,
            pecaLargCm = inputs.pecaLargCm
        )

        itens += MaterialItem(
            item = "Piso vinílico",
            unid = "m²",
            qtd = NumberFormatter.arred2(areaComSobraM2),
            observacao = observacaoRevest
        )

        // ================= 7) CONSUMO DE COLA (QUANDO USA COLA) =================
        // - switch desligado  -> não usa cola -> não calcula
        // - switch ligado     -> usa cola     -> calcula
        if (!inputs.pisoVinilicoAutoAdesivo) {   // <-- antes era (!inputs.pisoVinilicoAutoAdesivo)
            val consumoColaKg = if (RENDIMENTO_COLA_M2_POR_KG > 0.0) {
                areaTotalRevestimentoM2 / RENDIMENTO_COLA_M2_POR_KG
            } else 0.0

            val consumoColaKgPos = max(0.0, consumoColaKg)

            val obsCola =
                "Cola acrílica para piso vinílico. Vendida em potes.\nVer tipo de piso e aplicar conforme ficha técnica."

            itens += MaterialItem(
                item = "Cola",
                unid = "kg",
                qtd = NumberFormatter.arred1(consumoColaKgPos),
                observacao = obsCola
            )
        }

        // ================= 8) CONSUMO DE MASSA PVA (DESNIVELADO) =================
        // Só calcula se:
        //  - switch de desnível estiver ligado
        //  - qtd de demãos entre 1 e 4
        //  - tipo de base (grosso / fino) definido
        if (inputs.pisoVinilicoDesnivelAtivo && inputs.pisoVinilicoQtdDemaos != null) {
            val qtdDemaos = inputs.pisoVinilicoQtdDemaos
            if (qtdDemaos in 1..4) {
                val rendimentoPorDemao = when (inputs.pisoVinilicoDesnivelTipo) {
                    PisoVinilicoDesnivelTipo.GROSSO -> RENDIMENTO_PVA_GROSSO_M2_POR_KG_DEMAO
                    PisoVinilicoDesnivelTipo.FINO -> RENDIMENTO_PVA_LISO_M2_POR_KG_DEMAO
                    null -> null
                }

                if (rendimentoPorDemao != null && rendimentoPorDemao > 0.0) {
                    val consumoPvaPorDemaoKg = areaTotalRevestimentoM2 / rendimentoPorDemao
                    val consumoPvaTotalKg = consumoPvaPorDemaoKg * qtdDemaos
                    val consumoPvaTotalKgPos = max(0.0, consumoPvaTotalKg)

                    val obsPva = "Massa niveladora acrílica (PVA) para regularização de contrapiso."

                    itens += MaterialItem(
                        item = "Massa niveladora",
                        unid = "kg",
                        qtd = NumberFormatter.arred1(consumoPvaTotalKgPos),
                        observacao = obsPva
                    )
                }
            }
        }
    }
}