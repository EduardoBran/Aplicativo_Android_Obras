package com.luizeduardobrandao.obra.data.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.firebase.database.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

@Parcelize
@Keep
@IgnoreExtraProperties
data class SolutionHistory(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val date: String = ""  // "dd/MM/yyyy"
) : Parcelable