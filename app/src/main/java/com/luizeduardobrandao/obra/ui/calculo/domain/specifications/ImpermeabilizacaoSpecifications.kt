//import android.widget.RadioGroup
//import com.luizeduardobrandao.obra.R

//import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.AmbienteType
//import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.MaterialItem
//import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.TrafegoType
//import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.PisoIntertravadoCalculator.CIMENTO_SACOS_M3_BASE
//import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.PisoIntertravadoCalculator.arred1
//import com.luizeduardobrandao.obra.ui.calculo.domain.calculators.PisoIntertravadoCalculator.arred2
//import kotlin.math.ceil

//package com.luizeduardobrandao.obra.ui.calculo.domain.specifications
//
//import com.luizeduardobrandao.obra.ui.calculo.CalcRevestimentoViewModel.*
//
///**
// * Especificações de impermeabilização
// */
//object ImpermeabilizacaoSpecifications {
//
//    /** Tipos de impermeabilização para piso intertravado */
//    enum class ImpIntertravadoTipo {
//        MANTA_GEOTEXTIL,
//        ADITIVO_SIKA1,
//        MANTA_ASFALTICA
//    }
//
//    data class ImpConfig(
//        val item: String,
//        val consumo: Double,
//        val unid: String,
//        val observacao: String
//    )
//
//    /**
//     * Retorna configuração de impermeabilização conforme ambiente
//     */
//    fun getImpConfig(ambiente: AmbienteType?): ImpConfig? {
//        return when (ambiente) {
//            AmbienteType.SEMI -> ImpConfig(
//                item = "Impermeabilizante Membrana Acrílica",
//                consumo = 1.2,
//                unid = "L",
//                observacao = "Vendida em embalagens • Aplicar em 3 a 4 demãos."
//            )
//
//            AmbienteType.MOLHADO -> ImpConfig(
//                item = "Impermeabilizante Argamassa Polimérica Flexível (3,5 kg/m²)",
//                consumo = 3.5,
//                unid = "kg",
//                observacao = "Vendida em embalagens • Aplicar em 2 demãos."
//            )
//
//            AmbienteType.SEMPRE -> ImpConfig(
//                item = "Impermeabilizante Argamassa Polimérica Bicomponente (4 kg/m²)",
//                consumo = 4.0,
//                unid = "kg",
//                observacao = "Vendida em kits • Misturar os 2 componentes e aplicar em 2 demãos."
//            )
//
//            else -> null
//        }
//    }
//}


//// Piso intertravado: Manta Asfáltica → rolos 10 m²
//if (nome.contains("Manta Asfáltica", ignoreCase = true) &&
//(unid.equals("m²", true) || unid.equals("m2", true))
//) {
//    val rolos = ceil(alvo / 10.0).toInt().coerceAtLeast(1)
//    return if (rolos == 1) "1 rolo 10m²" else "$rolos rolos 10m²"
//}
//
//// Piso intertravado: Manta Geotêxtil → rolos 100 m²
//if (nome.contains("Manta Geotêxtil", ignoreCase = true) &&
//(unid.equals("m²", true) || unid.equals("m2", true))
//) {
//    val rolos = ceil(alvo / 100.0).toInt().coerceAtLeast(1)
//    return if (rolos == 1) "1 rolo de 100m²" else "$rolos rolos de 100m²"
//}
//
//// Piso intertravado: Aditivo impermeabilizante (Sika 1) → frasco/galão/balde
//if (nome.contains("Aditivo impermeabilizante", ignoreCase = true) &&
//unid.equals("L", true)
//) {
//    return buildAditivoSikaComprar(alvo)
//}

// ---------------- IMPERMEABILIZANTES (EXCETO INTERTRAVADO) ----------------

//// 1) Membrana Acrílica → 18L, 3,6L, 1L
//if (nome.equals("Impermeabilizante Membrana Acrílica", ignoreCase = true) &&
//unid.equals("L", true)
//) {
//    val pack = bestPackCombo(alvo, listOf(18.0, 3.6, 1.0))
//    return pack.toCompraString(unidade = "L", label = null)
//}
//
//// 2) Argamassa Polimérica Flexível → 18kg, 4kg
//if (nome.startsWith(
//"Impermeabilizante Argamassa Polimérica Flexível",
//ignoreCase = true
//) && unid.equals("kg", true)
//) {
//    val pack = bestPackCombo(alvo, listOf(18.0, 4.0))
//    return pack.toCompraString(unidade = "kg", label = null)
//}
//
//// 3) Argamassa Polimérica Bicomponente → 18kg, 4kg
//if (nome.startsWith(
//"Impermeabilizante Argamassa Polimérica Bicomponente",
//ignoreCase = true
//) && unid.equals("kg", true)
//) {
//    val pack = bestPackCombo(alvo, listOf(18.0, 4.0))
//    return pack.toCompraString(unidade = "kg", label = null)
//}

