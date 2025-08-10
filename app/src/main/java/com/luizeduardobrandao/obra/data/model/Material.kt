package com.luizeduardobrandao.obra.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

@Parcelize
@Keep
@IgnoreExtraProperties
data class Material(
    val id: String = "",
    val nome: String = "",
    val descricao: String? = null,
    val quantidade: Int = 0,
    val status: String = "Ativo"
) : Parcelable
