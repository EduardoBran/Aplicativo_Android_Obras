package com.luizeduardobrandao.obra.ui.calculo.domain.specifications

import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
import kotlin.math.max

/**
 * Especificações e cálculos de argamassa colante
 */
object ArgamassaSpecifications {

    /** Calcula consumo de argamassa em kg/m² */
    fun consumoArgamassaKgM2(inputs: Inputs): Double {
        val maxLado = max(inputs.pecaCompCm ?: 30.0, inputs.pecaLargCm ?: 30.0)
        val isPorc = inputs.revest == RevestimentoType.PISO &&
                inputs.pisoPlacaTipo == PlacaTipo.PORCELANATO
        val esp = inputs.pecaEspMm ?: RevestimentoSpecifications.getEspessuraPadraoMm(inputs)

        // 🧱 Tratamento especial para pastilhas
        if (inputs.revest == RevestimentoType.PASTILHA) {
            return when (inputs.pecaCompCm) {
                5.0 -> 7.0
                7.5 -> 7.0
                10.0 -> 7.0
                else -> 5.5
            }
        }

        val consumoBase = when {
            maxLado <= 15.0 -> 4.0
            maxLado <= 20.0 -> 5.0
            maxLado <= 32.0 -> 6.0
            maxLado <= 45.0 -> 7.0
            maxLado <= 60.0 -> 8.0
            maxLado <= 75.0 -> 9.0
            maxLado <= 90.0 -> 10.0
            maxLado <= 120.0 -> 12.0
            else -> 14.0
        }

        val fatorPorcelanato = if (isPorc) when {
            maxLado >= 60.0 -> 1.20
            maxLado >= 45.0 -> 1.15
            else -> 1.10
        } else 1.0

        val fatorEspessura = when {
            esp < 7.0 -> 0.95
            esp <= 10.0 -> 1.0
            esp <= 15.0 -> 1.1
            else -> 1.2
        }

        val fatorAmbiente = when (inputs.ambiente) {
            AmbienteType.SEMPRE -> 1.15
            AmbienteType.MOLHADO -> 1.10
            else -> 1.0
        }

        return (consumoBase * fatorPorcelanato * fatorEspessura * fatorAmbiente)
            .coerceIn(4.0, 18.0)
    }

    /** Calcula a área (m²) usada para Argamassa / Rejunte */
    fun calcularAreaMateriaisRevestimentoM2(
        inputs: Inputs,
        areaRevestimentoM2: Double,
        rodapeAreaM2: Double
    ): Double {
        return areaRevestimentoM2 +
                if (inputs.rodapeEnable && inputs.rodapeMaterial == RodapeMaterial.PECA_PRONTA)
                    rodapeAreaM2
                else 0.0
    }

    /** ======================= CLASSIFICAÇÃO DE ARGAMASSA =======================
     * Centraliza toda a lógica de escolha da classe de argamassa.
     *
     * Classes consideradas: ACI, ACII, ACIII, ACIII-E, AC Epóxi
     */
    fun classificarArgamassa(inputs: Inputs): String? {
        val revest = inputs.revest ?: return null
        val ambiente = inputs.ambiente ?: return null

        return when (revest) {
            RevestimentoType.PISO ->
                classificarPiso(inputs, ambiente)

            RevestimentoType.AZULEJO ->
                classificarAzulejo(inputs, ambiente)

            RevestimentoType.PASTILHA ->
                classificarPastilha(ambiente)

            RevestimentoType.MARMORE,
            RevestimentoType.GRANITO ->
                classificarMarmoreGranito(ambiente)

            else -> null // Pedra, Piso intertravado, etc. não entram nessas regras
        }
    }

    /** Retorna o maior lado da peça em cm, ou null se ainda não informado/fora de faixa */
    private fun ladoMaximoCm(inputs: Inputs): Double? {
        val comp = inputs.pecaCompCm
        val larg = inputs.pecaLargCm
        if (comp == null || larg == null) return null
        if (comp <= 0.0 || larg <= 0.0) return null
        return max(comp, larg)
    }