///**
// * Distribui o volume de aditivo (L) em frascos 1L, galões 3,6L e baldes 18L
// * com o menor excedente possível e poucas embalagens.
// */
//private fun buildAditivoSikaComprar(litros: Double): String {
//    if (litros <= 0.0) return "0"
//    val sizes = listOf(18.0, 3.6, 1.0)
//    var best: Map<Double, Int> = emptyMap()
//    var bestOver = Double.MAX_VALUE
//    var bestCount = Int.MAX_VALUE
//
//    fun search(idx: Int, acc: Map<Double, Int>) {
//        if (idx == sizes.size) {
//            val total = acc.entries.sumOf { it.key * it.value }
//            if (total <= 0.0) return
//            val over = (total - litros).coerceAtLeast(0.0)
//            val count = acc.values.sum()
//            if (total >= litros &&
//                (count < bestCount || (count == bestCount && over < bestOver))
//            ) {
//                best = acc
//                bestCount = count
//                bestOver = over
//            }
//            return
//        }
//        val size = sizes[idx]
//        val maxN = ceil(litros / size).toInt() + 3
//        for (n in 0..maxN) {
//            val next = if (n == 0) acc else acc + (size to n)
//            val partial = next.entries.sumOf { it.key * it.value }
//            if (partial > litros + bestOver && bestOver < Double.MAX_VALUE) continue
//            search(idx + 1, next)
//        }
//    }
//
//    search(0, emptyMap())
//
//    if (best.isEmpty()) {
//        val n = ceil(litros).toInt()
//        return if (n == 1) "1 frasco 1L" else "$n frascos 1L"
//    }
//
//    val parts = best.entries
//        .sortedByDescending { it.key }
//        .filter { it.value > 0 }
//        .map { (size, count) ->
//            when (size) {
//                18.0 -> if (count == 1) "1 balde 18L" else "$count baldes 18L"
//                3.6 -> if (count == 1) "1 galão 3,6L" else "$count galões 3,6L"
//                1.0 -> if (count == 1) "1 frasco 1L" else "$count frascos 1L"
//                else -> ""
//            }
//        }.filter { it.isNotBlank() }
//
//    return if (parts.isEmpty()) "0" else parts.joinToString(" + ")
//}

//@Suppress("UnnecessaryVariable")
//fun addAditivoSika1() {
//    if (volumeBgs <= 0.0) return
//
//    val sacosRef = volumeBgs * (1 + sobra / 100.0) * CIMENTO_SACOS_M3_BASE
//    val cimentoKg = sacosRef * 50.0
//
//    itens += MaterialItem(
//        item = "Cimento",
//        unid = "kg",
//        qtd = arred1(cimentoKg),
//        observacao = "Estabilização da base BGS."
//    )
//
//    val litros = sacosRef
//    itens += MaterialItem(
//        item = "Aditivo impermeabilizante (Sika 1 ou similar)",
//        unid = "L",
//        qtd = arred1(litros),
//        observacao = "Dosagem 1 L por saco de cimento na estabilização da base."
//    )
//}
//
//fun addMantaGeotextil() {
//    val area = arred2(areaM2 * (1 + sobra / 100.0))
//
//    val nome = when {
//        inputs.ambiente == AmbienteType.MOLHADO &&
//                inputs.trafego == TrafegoType.LEVE ->
//            "Manta Geotêxtil ≥ 150 g/m²"
//
//        inputs.ambiente == AmbienteType.MOLHADO &&
//                inputs.trafego == TrafegoType.MEDIO ->
//            "Manta Geotêxtil ≥ 200 g/m²"
//
//        inputs.ambiente == AmbienteType.SEMPRE &&
//                inputs.trafego == TrafegoType.LEVE ->
//            "Manta Geotêxtil ≥ 200 g/m²"
//
//        inputs.ambiente == AmbienteType.SEMPRE &&
//                inputs.trafego == TrafegoType.MEDIO ->
//            "Manta Geotêxtil ≥ 300 g/m²"
//
//        else -> "Manta Geotêxtil"
//    }
//
//    itens += MaterialItem(
//        item = nome,
//        unid = "m²",
//        qtd = area,
//        observacao = "Aplicar sob toda a área da base (rolos de 100 m²)."
//    )
//}
//
//fun addMantaAsfaltica() {
//    val area = arred2(areaM2 * (1 + sobra / 100.0))
//    itens += MaterialItem(
//        item = "Manta Asfáltica",
//        unid = "m²",
//        qtd = area,
//        observacao = "Aplicação em toda a área impermeabilizada (rolos de 10 m²)."
//    )
//}




///**
// * Sincroniza RadioGroup de impermeabilização (piso intertravado)
// */
//private fun syncImpermeabilizacao(
//    tipo: ImpermeabilizacaoSpecifications.ImpIntertravadoTipo?,
//    rgIntertravadoImp: RadioGroup
//) {
//    val radioId = when (tipo) {
//        ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.MANTA_GEOTEXTIL ->
//            R.id.rbImpMantaGeotextil
//
//        ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.ADITIVO_SIKA1 ->
//            R.id.rbImpAditivoSika1
//
//        ImpermeabilizacaoSpecifications.ImpIntertravadoTipo.MANTA_ASFALTICA,
//        null -> null // Manta asfáltica não tem radio button específico
//    }
//    rgIntertravadoImp.setCheckedSafely(radioId)
//}