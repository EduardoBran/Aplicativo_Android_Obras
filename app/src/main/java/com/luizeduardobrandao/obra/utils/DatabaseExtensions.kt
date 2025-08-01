package com.luizeduardobrandao.obra.utils

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

/**
 * Atalho para instanciar um [ValueEventListener] focado apenas no mét0do [onDataChange].
 *
 * Uso:
 * ```
 * ref.addValueEventListener(valueEventListener { snapshot ->
 *     // trate snapshot…
 * })
 * ```
 */

inline fun valueEventListener(
    crossinline onData: (DataSnapshot) -> Unit
): ValueEventListener = object : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)
    override fun onCancelled(error: DatabaseError) {
        // opcional: log de erro, Crashlytics, etc.
    }
}