    // ---------------- ARGAMASSAS – PISO ----------------
    private fun classificarPiso(
        inputs: Inputs,
        ambiente: AmbienteType
    ): String? {
        val tipo = inputs.pisoPlacaTipo ?: PlacaTipo.CERAMICA
        val isCer = tipo == PlacaTipo.CERAMICA
        val isPorc = tipo == PlacaTipo.PORCELANATO
        val ladoMax = ladoMaximoCm(inputs) ?: return null

        return when (ambiente) {
            // ---------------- AMBIENTE SECO ----------------
            AmbienteType.SECO -> when {
                // ACI – Ambiente seco + piso cerâmico + peça ≤ 30×30 cm
                isCer && ladoMax <= 30.0 ->
                    "ACI"
                // ACII – Ambiente seco + piso cerâmico + peça entre 31×31 cm e < 60×60 cm
                isCer && ladoMax > 30.0 && ladoMax < 60.0 ->
                    "ACII"
                // Piso cerâmica, ambiente seco, peça > 60 → ACIII
                isCer && ladoMax >= 60.0 ->
                    "ACIII"
                // ACIII – Ambiente seco + piso porcelanato + peça até 90×90 cm
                isPorc && ladoMax <= 90.0 ->
                    "ACIII"

                isPorc -> "ACIII"

                else -> null
            }

            // ---------------- AMBIENTE SEMI MOLHADO ----------------
            AmbienteType.SEMI -> when {
                // ACII ou ACIII – Ambiente semi molhado + piso cerâmico + peça até 60×60 cm
                isCer && ladoMax <= 60.0 ->
                    "ACII ou ACIII"
                // Piso cerâmica, ambiente semi molhado, peça > 60 → ACII ou ACIII
                isCer && ladoMax > 60.0 ->
                    "ACIII"
                // Piso porcelanato, ambiente semi molhado, peça < 60 → ACIII
                isPorc && ladoMax < 60.0 ->
                    "ACIII"
                // ACIII ou ACIII-E – Ambiente semi molhado + piso porcelanato + peça >= 60
                isPorc && ladoMax >= 60.0 ->
                    "ACIII ou ACIII-E"

                else -> null
            }

            // ---------------- AMBIENTE MOLHADO ----------------
            AmbienteType.MOLHADO -> when {
                // Piso cerâmica, ambiente molhado, peça < 60 → ACIII
                isCer && ladoMax < 60.0 ->
                    "ACIII"
                // Piso cerâmica, ambiente molhado, peça entre 60 e 90 → ACIII ou ACIII-E
                isCer && ladoMax >= 60.0 && ladoMax < 90.0 ->
                    "ACIII ou ACIII-E"
                // Ambiente molhado + piso cerâmico + peça > 90×90 cm → ACIII-E
                isCer && ladoMax >= 90.0 ->
                    "ACIII-E"
                // Piso porcelanato, ambiente molhado, peça < 90 → ACIII ou ACIII-E
                isPorc && ladoMax < 90.0 ->
                    "ACIII ou ACIII-E"
                // Ambiente molhado + piso porcelanato + peça ≥ 90×90 cm → ACIII-E
                isPorc && ladoMax >= 90.0 ->
                    "ACIII-E"

                else -> null
            }

            // ---------------- AMBIENTE SEMPRE MOLHADO ----------------
            AmbienteType.SEMPRE -> when {
                // Piso cerâmica, ambiente sempre molhado, peça < 60 → ACIII-E
                isCer && ladoMax < 60.0 ->
                    "ACIII-E"
                // Piso cerâmica, ambiente sempre molhado, peça > 60 → ACIII-E ou AC Epóxi
                isCer && ladoMax >= 60.0 ->
                    "ACIII-E ou AC Epóxi"
                // Piso porcelanato, ambiente sempre molhado, peça ≤ 120 → ACIII-E ou AC Epóxi
                isPorc && ladoMax < 120.0 ->
                    "ACIII-E ou AC Epóxi"
                // Piso porcelanato, ambiente sempre molhado, peça ≥ 120 → AC Epóxi
                isPorc && ladoMax >= 120.0 ->
                    "AC Epóxi"

                else -> null
            }
        }
    }

    // ---------------- ARGAMASSAS – AZULEJO ----------------
    private fun classificarAzulejo(
        inputs: Inputs,
        ambiente: AmbienteType
    ): String? {
        val ladoMax = ladoMaximoCm(inputs) ?: return null
        val tipo = inputs.pisoPlacaTipo ?: PlacaTipo.CERAMICA
        val isCer = tipo == PlacaTipo.CERAMICA
        val isPorc = tipo == PlacaTipo.PORCELANATO

        return when (ambiente) {
            // ---------------- AMBIENTE SECO ----------------
            AmbienteType.SECO -> when {
                // ACI – Ambiente seco + azulejo cerâmico + peça ≤ 30×30 cm
                isCer && ladoMax <= 30.0 ->
                    "ACI"
                // ACII – Ambiente seco + azulejo cerâmico + peça entre 31×31 cm e 45×90 cm
                isCer && ladoMax > 30.0 && ladoMax <= 90.0 ->
                    "ACII"
                // Azulejo cerâmico, seco, peça > 90 → ACIII
                isCer && ladoMax > 90.0 ->
                    "ACIII"
                // Azulejo porcelanato, seco, peça < 45 → ACII ou ACIII
                isPorc && ladoMax < 45 ->
                    "ACII ou ACIII"
                // Extensão natural para porcelanato seco até 90 cm
                isPorc && ladoMax > 45.0 && ladoMax <= 90.0 ->
                    "ACII ou ACIII"
                // Para porcelanato seco > 90 cm, manter ACII como base
                isPorc && ladoMax > 90.0 ->
                    "ACIII"

                else -> null
            }

            // ---------------- AMBIENTE SEMI MOLHADO ----------------
            AmbienteType.SEMI -> when {
                // Azulejo cerâmico em ambiente semi molhado → sempre ACII
                isCer ->
                    "ACII"
                // Azulejo porcelanato em ambiente semi molhado:
                // - peças com lado entre 31 cm e 45 cm → ACII ou ACIII demais tamanhos → ACIII
                isPorc && ladoMax >= 31.0 && ladoMax <= 45.0 ->
                    "ACII ou ACIII"

                isPorc ->
                    "ACIII"

                else -> null
            }

            // ---------------- AMBIENTE MOLHADO ----------------
            AmbienteType.MOLHADO -> when {
                // ACIII – Ambiente molhado + azulejo cerâmico + peça até 60×60 cm
                // Azulejo cerâmico, molhado, peça > 90 → ACIII
                isCer ->
                    "ACIII"
                // Para porcelanato: até 60 cm → ACIII
                isPorc && ladoMax <= 60.0 ->
                    "ACIII"
                // ACIII ou ACIII-E – Ambiente molhado + azulejo porcelanato > 60
                isPorc && ladoMax > 60.0 && ladoMax <= 90.0 ->
                    "ACIII ou ACIII-E"

                isPorc -> "ACIII ou ACIII-E"

                else -> null
            }

            // ---------------- AMBIENTE SEMPRE MOLHADO ----------------
            AmbienteType.SEMPRE -> when {
                // Azulejo cerâmico, sempre molhado, peça < 60 → ACIII ou ACIII-E
                isCer && ladoMax < 60.0 ->
                    "ACIII ou ACIII-E"
                // Azulejo cerâmico, sempre molhado, peça entre 60 e 90 → ACIII ou ACIII-E
                isCer && ladoMax >= 60.0 && ladoMax < 90.0 ->
                    "ACIII ou ACIII-E"
                // ACIII-E – Ambiente sempre molhado + azulejo cerâmico ou porcelanato + peça > 90×90 cm
                isCer && ladoMax >= 60.0 ->
                    "ACIII ou ACIII-E"
                // Azulejo porcelanato, sempre molhado, menor que 90 → ACIII-E
                isPorc && ladoMax < 90 ->
                    "ACIII-E"
                // Azulejo porcelanato, sempre molhado, maior ou igual que 90 → ACIII-E
                isPorc && ladoMax >= 90.0 ->
                    "ACIII-E ou AC Epóxi"

                else -> null
            }
        }
    }

    // ---------------- ARGAMASSAS – PASTILHA ----------------
    private fun classificarPastilha(
        ambiente: AmbienteType
    ): String {
        return when (ambiente) {
            // ACII – Ambiente seco + pastilha cerâmica
            AmbienteType.SECO ->
                "ACII"
            // ACII ou ACIII – Ambiente semi molhado + pastilha cerâmica ou porcelanato
            AmbienteType.SEMI ->
                "ACII ou ACIII"
            // ACIII – Ambiente molhado + pastilha cerâmica ou porcelanato
            // ACIII ou ACIII-E – Ambiente molhado + pastilha porcelanato
            AmbienteType.MOLHADO ->
                "ACIII"
            // ACIII-E – Ambiente sempre molhado + pastilha cerâmica ou porcelanato
            AmbienteType.SEMPRE ->
                "ACIII-E"
        }
    }

    // ---------------- ARGAMASSAS – MÁRMORE OU GRANITO ----------------
    private fun classificarMarmoreGranito(
        ambiente: AmbienteType
    ): String {
        return when (ambiente) {
            // ACIII – Ambientes seco, semi molhado ou molhado
            AmbienteType.SECO,
            AmbienteType.SEMI,
            AmbienteType.MOLHADO ->
                "ACIII"
            // AC Epóxi – Ambiente sempre molhado
            AmbienteType.SEMPRE ->
                "AC Epóxi"
        }
    }
}